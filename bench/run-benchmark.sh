#!/usr/bin/env bash
#
# Battleground 3 — high-concurrency product-detail reads.
#
# Hammers GET /api/products/{id} for a SINGLE hot id and shows that warm reads
# are served from Redis at high concurrency with sub-10ms latency, while the
# PostgreSQL connection pool is hit exactly once (the cold miss).
#
# Prerequisites:
#   1. The app is running:        ./run.sh
#   2. Bombardier is installed:   ./bench/install-bombardier.sh
#
# Tunables (env vars, all optional):
#   CONNS=1000        concurrent connections — the simultaneous spike
#   REQUESTS=1000000  total requests (used when DURATION is empty)
#   DURATION=         e.g. 20s — run time-based instead of count-based
#   TIMEOUT=10s       per-request timeout
#   PRODUCT_ID=       force a specific id (otherwise auto-discovered)
#   BASE_URL=         override host:port (otherwise derived from .env SERVER_PORT)
#
# Example:  CONNS=2000 DURATION=30s ./bench/run-benchmark.sh
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."   # project root

# --- locate bombardier: project-local install first, then PATH ---------------
BOMBARDIER="./bench/bin/bombardier"
if [ ! -x "$BOMBARDIER" ]; then BOMBARDIER="$(command -v bombardier || true)"; fi
if [ -z "${BOMBARDIER:-}" ] || ! "$BOMBARDIER" --version >/dev/null 2>&1; then
  echo "Bombardier not found. Run ./bench/install-bombardier.sh first." >&2
  exit 1
fi

# --- resolve base URL from .env (same idiom as run.sh) -----------------------
if [ -f .env ]; then export $(grep -v '^#' .env | xargs); fi
PORT="${SERVER_PORT:-8080}"
BASE_URL="${BASE_URL:-http://localhost:${PORT}}"

CONNS="${CONNS:-1000}"
REQUESTS="${REQUESTS:-1000000}"
DURATION="${DURATION:-}"
TIMEOUT="${TIMEOUT:-10s}"

# --- pick a product id that actually exists ----------------------------------
if [ -z "${PRODUCT_ID:-}" ]; then
  echo "Discovering a real product id from /api/products/trending ..."
  json="$(curl -fsS "$BASE_URL/api/products/trending" || true)"
  if command -v python3 >/dev/null 2>&1; then
    PRODUCT_ID="$(printf '%s' "$json" \
      | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"][0]["productId"])' 2>/dev/null || true)"
  fi
  if [ -z "${PRODUCT_ID:-}" ]; then
    PRODUCT_ID="$(printf '%s' "$json" | grep -oE '"productId"[: ]*[0-9]+' | head -1 | grep -oE '[0-9]+' || true)"
  fi
fi
if [ -z "${PRODUCT_ID:-}" ]; then
  echo "Could not auto-discover a product id. Set PRODUCT_ID=<id> and retry." >&2
  exit 1
fi

URL="$BASE_URL/api/products/$PRODUCT_ID"
echo "Target: $URL"

# --- health check ------------------------------------------------------------
code="$(curl -s -o /dev/null -w '%{http_code}' "$URL")"
if [ "$code" != "200" ]; then
  echo "Endpoint returned HTTP $code — is the app up and the id valid?" >&2
  exit 1
fi

# --- cold vs warm single-shot latency (the cache-miss -> cache-hit story) ----
echo
echo "== Priming the cache (1st request = miss -> Postgres -> Redis) =="
cold="$(curl -s -o /dev/null -w '%{time_total}' "$URL")"
warm="$(curl -s -o /dev/null -w '%{time_total}' "$URL")"
printf "  cold (cache miss): %ss\n  warm (cache hit) : %ss\n" "$cold" "$warm"

# --- the spike: many concurrent reads of the SAME hot id ---------------------
echo
echo "== Spike: ${CONNS} concurrent connections on one hot id =="
ARGS=(-c "$CONNS" -t "$TIMEOUT" -l --print=intro,result)
if [ -n "$DURATION" ]; then
  ARGS+=(-d "$DURATION")
else
  ARGS+=(-n "$REQUESTS")
fi
echo "+ $BOMBARDIER ${ARGS[*]} $URL"
"$BOMBARDIER" "${ARGS[@]}" "$URL"

echo
echo "Tip: with spring.jpa.show-sql=on, the app log should show the product"
echo "     SELECT exactly ONCE (the cold prime) and never again during the"
echo "     spike — proof the warm reads stayed entirely in Redis."
