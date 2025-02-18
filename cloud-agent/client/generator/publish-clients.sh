#!/bin/bash
set -e

AGENT_VERSION=${VERSION_TAG:13}
echo version=${AGENT_VERSION}

# install dependencies
yarn -s

# Determine if the version is a snapshot or a release
if [[ "$AGENT_VERSION" == *-* ]]; then
  echo "Publishing snapshot version"

  # kotlin
  gradle -p ../kotlin -Pversion=${AGENT_VERSION}-SNAPSHOT publish --info

  # typescript
  yarn --cwd ../typescript -s
  yarn --cwd ../typescript publish --new-version ${AGENT_VERSION} --no-git-tag-version --non-interactive --tag snapshot --verbose
else
  echo "Publishing release version"

  # kotlin
  gradle -p ../kotlin -Pversion=${AGENT_VERSION} publish closeAndReleaseSonatypeStagingRepository --info

  # typescript
  yarn --cwd ../typescript -s
  yarn --cwd ../typescript publish --new-version ${AGENT_VERSION} --no-git-tag-version --non-interactive --tag latest --verbose
fi
