# System Architecture — Redis-Cached Trending Products

This document visualizes the demo system: a Spring Boot 4 / Java 21 web app that
serves two **cache-aside** read paths, backed by **PostgreSQL** (source of
truth) and accelerated by **Redis** (cache layer):

* `GET /api/products/trending` — *trending-products analytics*, deliberately
  expensive on a cold call (a `SUM`/`GROUP BY` over 50,000 `order_items`) so the
  win from caching it in Redis is easy to observe.
* `GET /api/products/{id}` — *high-concurrency product-detail read*, kept warm in
  Redis so a flood of concurrent reads is answered without borrowing a
  PostgreSQL connection (the load-test target — see the product-detail section
  below and [bench/README.md](bench/README.md)).

---

## 1. Deployment / container view

How the running pieces fit together. The Spring Boot app talks to two
Docker-managed backing services. Host ports differ from container ports (see
[CLAUDE.md](CLAUDE.md) and the env-var notes) to avoid clashes with other
local stacks.

```mermaid
flowchart LR
    client["HTTP client<br/>(Bruno / curl / browser)"]

    subgraph host["Local host"]
        subgraph app["Spring Boot app (Tomcat)<br/>:8090 → 8080"]
            ctrl["ProductController"]
            svc["TrendingProductService<br/>@Cacheable"]
            repo["OrderItemRepository<br/>(Spring Data JPA)"]
        end

        subgraph docker["docker compose"]
            pg[("PostgreSQL 17<br/>ecom_postgres_db<br/>:5434 → 5432")]
            redis[("Redis 8<br/>ecom_redis_cache<br/>:6385 → 6379<br/>AOF + password")]
        end
    end

    client -->|"GET /api/products/trending"| ctrl
    ctrl --> svc
    svc -->|"cache miss → query"| repo
    repo -->|"JDBC / Hibernate"| pg
    svc <-->|"GET/SET trending-products<br/>(TTL 5 min)"| redis

    classDef store fill:#fde2e2,stroke:#c0392b,color:#000;
    classDef cache fill:#e2ecfd,stroke:#2c6fc0,color:#000;
    class pg store;
    class redis cache;
```

> Ports `5434 / 6385 / 8090` are this project's host-port choices (the defaults
> `5432 / 6379 / 8080` are taken by other local stacks). The same `.env` value
> drives both the docker mapping and the Spring connection.

---

## 2. Application layering (packages → responsibilities)

The codebase is split into a **feature-sliced** domain (`feature/*`) plus a
shared `common/*` layer and an infrastructure `config/*` layer. Caching plumbing
is kept out of the domain code.

```mermaid
flowchart TD
    subgraph web["feature.product.controller"]
        PC["ProductController<br/>GET /api/products/trending"]
    end

    subgraph service["feature.product.service"]
        TPS["TrendingProductService<br/>@Cacheable('trending-products')"]
    end

    subgraph data["feature.*.repository (Spring Data JPA)"]
        OIR["OrderItemRepository<br/>findTopTrending(Pageable)"]
        PR["ProductRepository"]
        CR["CategoryRepository"]
    end

    subgraph dto["DTO"]
        TPR["TrendingProductResponse<br/>(record projection)"]
    end

    subgraph common["common.*"]
        API["ApiResponse&lt;T&gt; envelope"]
        SEED["bootstrap.DataSeeder<br/>(seeds 50k order items on startup)"]
        GEH["exception.GlobalExceptionHandler"]
        BE["BaseEntity<br/>(pooled SEQUENCE id)"]
    end

    subgraph cfg["config.*"]
        RC["RedisConfig<br/>RedisCacheManager + JSON serializer"]
        BATCH["HibernateBatchConfig<br/>(JDBC batching)"]
    end

    PC --> TPS
    PC -. wraps result in .-> API
    TPS --> OIR
    OIR -. projects into .-> TPR
    TPS -. cache config from .-> RC
    SEED --> PR & CR & OIR
```

---

