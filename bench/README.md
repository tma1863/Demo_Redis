# Battleground 3 — load testing the hot product-detail read

Drives a flood of concurrent reads at a single hot `GET /api/products/{id}` to
demonstrate Redis serving warm hits at high concurrency without touching the
PostgreSQL connection pool.

## Tooling: Bombardier 1.2.6

[Bombardier](https://github.com/codesenberg/bombardier) is a single static Go
binary (fasthttp client) that saturates an endpoint with thousands of
keep-alive connections from one host. We pin **v1.2.6** — the latest stable
release, zero runtime deps, runs on this project's Linux/WSL2 x86_64 host.

```bash
./bench/install-bombardier.sh     # downloads bench/bin/bombardier (no sudo)
```

## Run it

```bash
./run.sh                          # 1) start infra + app (separate terminal)
./bench/run-benchmark.sh          # 2) prime the cache, then spike it
```

The script auto-derives the port from `.env` (`SERVER_PORT`), auto-discovers a
real product id via `/api/products/trending`, prints the cold-vs-warm
single-request latency, then fires the spike.

### Knobs (env vars)

| Var          | Default     | Meaning                                           |
|--------------|-------------|---------------------------------------------------|
| `CONNS`      | `1000`      | concurrent connections — the simultaneous spike   |
| `REQUESTS`   | `1000000`   | total requests (when `DURATION` is empty)         |
| `DURATION`   | _(unset)_   | e.g. `20s` — run time-based instead of count-based |
| `TIMEOUT`    | `10s`       | per-request timeout                               |
| `PRODUCT_ID` | auto        | force a specific id                               |
| `BASE_URL`   | from `.env` | override `http://host:port`                       |

```bash
# 2,000 connections for 30 seconds against a chosen id:
CONNS=2000 DURATION=30s PRODUCT_ID=42 ./bench/run-benchmark.sh
```

## What "Redis wins" looks like

* **Throughput**: tens to hundreds of thousands of req/s on the warm path.
* **Latency**: p99 well under 10 ms — the request never leaves Redis.
* **One DB query, total**: with `spring.jpa.show-sql` on, the app log shows the
  `select ... from products` **once** (the cold prime) and never again during
  the spike.

### Proving the connection pool stays idle

While the spike runs, watch active DB connections from a separate shell:

```bash
# replace creds/db with your .env values; container name from docker-compose
docker exec -it <postgres-container> \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "select count(*) from pg_stat_activity where state='active';"
```

Active connections should hover at ~0–1 during the warm spike, because warm
reads are answered by Redis and never borrow from HikariCP.

## The cold-storm angle

The dangerous case is a *cold* spike: thousands of simultaneous requests for an
id that is **not** cached. The service guards this with `@Cacheable(sync = true)`
in `ProductServiceImpl` — Spring Data Redis holds a per-key lock so exactly one
thread loads from PostgreSQL while the rest block briefly and then read the
freshly-cached value. Without `sync`, all of them would stampede the pool at
once (see the connection-pool notes below).
