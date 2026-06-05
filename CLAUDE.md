# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Spring Boot 4.0.6 / Java 21 web application demonstrating **Redis cache-aside** acceleration over PostgreSQL, wiring together PostgreSQL (JPA), Redis (cache), and Lombok. Packaged as a **WAR** (`<packaging>war</packaging>`) with Tomcat marked `provided`.

The code is organized **feature-first** under `com.example.demo` (note the package root is `feature/*`, singular):

- `feature/product` — the demo's center. [ProductController](src/main/java/com/example/demo/feature/product/controller/ProductController.java) exposes two cached read paths: `GET /api/products/trending` (a `SUM`/`GROUP BY` over `order_items`, cached in the `trending-products` bucket, 5-min TTL) and `GET /api/products/{id}` (a high-concurrency hot-key read cached in the `products` bucket, 10-min TTL, with `@Cacheable(sync = true)` as a stampede guard).
- `feature/category`, `feature/order` — supporting entities/repositories; `OrderItemRepository.findTopTrending` runs the trending aggregation and projects straight into a flat DTO.
- `common/*` — shared `ApiResponse` envelope, `GlobalExceptionHandler` + `ResourceNotFoundException`, `BaseEntity` (pooled-SEQUENCE id), `DataSeeder` (bulk-loads 20 categories / 1,000 products / 50,000 order items on `ApplicationReadyEvent`), and `HibernateBatchConfig`.
- `config/*` — `RedisConfig` (cache manager + Jackson-3 JSON value serializer).

See [README.md](README.md) for architecture diagrams and [bench/README.md](bench/README.md) for the load test.

## Commands

Use the Maven wrapper (`mvnw`); it auto-downloads Maven 3.9.16, so no local Maven install is needed.

```bash
./run.sh                                          # recommended: start infra, load .env, run app
./run.sh --skip-tests                             # same, passing -DskipTests to spring-boot:run
./mvnw clean package                              # build -> target/demo-0.0.1-SNAPSHOT.war
./mvnw spring-boot:run                            # run locally (needs env vars + infra, see below)
./mvnw test                                       # run all tests (needs Postgres + Redis running)
./mvnw test -Dtest=DemoApplicationTests#contextLoads   # run a single test method
docker compose up -d                              # start Postgres 17 + Redis 8 (reads .env)
```

[run.sh](run.sh) is the canonical local-run path: it runs `docker compose up -d`, sleeps 3s for the DB, exports `.env` into the shell (`export $(cat .env | xargs)`), then launches `./mvnw spring-boot:run`. Make it executable first with `chmod +x run.sh`.

## Configuration & the env-var gotcha

[application.properties](src/main/resources/application.properties) references environment variables with **no fallback defaults** (e.g. `${POSTGRES_HOST}`, `${REDIS_PASSWORD}`; only `APP_NAME` has a default). The app will fail to start if these are unset.

Critically, **`.env` is only loaded by docker-compose, not by Spring Boot.** Running `./mvnw spring-boot:run` or `./mvnw test` *directly* will not pick up `.env` — you must export the variables into the shell first. [run.sh](run.sh) handles this for the run case (via `export $(cat .env | xargs)`); for `./mvnw test` you must export them yourself. Required vars are listed in [.env.example](.env.example): `POSTGRES_{USER,PASSWORD,DB,PORT,HOST}`, `REDIS_{PASSWORD,PORT,HOST}`, `APP_NAME`, `SERVER_PORT`.

**Host-port conflicts:** `POSTGRES_PORT`, `REDIS_PORT`, and `SERVER_PORT` are *host* ports (docker maps `${POSTGRES_PORT}:5432` / `${REDIS_PORT}:6379`; the app's Tomcat binds `SERVER_PORT`). The same `.env` value drives both the container mapping and the Spring connection, so changing one value keeps them consistent. If `docker compose up` fails with *"port is already allocated"* or the app fails with *"port ... already in use"*, another process holds that port — bump the value in `.env`. `server.port` defaults to `8080` ([application.properties](src/main/resources/application.properties)) when `SERVER_PORT` is unset.

The test suite is mixed: `RedisConfigTest` is a **pure unit test** (no Spring context, no infra — it exercises the cache serializer round-trip directly), while `DemoApplicationTests` (`contextLoads`) and `ProductCachingIntegrationTest` are full `@SpringBootTest`s with no test profile or embedded DB. So **`./mvnw test` as a whole requires a live Postgres and Redis** with the env vars exported — the integration tests are not self-contained.

## Key infrastructure facts

- **JPA**: `spring.jpa.hibernate.ddl-auto=update` — schema is auto-migrated from entities; `show-sql` is on.
- **Cache**: `spring.cache.type=redis`, enabled via `@EnableCaching` in `RedisConfig` — `@Cacheable` reads are backed by Redis (the `trending-products` and `products` buckets, with per-bucket TTLs and a JSON value serializer).
- **WAR + provided Tomcat**: the build produces a deployable WAR; `./mvnw spring-boot:run` still works for local dev via the Spring Boot plugin.
- **Lombok** is an annotation processor (configured in the `maven-compiler-plugin` and excluded from the repackaged artifact) — use Lombok annotations freely in new code.
