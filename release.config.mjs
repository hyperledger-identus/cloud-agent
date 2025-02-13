export default {
    branches: [
        'main',
        '+([0-9])?(.{+([0-9]),x}).x',
        { name: 'beta', prerelease: true }
    ],
    plugins: [
        ['@semantic-release/commit-analyzer', {
            "preset": "conventionalcommits"
        }],
        ["@semantic-release/exec", {
            "prepareCmd": "echo ${nextRelease.version} > .release-version"
        }],
        '@semantic-release/release-notes-generator',
        ["@semantic-release/changelog", {
            "changelogFile": "CHANGELOG.md"
        }],
        ["@semantic-release/exec", {
            "prepareCmd": "sbt \"release release-version ${nextRelease.version} with-defaults\""
        }],
        ["@semantic-release/exec", {
            "prepareCmd": "npm version ${nextRelease.version} --git-tag-version false"
        }],
        ["@semantic-release/exec", {
            "prepareCmd": 'sbt "set ThisBuild / version:=\\\"${nextRelease.version}\\\"" "dumpLicenseReportAggregate" && cp ./target/license-reports/root-licenses.md ./DEPENDENCIES.md'
        }],
        ["@semantic-release/exec", {
            "prepareCmd": "docker buildx build --platform=linux/arm64,linux/amd64 --push -t ${DOCKERHUB_ORG}/identus-cloud-agent:${nextRelease.version} ./cloud-agent/service/server/target/docker/stage"
        }],
        ["@semantic-release/exec", {
            "prepareCmd": "sed -i.bak \"s/AGENT_VERSION=.*/AGENT_VERSION=${nextRelease.version}/\" ./infrastructure/local/.env && rm -f ./infrastructure/local/.env.bak"
        }],
        ["@semantic-release/exec", {
            "prepareCmd": "sbt \"cloudAgentServer/test:runMain org.hyperledger.identus.api.util.Tapir2StaticOAS ${process.env.PWD}/cloud-agent/service/api/http/cloud-agent-openapi-spec.yaml ${nextRelease.version}\""
        }],
        ["@semantic-release/git", {
            "assets": [
                "version.sbt",
                "CHANGELOG.md",
                "DEPENDENCIES.md",
                "package.json",
                "package-lock.json",
                "cloud-agent/service/api/http/cloud-agent-openapi-spec.yaml",
                "infrastructure/local/.env"
            ],
            "message": "chore(release): cut the Identus Cloud agent ${nextRelease.version} release\n\n${nextRelease.notes} [skip ci]\n\nSigned-off-by: Hyperledger Bot <hyperledger-bot@hyperledger.org>"
        }],
        ["semantic-release-slack-bot", {
            "notifyOnSuccess": true,
            "notifyOnFail": true,
            "markdownReleaseNotes": true,
            "onSuccessTemplate": {
                "text": "A new version of Identus Cloud Agent successfully released!\nVersion: `$npm_package_version`\nTag: $repo_url/releases/tag/cloud-agent-v$npm_package_version\n\nRelease notes:\n$release_notes"
            }
        }],
    ],
    tagFormat: "v${version}"
}
