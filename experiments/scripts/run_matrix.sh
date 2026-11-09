#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

java -jar target/mpi-virtual-threads-research-0.1.0.jar -a all -s all -t 256 -r 2 -m 1024
