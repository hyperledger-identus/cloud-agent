#!/usr/bin/env bash

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)

# Set working directory
cd "${SCRIPT_DIR}" || exit

AGENT_VERSION=$(cd ../../ && sbt "project agent" -Dsbt.supershell=false -error "print version")
export AGENT_VERSION
echo "prism-agent server version: ${AGENT_VERSION}"

export PRISM_NODE_VERSION=v2.1.1
echo "prism node version: ${PRISM_NODE_VERSION}"
