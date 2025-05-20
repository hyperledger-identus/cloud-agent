#!/bin/bash

set -e

# Variables
ENV_FILE=".env"
PERF_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
AGENT_DIR="$PERF_DIR/../../.."
DOCKERFILE="$AGENT_DIR/infrastructure/shared/docker-compose-combined.yml"

K6_BASE_URL="https://github.com/grafana/k6/releases/download/v0.45.0/k6-v0.45.0-"
K6_LINUX_ZIP_FILE="linux-amd64.tar.gz"
K6_MACOS_INTEL_ZIP_FILE="macos-amd64.zip"
K6_MACOS_ARM_ZIP_FILE="macos-arm64.zip"
K6_WINDOWS_ZIP_FILE="windows-amd64.zip"

# Functions
function startAllAgents() {
	echo "Starting agents"

	local DOCKERHOST="host.docker.internal"
	local NODE_REFRESH_AND_SUBMIT_PERIOD="1s"
	local NODE_MOVE_SCHEDULED_TO_PENDING_PERIOD="1s"
	local NODE_WALLET_MAX_TPS="1000"
	local ADMIN_TOKEN=admin
	local DEFAULT_WALLET_ENABLED=true
	local DEFAULT_WALLET_AUTH_API_KEY=default
	local API_KEY_AUTO_PROVISIONING=false
	local API_KEY_ENABLED=true

	echo "Issuer Port   [$ISSUER_PORT]"
	echo "Holder Port   [$HOLDER_PORT]"
	echo "Verifier Port [$VERIFIER_PORT]"

	ADMIN_TOKEN="${ADMIN_TOKEN}" \
		DEFAULT_WALLET_ENABLED="${DEFAULT_WALLET_ENABLED}" \
		DEFAULT_WALLET_AUTH_API_KEY="${DEFAULT_WALLET_AUTH_API_KEY}" \
		API_KEY_AUTO_PROVISIONING="${API_KEY_AUTO_PROVISIONING}" \
		API_KEY_ENABLED="${API_KEY_ENABLED}" \
		DOCKERHOST="${DOCKERHOST}" \
		PG_PORT="${PG_PORT}" \
		NODE_REFRESH_AND_SUBMIT_PERIOD="${NODE_REFRESH_AND_SUBMIT_PERIOD}" \
		NODE_MOVE_SCHEDULED_TO_PENDING_PERIOD="${NODE_MOVE_SCHEDULED_TO_PENDING_PERIOD}" \
		NODE_WALLET_MAX_TPS="${NODE_WALLET_MAX_TPS}" \
		docker compose -f "${DOCKERFILE}" \
		--env-file "${ENV_FILE}" up -d --wait 2>/dev/null || {
		  echo "Docker failed to start? Please check whether Docker daemon is running" >&2
		  exit 1
		}
	echo "starting containers in docker"
	echo "Agents healthy"
}

function stopAllAgents() {
	echo "Stopping  agents"
	docker compose \
		-f "${DOCKERFILE}" \
		--env-file "${ENV_FILE}" down -v 2>/dev/null
	echo "Agents  stopped"
}

# clean up on finish
function cleanup() {
	local exit_code=$?
	if [[ $exit_code -eq 0 ]]; then
		echo "Script exited normally with code $exit_code."
	else
		echo "Script exited with error code $exit_code."
	fi
	echo "Removing K6 binaries"
	rm k6
	rm "$K6_ZIP_FILE"
	echo "Stopping All Agents"
	stopAllAgents
	echo "cleanup complete"
}

function check_os_name_and_arch(){

  if [ "$(uname -s)" = "Darwin" ]; then
      ARCH=$(uname -m)
      if [ "$ARCH" = "x86_64" ]; then
          echo "MacOS $ARCH"
      elif [ "$ARCH" = "arm64" ]; then
          echo "MacOS $ARCH"
      fi
  elif [ "$(uname -s)" = "Linux" ]; then
      ARCH=$(uname -m)
      echo "Linux $ARCH"
  else
      if command -v cmd.exe &>/dev/null ||
         command -v powershell.exe &>/dev/null ||
         command -v systeminfo &>/dev/null; then
         echo "WINDOWS"
      else
         echo "Unknown platform"
      fi
  fi

}

trap 'cleanup' EXIT

