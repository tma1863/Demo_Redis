# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Spring Boot 4.0.6 / Java 21 web application scaffold wiring together PostgreSQL (JPA), Redis (cache), and Lombok. Packaged as a **WAR** (`<packaging>war</packaging>`) with Tomcat marked `provided`. The codebase is currently a skeleton: only [DemoApplication.java](src/main/java/com/example/demo/DemoApplication.java) (the `@SpringBootApplication` entrypoint) and the `contextLoads` smoke test exist — no controllers, entities, or repositories yet.

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

Because the only test is a full `@SpringBootTest` (`contextLoads`) with no test profile or embedded DB, **`./mvnw test` requires a live Postgres and Redis** with the env vars set — it is not a self-contained unit test.

## Key infrastructure facts

- **JPA**: `spring.jpa.hibernate.ddl-auto=update` — schema is auto-migrated from entities; `show-sql` is on.
- **Cache**: `spring.cache.type=redis` — `@Cacheable`/`@CacheEvict` annotations are backed by Redis once enabled.
- **WAR + provided Tomcat**: the build produces a deployable WAR; `./mvnw spring-boot:run` still works for local dev via the Spring Boot plugin.
- **Lombok** is an annotation processor (configured in the `maven-compiler-plugin` and excluded from the repackaged artifact) — use Lombok annotations freely in new code.
