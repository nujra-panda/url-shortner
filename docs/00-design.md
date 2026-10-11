# Design: URL Shortener

The whiteboard design that comes before any code. Every stage in this repo follows from the requirements, numbers and trade-offs below.

Approach: **requirements → estimates → API → data model → core algorithm → high-level design → bottlenecks**.

---

## 1. Requirements

**Clarifying questions and the answers we chose**

| Question | Decision |
|---|---|
| Custom aliases (`sho.rt/my-party`)? | No |
| Do links expire? | Yes, after a configurable lifetime |
| Accounts? | Optional. Anonymous users can shorten; logged-in users see their history. |
| Click analytics? | Yes, basic click counts |
| Edit or delete links? | No |

**Functional requirements**
1. Shorten a long URL.
2. Redirect from the short URL to the long URL.
3. Expire links.
4. Logged-in users see their own links.
5. Count clicks.

**Non-functional requirements**
- Highly available. If redirects are down, every short link on the internet is broken.
- Low latency on redirects.
- Ids must be unique, so no two links ever share an id.
- Ids hard to guess (nice to have; see §5).

---

## 2. Back-of-the-envelope estimates

Assumption: **100M new links per month**, with **100 reads for every write**.

| | Calculation | Result |
|---|---|---|
| Writes | 100M / 2.6M seconds per month | **~40 / s** |
| Reads | 40 × 100 | **~4,000 / s**, peak ~10,000 / s |
| Storage (5 years, 500 B per link) | 100M × 12 × 5 × 500 B | **6B links, ~3 TB** |
| Id length (base62) | 62⁶ ≈ 56B, 62⁷ ≈ 3.5T | **7 chars** is plenty |
| Cache (80/20 rule) | 350M reads/day × 20% × 500 B | **~35 GB**, fits in RAM |

**What the numbers mean**

| Number | Conclusion |
|---|---|
| 40 writes/s | Tiny. One database handles it easily. |
| 10k reads/s | Reads are the real load. That calls for a **cache** (Stage 3) and **many stateless app servers** (Stage 5). |
| 3 TB | One database is fine for a long time. Sharding can wait. |
| Unique 7-char ids across servers | Needs a coordinated id strategy (Stage 4). |

---

## 3. API

```
POST /shorten   {"longUrl": "..."}  → 201 {"id", "shortUrl"}  | 400 invalid input
GET  /{id}                          → 302 Location: longUrl    | 404 unknown | 410 expired (Stage 2)
GET  /history   (authenticated)     → 200 [links]              (Stage 6)
```

### 302, not 301
- **301 (permanent):** the browser caches the redirect and never asks us again. That means less load, but we can't count clicks and expiring a link has no effect for browsers that cached it.
- **302 (temporary):** every click reaches us, so analytics and expiry work.

We need analytics and expiry, so we use **302**. We pay for it with more load, which the cache absorbs.

---

## 4. Data model

```
redirection
──────────────────────────────
id          VARCHAR(11)  PRIMARY KEY   -- Long.MAX_VALUE in base62 is 11 chars
long_url    TEXT
created_at  TIMESTAMP                  -- Stage 2
expires_at  TIMESTAMP                  -- Stage 2
user_id     VARCHAR NULL               -- Stage 6
```

The access pattern is almost entirely **lookups by primary key**, which is effectively key-value. That works well in both SQL and NoSQL, and it caches very well.

### Same long URL shortened twice: same id or different ids?
**Different ids.** Each user needs their own history, their own expiry and their own click counts. Deduplicating would also mean an extra lookup by `long_url` (and another index) on every write.

### Why MySQL
- **The numbers allow it.** 40 writes/s, 3 TB over 5 years, and most reads served by the cache. A single instance handles that comfortably.
- **It suits the access pattern.** InnoDB's clustered primary-key index makes `WHERE id = ?` a single B-tree lookup.
- **The PK constraint is a safety net.** A duplicate id fails loudly instead of silently overwriting, as an upsert in Cassandra would.
- **It covers more than key lookups.** `WHERE user_id = ? ORDER BY created_at` for history is trivial in SQL.
- **It's mature.** Backups, replication, tooling and team familiarity are all solved problems.
- **It can grow.** Read replicas first, then shard by id (no cross-link joins). Vitess is the proven route.

**Honest weaknesses:** write scaling means sharding by hand, there's no native TTL (we need a cleanup job), and multi-region active-active is hard.
**We would switch if:** writes reached ~10k/s sustained, data grew to tens of TB, or we needed multi-region writes. Postgres would serve equally well here.

---

## 5. Core problem: generating unique ids

| Approach | Pros | Cons |
|---|---|---|
| **A. Hash the long URL** | Built-in dedupe | Collisions mean a DB check and retry |
| **B. Random characters** | Unguessable | Collisions mean a DB check and retry |
| **C. Counter → base62** | No collisions, simple | Only one counter (breaks with more than one server); ids are predictable |
| **D. Counter ranges (via ZooKeeper)** | No collisions, fast, works across servers | Needs distributed coordination; a crash loses the rest of a range (harmless) |

**The plan:** C in Stage 1, then D in Stage 4, once we've actually seen C break with two servers.

### Base62
A counter makes ids **unique**. Base62 makes them **short** by writing the same number with 62 symbols (`0-9a-zA-Z`) instead of 10. For example, 6 billion is 10 characters in decimal but only 6 in base62. Different numbers always give different strings, so the ids stay unique.

### Enumeration (guessable ids)
Sequential ids can be walked one by one (see the paper *"Gone in Six Characters"*, 2016). The options:
- **Shuffled alphabet:** weak. The pattern is still visible.
- **Scrambled counter** (reversible mixing, e.g. Sqids): looks random and stays collision-free.
- **Random ids:** unguessable, but needs collision retries.

There's a catch, though: at 6B ids in a 62⁷ space, **a random guess hits a real link 1 time in 580**, whatever the algorithm. Short ids and unguessable ids work against each other. What actually helps: **short links are not access control** (private targets must require auth), **rate limiting**, and longer ids where secrecy matters.
Because `IdGenerator` is an interface, trying other strategies doesn't touch the rest of the code.

---

## 6. High-level design (target, after Stage 10)

```
                    nginx (load balancer)
                 ┌──────────┼──────────┐
             shortener  shortener  shortener      ← stateless
                 │
   ┌─────────┬───┴────────┬──────────────┬──────────────┐
 Redis     MySQL      id-gen svc      user svc      analytics svc
 cache                 + ZooKeeper    (JWT)         (async)

 Consul: service discovery     Prometheus + Grafana + Zipkin: observability
```

**Redirect (hot path, ~10k/s):** nginx → shortener → Redis (hit: 302) → on a miss, MySQL, then fill the cache → 302. Analytics is notified **asynchronously**, so it never slows a redirect down.
**Shorten (cold path, ~40/s):** take an id from the local range (ask id-gen when it runs out) → save to MySQL → 201.

---

## 7. Bottlenecks and failures

| Problem | Fix | Stage |
|---|---|---|
| Too many reads for the DB | Redis cache | 3 |
| Two servers generate the same id | Ranges via ZooKeeper | 4 |
| One server can't handle 10k/s | Stateless replicas behind nginx | 5 |
| Services can't find each other | Consul | 5 |
| Slow analytics slows redirects | Async, off the hot path | 7 |
| "Is it healthy? Why is it slow?" | Metrics, dashboards, tracing | 8 |
| id-gen or analytics goes down | Circuit breakers, fallbacks | 9 |
| "How much load can it take?" | Gatling | 10 |
