#!/bin/bash
set -e

echo "Publishing ${VERSION_TAG}"

PUBLISH_KOTLIN="${PUBLISH_KOTLIN:-true}"
PUBLISH_TS="${PUBLISH_TS:-true}"

# install generator dependencies
yarn -s

# Determine if the version is a snapshot or a release
if [[ "$VERSION_TAG" == *-* ]]; then
  echo "Publishing snapshot version"
  if [[ "$PUBLISH_KOTLIN" == "true" ]]; then
    echo "Publishing Kotlin client snapshot"
    gradle -p ../kotlin -Pversion=${VERSION_TAG}-SNAPSHOT publishToMavenCentral
  fi

  if [[ "$PUBLISH_TS" == "true" ]]; then
    echo "Publishing TypeScript client snapshot"
    npm --prefix ../typescript install
    npm --prefix ../typescript version "${VERSION_TAG}" --no-git-tag-version
    npm --prefix ../typescript publish --provenance --access public --tag snapshot
  fi
else
  echo "Publishing release version"

  if [[ "$PUBLISH_KOTLIN" == "true" ]]; then
    echo "Publishing Kotlin client release"
    gradle -p ../kotlin -Pversion=${VERSION_TAG} publishToMavenCentral
  fi

  if [[ "$PUBLISH_TS" == "true" ]]; then
    echo "Publishing TypeScript client release"
    npm --prefix ../typescript install
    npm --prefix ../typescript version "${VERSION_TAG}" --no-git-tag-version
    npm --prefix ../typescript publish --provenance --access public --tag latest
  fi
fi
