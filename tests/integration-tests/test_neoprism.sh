#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# Use NeoPrism config with memory and database VDR drivers enabled.
export TESTS_CONFIG=/configs/basic_neoprism.conf
export VDR_MEMORY_DRIVER_ENABLED=true
export VDR_DATABASE_DRIVER_ENABLED=true
export VDR_PRISM_NODE_DRIVER_ENABLED=false
export VDR_LEDGER_DRIVER=neoprism
export AGENT_VERSION=2.1.1-SNAPSHOT
export PRISM_NODE_VERSION=${PRISM_NODE_VERSION:-edge}
export NEOPRISM_VERSION=${NEOPRISM_VERSION:-0.13.0}

./gradlew clean
./gradlew test