## 3. The Redis cache-aside flow (the heart of the demo)

`TrendingProductService.getTrendingProducts()` is annotated `@Cacheable`, so
Spring's caching aspect intercepts the call. The **first** call is a cache miss
(slow Postgres aggregation); every call within the **5-minute TTL** is a cache
hit served straight from Redis without touching the DB.

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant API as ProductController
    participant Aspect as Spring Cache Aspect
    participant R as Redis<br/>(trending-products)
    participant S as TrendingProductService
    participant DB as PostgreSQL

    C->>API: GET /api/products/trending
    API->>Aspect: getTrendingProducts()
    Aspect->>R: GET "trending-products::getTrendingProducts"

    alt Cache MISS (cold — first call / after TTL)
        R-->>Aspect: (nil)
        Aspect->>S: invoke method body
        Note over S,DB: log: "Cache miss — aggregating…"
        S->>DB: SUM(quantity) GROUP BY product<br/>ORDER BY ... LIMIT 10
        DB-->>S: top-10 rows → List<TrendingProductResponse>
        S-->>Aspect: result
        Aspect->>R: SET key = JSON, TTL = 5 min
        Aspect-->>API: result
    else Cache HIT (warm — within 5 min)
        R-->>Aspect: cached JSON
        Note over Aspect: method body NEVER runs<br/>(no DB query, no log line)
        Aspect-->>API: deserialized result
    end

    API-->>C: 200 OK · ApiResponse<List<…>>
