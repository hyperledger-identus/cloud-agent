#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# Enable prism-node VDR and run the full suite (basic prism-node config).
export TESTS_CONFIG=/configs/basic.conf
export VDR_MEMORY_DRIVER_ENABLED=true
export VDR_DATABASE_DRIVER_ENABLED=true
export VDR_PRISM_NODE_DRIVER_ENABLED=true
export AGENT_VERSION=2.1.1-SNAPSHOT
export PRISM_NODE_VERSION=${PRISM_NODE_VERSION:-edge}

# Optional: bump versions if needed
./gradlew clean
./gradlew test
