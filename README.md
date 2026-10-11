# url-shortner

A URL shortening service, built one system-design concept at a time with **Java 21 + Spring Boot 4**.

This is a rebuild of the `tiny-url-generator` project from [system-design-with-code](https://github.com/nujra-panda/system-design-with-code), which used Micronaut 1.x and Gradle 5. It starts as a single in-memory app. Each stage adds one technology, and only when a real limitation calls for it: MySQL, Redis, an ID service with ZooKeeper, service discovery, auth, analytics, observability, resilience, and load testing.

The design work behind it (requirements, estimates, trade-offs) is in [`docs/00-design.md`](docs/00-design.md). Each stage has its own write-up in [`docs/`](docs/).

---

## Progress

| # | Stage | New tech / concepts | Status |
|---|---|---|---|
| 0 | Setup | Spring Boot, Gradle, Docker | ✅ |
| 1 | [Monolith, in-memory](docs/stage-1.md) | REST, layering, DI, Base62, concurrency, unit tests | 🚧 controller test left |
| 2 | Real database + expiry | MySQL, Docker Compose, Spring JDBC, Testcontainers | ⬜ |
| 3 | Caching | Redis, cache-aside, TTLs | ⬜ |
| 4 | Split out ID generation | Multi-module Gradle, ID ranges, ZooKeeper / Curator | ⬜ |
| 5 | Discovery + load balancing | Consul, nginx, multiple replicas | ⬜ |
| 6 | Users + auth | Spring Security, JWT, per-user history | ⬜ |
| 7 | Analytics service | Async service-to-service calls | ⬜ |
| 8 | Observability | Micrometer, Prometheus, Grafana, Zipkin | ⬜ |
| 9 | Resilience | Resilience4j, chaos testing | ⬜ |
| 10 | Load testing | Gatling | ⬜ |

---

## Current architecture (Stage 1)

```
 HTTP ──► RedirectionController ──► UrlShortner ──┬──► IdGenerator ◄── CounterIdGenerator (AtomicLong + Base62)
           (api)                    (domain)      │
                                                  └──► RedirectionDao ◄── InMemoryRedirectionDao (ConcurrentHashMap)
```

- **api**: HTTP only. It handles routing and status codes.
- **dto**: the JSON shapes going in and out of the API, plus the input validation rules.
- **domain**: business logic. It only depends on the interfaces `IdGenerator` and `RedirectionDao`.
- **dao**: storage behind an interface, so Stage 2 can swap in MySQL without touching the domain.

---

## API

### Shorten a URL
```
POST /shorten
Content-Type: application/json

{"longUrl": "https://example.com/some/long/path"}
```
| Response | When |
|---|---|
| `201 Created` + `Location` header + `{"id": "1", "shortUrl": "http://localhost:8080/1"}` | Success |
| `400 Bad Request` | `longUrl` is blank, longer than 2048 characters, or not an `http(s)://` URL |

### Follow a short URL
```
GET /{id}
```
| Response | When |
|---|---|
| `302 Found` + `Location: <longUrl>` | The id exists |
| `404 Not Found` | Unknown id |

---

## Run it

Requires **Java 21**. Docker is only needed from Stage 2 onwards.

```bash
./gradlew test       # run all tests
./gradlew bootRun    # start on http://localhost:8080
```

```bash
# shorten
curl -i -X POST localhost:8080/shorten \
  -H 'Content-Type: application/json' \
  -d '{"longUrl": "https://github.com/nujra-panda/system-design-with-code"}'

# follow (or open http://localhost:8080/1 in a browser)
curl -i localhost:8080/1

# health
curl localhost:8080/actuator/health
```

> Stage 1 keeps everything in memory, so restarting the app forgets every link. Stage 2 fixes that.

---

## Project layout

```
src/main/java/dev/arjun/urlshortner/
├── UrlShortenerApplication.java
├── api/       RedirectionController
├── dto/       ShortenRequest, ShortenResponse
├── domain/    UrlShortner, IdGenerator, CounterIdGenerator, Base62
├── dao/       RedirectionDao, InMemoryRedirectionDao
└── model/     Redirection
docs/          design notes + one write-up per stage
```
