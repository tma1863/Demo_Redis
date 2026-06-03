#!/usr/bin/env bash
#
# Installs the Bombardier HTTP load tester locally — no sudo, no Go toolchain.
#
# Why Bombardier, and why this version:
#   * Bombardier is a single, statically-linked Go binary (fasthttp client) that
#     comfortably drives thousands of concurrent connections from one box — ideal
#     for Battleground 3's "1,000 simultaneous reads of one hot id".
#   * Pinned to v1.2.6, the latest stable release. There is no newer GA, and the
#     binary has zero runtime dependencies, so it Just Works on this project's
#     WSL2 / Linux x86_64 host (and on macOS / arm64 too).
#
# The binary lands in bench/bin/bombardier so it never pollutes the system and
# run-benchmark.sh finds it automatically.
set -euo pipefail

VERSION="1.2.6"
DEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/bin"
BIN="$DEST_DIR/bombardier"

os="$(uname -s)"
arch="$(uname -m)"
case "$os" in
  Linux)  rel_os="linux" ;;
  Darwin) rel_os="darwin" ;;
  *) echo "Unsupported OS '$os'. Grab a binary from https://github.com/codesenberg/bombardier/releases" >&2; exit 1 ;;
esac
case "$arch" in
  x86_64|amd64)  rel_arch="amd64" ;;
  aarch64|arm64) rel_arch="arm64" ;;
  *) echo "Unsupported arch '$arch'." >&2; exit 1 ;;
esac

asset="bombardier-${rel_os}-${rel_arch}"
url="https://github.com/codesenberg/bombardier/releases/download/v${VERSION}/${asset}"

mkdir -p "$DEST_DIR"
echo "Downloading ${asset} (v${VERSION})"
echo "  from: $url"
echo "  to:   $BIN"
if command -v curl >/dev/null 2>&1; then
  curl -fL --retry 3 -o "$BIN" "$url"
elif command -v wget >/dev/null 2>&1; then
  wget -O "$BIN" "$url"
else
  echo "Need curl or wget on PATH to download." >&2; exit 1
fi

chmod +x "$BIN"
printf 'Installed: '
"$BIN" --version
echo "Done. Now run:  ./bench/run-benchmark.sh"