```

**Key (cache layout in Redis):**

| Property        | Value                                                                   |
|-----------------|-------------------------------------------------------------------------|
| Cache bucket    | `trending-products` (`RedisConfig.TRENDING_PRODUCTS_CACHE`)             |
| Redis key       | `trending-products::getTrendingProducts` (`key = "#root.methodName"`)   |
| TTL             | 5 minutes (configured in `RedisConfig`)                                |
| Value format    | JSON via `GenericJacksonJsonRedisSerializer` (Jackson 3, default typing)|
| Why default typing | so `List<TrendingProductResponse>` round-trips to its concrete type, not `LinkedHashMap` |

> **Observing it:** a cold call logs `Cache miss — aggregating top 10 trending
> products from PostgreSQL` and responds slowly; a warm call logs nothing and
> responds fast. To force a fresh cold path, delete the key:
> `redis-cli -a "$REDIS_PASSWORD" DEL "trending-products::getTrendingProducts"`.

---

## 4. Data model (ERD)

The aggregation joins `order_items → products`; categories exist for the broader
catalog. All ids come from one shared pooled `SEQUENCE` (`BaseEntity`), and all
associations are `LAZY` so the analytics query never hydrates entities.

```mermaid
erDiagram
    CATEGORIES ||--o{ PRODUCTS : "has"
    PRODUCTS   ||--o{ ORDER_ITEMS : "sold as"

    CATEGORIES {
        bigint id PK
        string name
        string slug UK
    }
    PRODUCTS {
        bigint id PK
        bigint category_id FK
        string name
        decimal price
        int    stock_quantity
        text   description
    }
    ORDER_ITEMS {
        bigint id PK
        bigint product_id FK
        int    quantity
    }
```

Seeded by `DataSeeder` on `ApplicationReadyEvent` (idempotent — skips if any
categories exist): **20 categories · 1,000 products · 50,000 order items**,
bulk-inserted with Hibernate JDBC batching to make the cold aggregation
genuinely slow.

---

## 5. Request lifecycle at a glance

```mermaid
flowchart LR
    A["Client request"] --> B["ProductController<br/>/api/products/trending"]
    B --> C{"In Redis<br/>& not expired?"}
    C -->|"Yes (hit)"| D["Deserialize JSON<br/>from Redis"]
    C -->|"No (miss)"| E["Aggregate in PostgreSQL<br/>SUM/GROUP BY · top 10"]
    E --> F["Store JSON in Redis<br/>TTL 5 min"]
    F --> G["Wrap in ApiResponse"]
    D --> G
    G --> H["200 OK → Client"]
```

---

## 6. Second battleground — high-concurrency product-detail reads

A different shape of cache win: not an expensive aggregation, but a *hot key*.
`GET /api/products/{id}` is served by `ProductService.getProductById`, annotated
`@Cacheable(cacheNames = "products", sync = true)`. Once an id is warm, a flood
of concurrent reads is answered entirely from Redis and the PostgreSQL
connection pool stays idle.

```mermaid
sequenceDiagram
    autonumber
    actor C as Clients<br/>(1000s, concurrent)
    participant API as ProductController
    participant Aspect as Spring Cache Aspect<br/>(sync = true)
    participant R as Redis<br/>(products)
    participant S as ProductService
    participant DB as PostgreSQL

    C->>API: GET /api/products/{id}
    API->>Aspect: getProductById(id)
    Aspect->>R: GET "products::{id}"

    alt Cache HIT (warm — the spike)
        R-->>Aspect: cached JSON
        Aspect-->>API: ProductResponse (no DB query)
    else Cache MISS (cold — first request only)
        Note over Aspect,DB: sync=true → a per-key lock admits ONE loader;<br/>concurrent callers block, then read the fresh value
        Aspect->>S: invoke method body (single thread)
        S->>DB: select … from products where id = ?
        DB-->>S: row → ProductResponse
        S-->>Aspect: result
        Aspect->>R: SET "products::{id}" = JSON, TTL = 10 min
        Aspect-->>API: result
    end

    API-->>C: 200 OK · ApiResponse<ProductResponse>
```

**Key (cache layout in Redis):**

| Property        | Value                                                                   |
|-----------------|-------------------------------------------------------------------------|
| Cache bucket    | `products` (`RedisConfig.PRODUCTS_CACHE`)                               |
| Redis key       | `products::{id}` (`key = "#id"`)                                        |
| TTL             | 10 minutes (configured in `RedisConfig`)                               |
| Stampede guard  | `@Cacheable(sync = true)` — one loader per key on a cold miss          |
| Value format    | `ProductResponse` record as JSON (same Jackson-3 serializer as above)  |

The dangerous case is a *cold spike*: thousands of simultaneous requests for an
id that is **not** yet cached. Without `sync = true` they would all stampede the
connection pool; with it, exactly one thread loads from PostgreSQL while the rest
block briefly and then read the freshly-cached value. The
[bench harness](bench/README.md) drives this scenario with Bombardier.

---

### Component reference

| Concern               | Type / file                                                                 |
|-----------------------|------------------------------------------------------------------------------|
| HTTP endpoint         | [ProductController](src/main/java/com/example/demo/feature/product/controller/ProductController.java) |
| Trending read logic   | [TrendingProductService](src/main/java/com/example/demo/feature/product/service/TrendingProductService.java) |
| Aggregation query     | [OrderItemRepository](src/main/java/com/example/demo/feature/order/repository/OrderItemRepository.java) |
| Trending projection   | [TrendingProductResponse](src/main/java/com/example/demo/feature/product/dto/TrendingProductResponse.java) |
| Product-detail read   | [ProductService](src/main/java/com/example/demo/feature/product/service/ProductService.java) (+ `ProductServiceImpl`) |
| Product CRUD          | [ProductRepository](src/main/java/com/example/demo/feature/product/repository/ProductRepository.java) |
| Detail projection     | [ProductResponse](src/main/java/com/example/demo/feature/product/dto/ProductResponse.java) |
| Redis cache wiring    | [RedisConfig](src/main/java/com/example/demo/config/RedisConfig.java)        |
| Response envelope     | [ApiResponse](src/main/java/com/example/demo/common/api/ApiResponse.java)    |
| Benchmark data        | [DataSeeder](src/main/java/com/example/demo/common/bootstrap/DataSeeder.java) |
| Backing services      | [docker-compose.yml](docker-compose.yml) (Postgres 17, Redis 8)              |
