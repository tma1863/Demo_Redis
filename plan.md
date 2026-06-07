# Refactoring Plan — demo_redis

> Spring Boot 4.0.6 / Java 21 — Redis cache-aside demo over PostgreSQL.
> This plan is the output of a 9-dimension review whose every proposed change
> was adversarially vetted against the demo's pedagogical purpose. The actionable
> set is deliberately small; most "obvious" refactors were rejected as
> over-engineering (see [Appendix A](#appendix-a--deliberately-not-doing)).

## Status at a glance

| Stage | Title | Status |
|-------|-------|--------|
| 0 | Documentation accuracy | ✅ **Done** (2026-06-05) |
| 1 | Cache-name decoupling + config split-brain | ✅ **Done** (2026-06-05) |
| 2 | Service interfaces + dead-surface cleanup | ✅ **Done** |
| 3 | Persistence safety | ✅ **Done** (2026-06-05) |
| 4 | Teaching comments | ✅ **Done** (2026-06-05) |
| 5 | Testing | ⏸️ Planned — **needs a human decision** |

---

## Guiding principles

1. **Clarity and benchmark validity are first-class.** This is a teaching/benchmark
   project. A change that adds layers, ceremony, or config indirection without
   making the demo clearer or the numbers more valid is **over-engineering** and is
   rejected.
2. **No behavioral surprises.** Cache literals, key strategies, and the seeded-id
   convention (`id 1` is a Category that 404s; real products ≈ 1002–2001) must be
   preserved exactly.
3. **Each stage is independently shippable and verifiable** with `./mvnw test`
   (or a compile for doc/comment-only changes). Stages are ordered so earlier ones
   de-risk later ones.

---

## Resolved design decisions

These were the five open questions. Where the human chose differently from the
review's recommendation, **the human's choice wins** and is recorded here.

| # | Question | Decision | Notes |
|---|----------|----------|-------|
| a | Interfaces for services? | **Interface for *every* service** | ⚠️ Human override (review recommended concrete/interface-none). → Stage 2 *adds* a `TrendingProductService` interface, rather than removing the `ProductService` one. |
| b | Where does "trending" analytics belong? | **Stays in `feature.product`** | Served at `/api/products/trending`; a `feature.analytics` slice just relocates inherent cross-entity coupling and risks the JPQL FQN. |
| c | Cache name / TTL ownership? | **Names → `common/cache/CacheNames`; TTLs + serializer stay in `RedisConfig`** | The bucket name is a domain contract; TTLs are the pedagogical point and stay compiled-in. |
| d | Battleground-2 (advanced filtering)? | **Delete the speculation** | Drop the unused `JpaSpecificationExecutor` + dead javadoc. Re-add when the first real `Specification` lands. |
| e | Testing approach? | **Decide later** (human deferred) | See Stage 5 for the two candidate paths. |

---

## Open decisions needing human input

1. **Stage 5 — Testcontainers vs. status quo.** Adopting Testcontainers makes
   `./mvnw test` self-contained but requires a Docker daemon at test time. The
   lighter alternative only improves failure legibility. **Pick one before Stage 5.**
2. **Stage 2 — confirm no in-flight `Specification` branch** before deleting
   `JpaSpecificationExecutor`. If such a branch exists, keep it with a `TODO` instead.
3. **DTO field rename `productId` → `id`** is **rejected** (breaks bench
   auto-discovery). Only revisit on explicit override, done atomically with the
   bench script + bench/README.

---

## Stage 0 — Documentation accuracy ✅ DONE (2026-06-05)

Pure doc fixes; no code touched. Completed this stage already:

- **README.md** — fixed all broken links (`features/*` → `feature/*` **and** the
  `../` prefix that escaped the repo root — the file was authored as if it lived in
  the now-empty `docs/` dir). Reframed the intro to **two** cache-aside read paths;
  added a full **Battleground-3** section (sequence diagram + cache-layout table) for
  `GET /api/products/{id}`; fixed the private-`TRENDING_PRODUCTS_TTL` reference to
  "configured in `RedisConfig`"; expanded the component-reference table with
  `ProductService` / `ProductRepository` / `ProductResponse`.
- **CLAUDE.md** — replaced the stale "skeleton… no controllers/entities/repositories
  yet" overview with the real feature-first layout; corrected the testing note
  (`RedisConfigTest` is a pure unit test; the two `@SpringBootTest`s need live infra);
  fixed the cache fact.
- **bench/README.md** — reconciled the `PRODUCT_ID` contradiction (doc said "not
  configurable"; the script honors it as the discovery-failure fallback). Fixed the
  doc, added the missing knobs-table row.
- **HELP.md** — gitignored (`.gitignore:1`), never reaches commits → deletion moot.
  Local `rm`/`git rm` blocked by permission settings; the file is harmless. Remove
  manually if desired.

**Verification (passed):** no `features` / `../` / `TRENDING_PRODUCTS_TTL` /
`skeleton` references remain; all 13 README file links resolve to real files.

> ⚠️ **Re-drift guard:** if Stages 1–3 land, re-check the CLAUDE.md layout
> description and README component table still match (e.g. the new `CacheNames`
> class, the `TrendingProductServiceImpl` rename).

---

## Stage 1 — Cache-name decoupling + config split-brain ✅ DONE (2026-06-05)

**Goal:** stop feature code importing the infra `@Configuration` class just to read
a bucket-name `String`, and remove the contradictory "owns global configuration"
javadoc — *without* moving classes.

> ✅ **Completed 2026-06-05.** Commit 1a (split-brain javadoc on `HibernateBatchConfig`
> + `config/package-info`) was already in place. Commit 1b repointed `RedisConfig`,
> `ProductServiceImpl`, `TrendingProductService`, and `ProductCachingIntegrationTest`
> at `common/cache/CacheNames` and removed the duplicate name constants from
> `RedisConfig` (single source of truth). Verified: `./mvnw test` → **4/4 pass** (the
> caching test still proves 6 reads → 1 DB call against the real `products` bucket);
> `grep` confirms the bucket literals now live only in `CacheNames`.

### Commit 1a — Resolve the config "split-brain" via javadoc (no class moves)
- **Before:** both `common/config/HibernateBatchConfig.java` and
  `config/package-info.java` claim to own "global configuration."
- **After:** edit `HibernateBatchConfig`'s javadoc to state the real reason it lives
  in `common.config`: it is co-located with the `BaseEntity` pooled-SEQUENCE change
  and the `DataSeeder` it enables (JDBC batching is meaningless without the pooled
  SEQUENCE, so co-location keeps the dependency visible). Optionally add one sentence
  to `config/package-info.java` clarifying it holds framework/infra beans (Redis),
  while domain-coupled tuning sits beside its primitive in `common.config`.
- **Why:** removes the contradiction with ~1/10th the churn of moving classes; keeps
  `RedisConfig` purely infra.
- **Effort:** trivial. **Risk:** none (javadoc only).
- **Verify:** `./mvnw -q -DskipTests compile`.

### Commit 1b — Introduce `common/cache/CacheNames`
- **Before:** `RedisConfig.PRODUCTS_CACHE` / `RedisConfig.TRENDING_PRODUCTS_CACHE`
  are imported into the feature services (feature → infra-config coupling).
- **After:** new final class `com.example.demo.common.cache.CacheNames` (private
  ctor) with **byte-identical literals**:
  ```java
  public static final String PRODUCTS = "products";
  public static final String TRENDING_PRODUCTS = "trending-products";
  ```
  Repoint every reference at `CacheNames`:
  - `RedisConfig` — the `withCacheConfiguration(...)` keys (keep TTL constants + the
    serializer here; they are genuine infra).
  - `ProductServiceImpl` — `@Cacheable(cacheNames = CacheNames.PRODUCTS, ...)`,
    remove `import ...config.RedisConfig`.
  - `TrendingProductService(Impl)` — `@Cacheable` + the javadoc `{@link}`s.
  - **`ProductCachingIntegrationTest.java`** (lines ~25, ~83) —
    `RedisConfig.PRODUCTS_CACHE` → `CacheNames.PRODUCTS`. *(This file was missing
    from the original finding; skipping it leaves a stray feature→config import.)*
  - You may keep `PRODUCTS_CACHE` / `TRENDING_PRODUCTS_CACHE` in `RedisConfig` as
    `= CacheNames.X` aliases, or remove them once all callers move. Prefer removing
    to avoid two sources of truth.
- **Why:** the bucket name is a domain contract; today every cacheable service
  imports the infra `@Configuration` to read a String. No enum, no registry.
- **Effort:** small. **Risk:** ⚠️ literal values **must** stay identical or warm
  reads silently miss on first run.
- **Verify:** `./mvnw test` (the caching test asserts 6 reads → 1 DB call against the
  real bucket) and `grep -rn '"products"\|"trending-products"' src` shows the two
  literals only in `CacheNames`.

---

## Stage 2 — Service interfaces + dead-surface cleanup

**Goal:** make the service layer consistent in the **interface-for-every-service**
direction (per the human's choice), and delete speculative dead surface.

### Commit 2a — Add a `TrendingProductService` interface (level *up* to interfaces)
> ⚠️ This follows the human override (decision **a**). The review's own
> recommendation was the opposite (collapse `ProductService` to concrete). Do **not**
> collapse `ProductService`; instead make `TrendingProductService` match the
> interface+impl shape.

- **Before:** `ProductService` (interface) + `ProductServiceImpl`;
  `TrendingProductService` is a concrete `@Service` with no interface.
- **After:**
  - New interface `feature/product/service/TrendingProductService.java`:
    ```java
    public interface TrendingProductService {
        /** Top-N products by total units sold; cold call hits PostgreSQL, warm calls served from Redis. */
        List<TrendingProductResponse> getTrendingProducts();
    }
    ```
  - Rename the existing class → `TrendingProductServiceImpl`, add
    `implements TrendingProductService` and `@Override`. Keep `@Slf4j`, `@Service`,
    `@RequiredArgsConstructor`, the `@Cacheable(...)` (stays on the impl method), and
    `TRENDING_LIMIT`.
  - **`ProductController` needs no edit** — it already injects a field typed
    `TrendingProductService`, which is now the interface.
- **Why:** consistency with the existing `ProductService` pattern, as chosen.
- **Effort:** small. **Risk:** low — Spring proxies an interface-backed `@Service`
  via a JDK dynamic proxy; `@Cacheable` on the impl still intercepts. Confirm the
  caching test still passes (proxy type change is transparent to MockMvc).
- **Verify:** `./mvnw test`.

### Commit 2b — Drop `JpaSpecificationExecutor` + advanced-filtering javadoc
- **Before:** `ProductRepository extends JpaRepository<Product,Long>,
  JpaSpecificationExecutor<Product>` with "upcoming advanced-filtering battleground"
  javadoc; `Product.java` javadoc lists three battlegrounds.
- **After:** `extends JpaRepository<Product, Long>` only; remove the unused
  `import ...JpaSpecificationExecutor`; replace the speculative javadoc with
  `/** Standard CRUD for {@link Product}. */`. Edit `Product.java` class javadoc to
  list only the **two implemented** battlegrounds (trending analytics + high-
  concurrency detail).
- **Why:** textbook speculative generality — zero callers, no `Specification`
  anywhere, README never mentions filtering; the javadoc misleads a reader into
  hunting for code that isn't there.
- **Effort:** trivial. **Risk:** none (no callers). **Precondition:** confirm no
  in-flight filtering branch (open decision #2).
- **Verify:** `./mvnw -q -DskipTests compile` then `./mvnw test`.

> If Stage 2 lands, update the README "advanced filtering… not yet implemented" note
> and the CLAUDE.md `JpaSpecificationExecutor` sentence (both currently describe the
> speculation as present).

---

## Stage 3 — Persistence safety ✅ DONE (2026-06-05)

**Goal:** remove a latent mass-delete/N+1 foot-gun on an unused collection; small
correctness tightening. Per the human's choice: **strip the cascade, keep the field.**

> ✅ **Completed 2026-06-05.** 3a — stripped `cascade=ALL, orphanRemoval=true` from
> `Category.products` (now plain `@OneToMany(mappedBy = "category", fetch = LAZY)`) and
> dropped the unused `CascadeType` import. 3b — tightened `slug` to
> `@Column(nullable = false, unique = true)`. 3c — **not done (left in place):** the
> Lombok caveat held — `@Builder` + `@NoArgsConstructor` requires the explicit
> `@AllArgsConstructor` (compile failed: "constructor Category cannot be applied to
> given types" from the generated builder), so `@AllArgsConstructor` stays on all three
> entities. Verified: `./mvnw -q -DskipTests compile` clean; `./mvnw test` → **4/4 pass**.

### Commit 3a — Strip cascade/orphanRemoval from `Category.products`
- **Before** (`Category.java:46-47`):
  ```java
  @OneToMany(mappedBy = "category", fetch = FetchType.LAZY,
          cascade = CascadeType.ALL, orphanRemoval = true)
  ```
- **After:**
  ```java
  @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
  ```
  Remove the now-unused `import jakarta.persistence.CascadeType;`. Keep the field,
  `@Builder.Default`, the javadoc, and `@ToString(exclude = "products")`.
- **Why:** nothing reads/writes the inverse collection (grep: zero `getProducts`
  call sites; `DataSeeder` sets only the owning side via `getReference`).
  `cascade=ALL + orphanRemoval` on a ~1000-child collection is a latent foot-gun: an
  accidental category delete/load-and-mutate would hydrate the collection and issue
  per-row DELETEs — defeating the very JDBC batching the demo showcases (a
  benchmark-validity hazard).
- **Effort:** trivial. **Risk:** none to current behavior (nothing cascades today).
- **Verify:** `./mvnw -q -DskipTests compile`; `./mvnw test`.

### Commit 3b — Tighten `slug` (small, optional, fold into 3a)
- **Before:** `@Column(unique = true) private String slug;`
- **After:** `@Column(nullable = false, unique = true) private String slug;`
- **Why:** a unique business key shouldn't be NULLable; the seeder always sets it.
- **Risk:** under `ddl-auto=update` an existing NULL slug would block the constraint
  — fine on a fresh demo DB; note it if running against a pre-seeded volume.

### Commit 3c — Drop `@AllArgsConstructor` from entities (OPTIONAL — verify compile)
- Entities are constructed builder-only; `@AllArgsConstructor` is redundant API.
- **⚠️ Lombok caveat:** `@Builder` + `@NoArgsConstructor` together often *require* an
  explicit `@AllArgsConstructor` (Lombok won't auto-generate the all-args ctor once a
  no-args ctor is declared). **Only remove if the build still compiles**; otherwise
  leave it. **Do NOT remove `@Setter`/`setId`** — `ProductCachingIntegrationTest`
  calls `setId(...)` (lines ~138/147) and asserts those ids.
- **Verify:** `./mvnw -q -DskipTests compile` must pass; if it fails, revert 3c.

---

## Stage 4 — Teaching comments (pure in-code docs, zero behavior) ✅ DONE (2026-06-05)

Bundle these trivial, zero-risk additions. They make the demo's pedagogy explicit.

> ✅ **Completed 2026-06-05.** 4a — added the `sync=true` Battleground-3 comment above
> `ProductServiceImpl`'s `@Cacheable` and the "sync intentionally OFF" comment above
> `TrendingProductServiceImpl`'s. 4b — class javadoc on `OrderItem` (fact table) and
> `BaseEntity` (pooled-SEQUENCE superclass). 4c — the parameterless cache-key invariant
> comment above the trending `@Cacheable`; `key = "#root.methodName"` and
> `ProductServiceImpl`'s `key = "#id"` left as-is. Verified: `./mvnw -q -DskipTests compile`
> clean (comment/javadoc only, zero behavior change).

### Commit 4a — Document the `sync=true` asymmetry
- `ProductServiceImpl`, above `@Cacheable(... sync=true)`:
  `// sync=true is THE Battleground-3 mechanism: under a concurrent cold miss only one thread loads from PostgreSQL (no thundering herd).`
- `TrendingProductServiceImpl` (the `@Cacheable` for trending):
  `// sync intentionally OFF: a 5-min analytics result tolerates a brief cold stampede, and a sync lock would serialize warm reads and skew the benchmark.`

### Commit 4b — Class javadoc on `OrderItem` and `BaseEntity`
- `OrderItem`: `/** A single product line within an order — the fact table the trending aggregation sums over. */`
- `BaseEntity`: `/** Mapped superclass providing the shared pooled-SEQUENCE primary key for all entities. */`
- Do **not** sweep repositories / `DemoApplication`.

### Commit 4c — Trending cache-key invariant comment
- Keep `key = "#root.methodName"` (readable Redis key
  `trending-products::getTrendingProducts`, good for `redis-cli` inspection).
- Add near the trending `@Cacheable`:
  `// Parameterless by design → this bucket holds exactly one entry; if a limit param is ever added, change key to "#limit" so entries vary by argument.`
- Leave `ProductServiceImpl`'s `key = "#id"` as-is.

**Effort:** trivial. **Risk:** none. **Verify:** `./mvnw -q -DskipTests compile`.

---

## Stage 5 — Testing (HUMAN DECISION REQUIRED — see open decision #1)

Do this last; it's the heaviest and depends on a team call. The diagnosis is sound:
`./mvnw test` fails out-of-the-box with an opaque `Could not resolve placeholder
POSTGRES_HOST`, and the headline Battleground 1 has **no** automated proof while the
secondary one does.

### Path A (recommended) — Testcontainers, scoped per test
- `pom.xml`: add test-scoped Testcontainers BOM (prefer the Boot-4-parent-managed
  version) + `junit-jupiter`, `postgresql`, `com.redis:testcontainers-redis`.
- `ProductCachingIntegrationTest`: add a **Redis-only** `@Container` via
  `@DynamicPropertySource` (repo + seeder are `@MockitoBean`-mocked, so Postgres is
  never queried). To stop JPA auto-config forcing a live DataSource at boot, bind a
  test-scoped H2 via the same `@DynamicPropertySource` **or** exclude
  `DataSourceAutoConfiguration`. Real Redis is required (the Jackson-3 serializer
  round-trip is load-bearing).
- `DemoApplicationTests#contextLoads`: boots the genuine un-mocked context (real
  repo + real `DataSeeder` `ApplicationReadyEvent`) → needs **both** Postgres and
  Redis containers. Keep it (different TestContext cache key from the mocked test —
  not redundant).
- Keep `RedisConfigTest` context-free and untouched (the canonical fast unit test;
  keep `cacheValueSerializer()` package-visible).
- Defer an abstract base class until a third integration test exists.
- Update CLAUDE.md/README: `./mvnw test` now needs only a Docker daemon; drop the
  stale "export env vars" precondition from the test javadoc.

### Path B (lighter) — legibility only
- Add `src/test/resources/application.properties` with safe localhost **placeholder
  defaults** (`${POSTGRES_HOST:localhost}`, `${POSTGRES_PORT:5434}`,
  `${REDIS_HOST:localhost}`, `${REDIS_PORT:6385}`, … matching `.env`) so a missing
  export fails with a clear connection error instead of an opaque placeholder error.
- Do **not** add `ddl-auto=create-drop`, Testcontainers, or H2. Tests still need
  infra but fail legibly.

### Commit 5b (either path) — Add the missing trending test
- Add `trendingProductsAggregateHitDatabaseExactlyOnce` to
  `ProductCachingIntegrationTest`: `@MockitoBean OrderItemRepository`, stub
  `findTopTrending(any())`, drive `GET /api/products/trending` through MockMvc 6×
  (assert the envelope + `$.data[0].productId`), then
  `verify(orderItemRepository, times(1)).findTopTrending(any())`.
- In `@BeforeEach`, **clear** the trending bucket
  (`cacheManager.getCache(CacheNames.TRENDING_PRODUCTS).clear()`) — not `evict(id)` —
  because the key is the fixed method name.
- Update the class javadoc to say it proves Battlegrounds 1 and 3.
- **Verify:** `./mvnw test`.

---

## Appendix A — Deliberately NOT doing (rejected)

One-line rationale each; these are over-engineering, demo-breaking, or no-ops.

- **`feature.analytics` slice / move `TrendingProductResponse`** — relocates inherent
  cross-entity coupling without removing it; risks the JPQL FQN.
- **`BaseEntity` → `common.persistence`** — inventing a package for one foundational
  mapped-superclass is future-proofing churn; root of `common/` is conventional.
- **`@Transactional(readOnly)` on read services** — false "outside an open session"
  premise (OSIV on by default; `getId()` on a proxy never SELECTs); changes zero
  queries.
- **Auditing / `@Version` columns** — read-only-after-seed data; invites unbounded
  "why-not-also-X" annotations.
- **Externalize cache TTLs / per-feature cache-name ownership / `TRENDING_LIMIT`
  to properties** — the TTLs/limit are the pedagogical point, co-located with the
  wiring; externalizing adds indirection and benchmark-key risk.
- **Per-type cache serializer (drop `enableUnsafeDefaultTyping`)** — defends a threat
  that can't reach this local single-writer Redis while risking silent
  `LinkedHashMap` corruption of the trending result; the method name already
  discloses the trade-off.
- **DataSource env defaults / profile-properties split** — machine-specific defaults
  (5434/6385) are a silent-wrong-DB foot-gun; a bench profile with `show-sql=false`
  would delete the demo's own proof (the single warm-spike SELECT). Fail-fast +
  `run.sh` already deliver "just run it."
- **Per-entity sequences / collapse the three `1000` knobs** — would destroy the
  `id 1`-is-a-Category cache-penetration convention; `allocationSize` ≠
  `jdbc.batch_size` ≠ flush-chunk are three distinct knobs.
- **Delete `HibernateBatchConfig` / inline its props** — scatters its load-bearing
  SEQUENCE-vs-IDENTITY teaching javadoc into a properties blob.
- **`@WebMvcTest` slice split / three-tier test taxonomy** — adds unused mocks and
  duplicated assertions for a 3-file suite.
- **Remove `contextLoads`** — not redundant; the only test of the real un-mocked
  wiring (different TestContext cache key from the mocked integration test).
- **Name-based JPQL projection (avoid the hardcoded FQN)** — introduces an
  alias-matching footgun threatening benchmark output; the cross-link javadoc exists.
- **Rename `productId` → `id`** — cosmetic; breaks bench auto-discovery
  (`run-benchmark.sh:49,52`). Only via explicit override, done atomically with the
  bench files.
- **Remove `ApiResponse.success(T)` overload / `Category.slug`** — both complete the
  illustrative example / are canonical catalog data; deleting churns tests for no gain.

---

## Appendix B — Verification cheatsheet

```bash
# Compile-only (doc/comment/structure changes)
./mvnw -q -DskipTests compile

# Full suite (needs infra per Stage 5 choice; the caching test counts DB lookups)
./mvnw test

# Cache literals unchanged (after Stage 1)
grep -rn '"products"\|"trending-products"' src   # → only common/cache/CacheNames

# Docs still accurate (after any package move)
grep -rn 'demo/features\|skeleton\|TRENDING_PRODUCTS_TTL' README.md CLAUDE.md  # → nothing
```

---

## Appendix C — File map (paths from repo root)

| Area | Files |
|------|-------|
| Cache config | `src/main/java/com/example/demo/config/RedisConfig.java`, `config/package-info.java` |
| New (Stage 1) | `src/main/java/com/example/demo/common/cache/CacheNames.java` |
| Hibernate batching | `src/main/java/com/example/demo/common/config/HibernateBatchConfig.java` |
| Product feature | `feature/product/controller/ProductController.java`, `service/ProductService.java` (+ `ProductServiceImpl.java`), `service/TrendingProductService.java` (→ `+ Impl` in Stage 2), `repository/ProductRepository.java`, `dto/ProductResponse.java`, `dto/TrendingProductResponse.java`, `entity/Product.java` |
| Category / Order | `feature/category/entity/Category.java`, `feature/order/entity/OrderItem.java`, `feature/order/repository/OrderItemRepository.java` |
| Common | `common/BaseEntity.java`, `common/api/ApiResponse.java`, `common/exception/*`, `common/bootstrap/DataSeeder.java` |
| Tests | `src/test/java/com/example/demo/feature/product/ProductCachingIntegrationTest.java`, `config/RedisConfigTest.java`, `DemoApplicationTests.java` |
| Docs | `README.md`, `CLAUDE.md`, `bench/README.md` |
