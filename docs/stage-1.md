# Stage 1: Monolith, in-memory

**Goal:** the whole shorten → redirect flow working in one Spring Boot app, with storage in memory.
**Why start in memory:** it isolates one lesson (layering + HTTP) with no Docker or SQL to debug. Stage 2 is the real test of Stage 1: swapping in MySQL should mean adding **one class**, with no changes to the domain.

**Status:** 🚧 everything is built and tested except the controller test (`@WebMvcTest`).

---

## What was built

| File | Layer | Job |
|---|---|---|
| `model/Redirection` | model | Immutable record: `id`, `longUrl` |
| `dao/RedirectionDao` | dao | Contract: `save`, `findById` (returns `Optional`) |
| `dao/InMemoryRedirectionDao` | dao | `ConcurrentHashMap`; rejects duplicate ids |
| `domain/Base62` | domain | Static `encode(long)`: writes a number with 62 symbols |
| `domain/IdGenerator` | domain | Contract: `nextId()` |
| `domain/CounterIdGenerator` | domain | `AtomicLong` + `Base62` |
| `domain/UrlShortner` | domain | `shorten` / `resolve`, built from the two interfaces |
| `api/RedirectionController` | api | `POST /shorten` → 201, `GET /{id}` → 302 / 404 |
| `dto/ShortenRequest`, `dto/ShortenResponse` | dto | DTOs; the request carries the validation rules |

```
api ──► domain ──► dao        (dependencies point one way only)
 │        │
 │        └── model is used by every layer
 └── dto is used only by api
```

---

## Key decisions

### Layering + interfaces + dependency injection
`UrlShortner` depends only on the `IdGenerator` and `RedirectionDao` **interfaces**, which it receives through its **constructor**. Spring finds the single bean that implements each interface and passes it in. This is Inversion of Control.
- Storage can be swapped (in-memory → MySQL) without touching the domain.
- The id strategy can be swapped (counter → random → ranges) without touching the domain.
- Tests can pass in real objects, lambda stubs or Mockito mocks without starting Spring.

Constructor injection rather than `@Autowired` fields: the fields can be `final`, no dependency can be forgotten, tests don't need Spring, and the dependencies are visible at a glance.

### `IdGenerator` is a bean, not just `Base62`
The goal was to be able to try different id algorithms. The thing to swap is **"how to get the next id"**, not "how to write a number in base62", since random or hash-based ids wouldn't use Base62 at all. So `IdGenerator` is the interface, and `Base62` stays a **static pure function**: no state, no dependencies, nothing to swap.

### The alphabet stays in code, private to `Base62`
- **Not in config:** it must be identical on every server forever. Config would make it *easier* to break (one server with a typo produces ids in a different alphabet).
- **Not in a shared `Constants` class:** it would have to become public, and the class turns into a junk drawer. A constant lives with the code that owns it.
- **Protected by a test:** `alphabetNeverChanges` encodes 0–61 and compares the result with the literal alphabet.

### Thread safety (Tomcat runs up to 200 requests at once)
- `counter++` is read → add → write, and two threads can interleave and get the **same id**. `AtomicLong.incrementAndGet()` does it as one atomic step (a CPU compare-and-swap).
- `HashMap` can corrupt under concurrent writes, so we use `ConcurrentHashMap`.
- `save` uses `putIfAbsent` rather than check-then-put. Check-then-act is a race: two threads both see "free", and the second overwrites.
- Records are immutable, so they're thread-safe automatically.

### Duplicate ids fail loudly
Saving an id that already exists **throws** instead of overwriting (the "Alice/Bob" scenario: Alice's link silently pointing to Bob's site). MySQL's primary key gives the same behaviour in Stage 2, so the contract holds across implementations.

### HTTP
- `201 Created` + `Location` for shorten; `302 Found` for redirect (see the design doc for why not 301); `404` via `Optional`.
- **DTOs** keep the internal model out of the public API. `shortUrl` isn't part of the model, and future fields (`expiresAt`, `userId`) shouldn't leak out. They live in their own `dto/` package, separate from the controller. Only `api` may use them; the domain never sees a DTO.
- **Validation:** `@NotBlank`, `@Size(max = 2048)`, and `^https?://\S+$`. The scheme check blocks `javascript:` URLs, which would otherwise turn our trusted short links into an XSS vector.

### No Lombok (for now)
Records already replace `@Data`/`@Value`, and writing constructors by hand first means you know what Lombok would be generating. It may come back later for `@RequiredArgsConstructor` or `@Slf4j`.

---

## Tests

| Test | Kind | What it proves |
|---|---|---|
| `InMemoryRedirectionDaoTest` | unit | save/find, empty for unknown ids, duplicate rejected and the original kept |
| `Base62Test` | unit, parameterized | boundary values (0, 9/10, 35/36, 61/62, 3843/3844), the alphabet golden test, uniqueness over 100k numbers, max 11 chars, negatives rejected |
| `CounterIdGeneratorTest` | unit, concurrency | 200 threads × 1,000 ids give 200,000 unique ids (a `CountDownLatch` start gate + `ExecutorService`) |
| `UrlShortnerTest` | unit, with test doubles | a lambda stub controls ids; a stub returning a duplicate simulates a buggy generator; a Mockito mock verifies that `resolve` never calls `nextId()` |
| `UrlShortnerApplicationTests` | full Spring context | all beans wire up |
| `RedirectionControllerTest` | `@WebMvcTest` slice | ⬜ to do |

**Testing lessons**
- Unit tests use `new`, not Spring. That's faster, and every test gets fresh state (JUnit creates a new test instance per method; Spring singletons are shared and cached).
- Only fake what is slow, unpredictable or hard to control. The in-memory DAO is fast and already tested, so the tests use the real one.
- Prefer **state** assertions (results) over **interaction** assertions (`verify`). Use `verify` only when the interaction *is* the requirement.
- Test code must be as thread-safe as the code under test (hence `ConcurrentHashMap.newKeySet()`).
- A concurrency test can show a race exists, but a passing run never proves there isn't one.

---

## Known limitations (left on purpose, fixed in later stages)

| Limitation | Fixed in |
|---|---|
| Restarting loses every link (in-memory storage) | Stage 2 |
| The counter resets to 0 on restart. With persistent storage that causes duplicate-id errors. | Stage 2 → 4 |
| Only one counter, so two app instances would hand out the same ids | Stage 4 |
| Sequential ids are guessable | Future experiment: scrambled or random `IdGenerator` + rate limiting |
| `shortUrl` is built from the incoming request, so behind nginx it needs `X-Forwarded-*` headers | Stage 5 |
| No expiry yet | Stage 2 |

---

## Bugs hit along the way
- **`UrlShortner` had no `.java` extension.** `javac` silently ignored the file, so the class didn't exist. `contextLoads` still passed because there was no bean to wire, and the test file failed with "cannot find symbol". Lesson: a green context test proves nothing about a class that never got compiled.
- **Docker "permission denied"** after `usermod -aG docker`: group membership only applies to new login sessions, so a reboot fixed it.
