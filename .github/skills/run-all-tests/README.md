# Run all tests before pushing

Checklist to run locally before pushing to origin:

- sbt reload; clean; scalafmtAll; compile
- sbt test (or at least module tests you touched)
- ./gradlew clean test (e2e gradle project)
- For VDR changes: ./tests/integration-tests/test_prism_node.sh and ./tests/integration-tests/test_neoprism.sh
- If docker/Testcontainers involved: ensure DOCKER_API_VERSION=1.44 and Docker daemon reachable

Push only after unit tests pass (fail early instead of in CI).
