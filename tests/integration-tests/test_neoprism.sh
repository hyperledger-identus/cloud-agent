#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# Use NeoPrism config but enable memory and database VDR drivers; exclude prism-node scenarios.
export TESTS_CONFIG=/configs/basic_neoprism.conf
export VDR_MEMORY_DRIVER_ENABLED=true
export VDR_DATABASE_DRIVER_ENABLED=true
export VDR_PRISM_NODE_DRIVER_ENABLED=false
export AGENT_VERSION=2.1.1-SNAPSHOT
export PRISM_NODE_VERSION=${PRISM_NODE_VERSION:-2.6.1-SNAPSHOT}
export NEOPRISM_VERSION=${NEOPRISM_VERSION:-0.10.1}

# Run all but prism-node and memory/db-specific VDR scenarios (NeoPrism driver)
./gradlew clean
./gradlew test -Dcucumber.filter.tags='not @vdr_prism_node'
