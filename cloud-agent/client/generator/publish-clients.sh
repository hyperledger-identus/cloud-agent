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
	cd ../typescript
	echo "Publishing TypeScript client snapshot"
	# Ensure dependencies are installed for the package before publishing
	npm install
	npm version "${AGENT_VERSION}" --no-git-tag-version
	npm publish --provenance --tag snapshot
else
	echo "Publishing release version"

	# kotlin
	PACKAGE_URL="https://repo1.maven.org/maven2/org/hyperledger/identus/cloud-agent-client/${AGENT_VERSION}/cloud-agent-client-${AGENT_VERSION}.pom"
	HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$PACKAGE_URL")

	if [ "$HTTP_CODE" -eq 200 ]; then
		echo "Package version ${AGENT_VERSION} already exists. Skipping publication."
	else
		echo "Package version ${AGENT_VERSION} does not exist. Proceeding with publication."
		gradle -p ../kotlin -Pversion=${AGENT_VERSION} publish closeAndReleaseSonatypeStagingRepository --info
	fi

	# typescript
		cd ../typescript
  	echo "Publishing TypeScript client release"
  	# Ensure dependencies are installed for the package before publishing
  	npm install
  	npm version "${AGENT_VERSION}" --no-git-tag-version
  	npm publish --provenance --tag latest
fi
