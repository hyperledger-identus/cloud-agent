#!/bin/bash
set -e

# Parse the version from the revision, beta or tag (everything that comes after 'v' in the VERSION_TAG)
AGENT_VERSION=$(echo "$VERSION_TAG" | sed -E 's/.*v([0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9\.]+)?).*/\1/')
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