# download and unzip k6
echo "Downloading K6"
FULL_ARCH=$(check_os_name_and_arch)

case "$FULL_ARCH" in
    "MacOS x86_64")
          FULL_URL="${K6_BASE_URL}${K6_MACOS_INTEL_ZIP_FILE}"
          ZIP_FILE=$(basename ${FULL_URL})
          echo "Downloading for MacOS x86_64"
          echo "Downloading from url:${FULL_URL}"
          curl -LO -s "${FULL_URL}"
          unzip -j "${ZIP_FILE}" >/dev/null
        ;;
    "MacOS arm64")
          FULL_URL="${K6_BASE_URL}${K6_MACOS_ARM_ZIP_FILE}"
          ZIP_FILE=$(basename ${FULL_URL})
          echo "Downloading for MacOS arm64"
          echo "Downloading from url:${FULL_URL}"
          curl -LO -s "${FULL_URL}"
          unzip -j "${ZIP_FILE}" >/dev/null
        ;;
    "Linux x86_64")
          FULL_URL="${K6_BASE_URL}${K6_LINUX_ZIP_FILE}"
          ZIP_FILE=$(basename ${FULL_URL})
          echo "Downloading for Arch Linux x86_64"
          echo "Downloading from url: ${FULL_URL}"
          curl -LO -s "${FULL_URL}"
          echo "${ZIP_FILE}"
          tar -xzvf "${ZIP_FILE}" --strip-components=1 -C ./ --wildcards '*/k6' 2>/dev/null
        ;;
    "WINDOWS")
          FULL_URL="${K6_BASE_URL}${K6_WINDOWS_ZIP_FILE}"
          ZIP_FILE=$(basename ${FULL_URL})
          echo "Downloading for Windows"
          echo "Downloading from url: ${FULL_URL}"
          curl -LO -s "${FULL_URL}"
          tar -xf %ZIP_FILE% --strip-components=1 -C . --wildcards "*/k6.exe"
        ;;
    *)
          echo "Unrecognized system: $FULL_ARCH, go to the k6 release page and download the respective binary https://github.com/grafana/k6/releases"
          exit 1
        ;;
esac

echo "K6 downloaded"

## navigate to main project
cd "$AGENT_DIR"

##sbt docker:publishLocal
AGENT_VERSION=$(cut -d '"' -f 2 version.sbt)

## back to performance folder
cd "$PERF_DIR"

# set version to env file
sed -i.bak "s/AGENT_VERSION=.*/AGENT_VERSION=${AGENT_VERSION}/" "${ENV_FILE}" && rm -f "${ENV_FILE}.bak"

# create docker  agent-network
# create Prism Node
export HOLDER_PORT=8300
export ISSUER_PORT=8080
export VERIFIER_PORT=8100
export ISSUER_AGENT_API_KEY=default
export HOLDER_AGENT_API_KEY=default
export VERIFIER_AGENT_API_KEY=default
export ISSUER_AGENT_URL="http://localhost:${ISSUER_PORT}/cloud-agent"
export HOLDER_AGENT_URL="http://localhost:${HOLDER_PORT}/cloud-agent"
export VERIFIER_AGENT_URL="http://localhost:${VERIFIER_PORT}/cloud-agent"

startAllAgents

# yarn install
echo "Installing dependencies"
yarn -s >/dev/null
echo "Building performance tests"
yarn webpack >/dev/null

# start perf test
echo "Starting performance testing"

./k6 run -e SCENARIO_LABEL=create-prism-did-smoke ./dist/create-prism-did-test.js
./k6 run -e SCENARIO_LABEL=credential-offer-smoke ./dist/credential-offer-test.js
./k6 run -e SCENARIO_LABEL=credential-definition-smoke ./dist/credential-definition-test.js
./k6 run -e SCENARIO_LABEL=credential-schema-smoke ./dist/credential-schema-test.js
./k6 run -e SCENARIO_LABEL=did-publishing-smoke ./dist/did-publishing-test.js
./k6 run -e SCENARIO_LABEL=connection-flow-smoke ./dist/connection-flow-test.js
./k6 run -e SCENARIO_LABEL=issuance-flow-smoke ./dist/issuance-flow-test.js
./k6 run -e SCENARIO_LABEL=present-proof-flow-smoke ./dist/present-proof-flow-test.js
