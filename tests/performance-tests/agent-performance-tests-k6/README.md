# Prerequisites

- [k6](https://k6.io/docs/getting-started/installation)
- [NodeJS](https://nodejs.org/en/download/)
- [Yarn](https://yarnpkg.com/getting-started/install) (optional)

# Installation

**Install dependencies**

Clone the generated repository on your local machine, move to the project root folder and install the dependencies defined in [`package.json`](./package.json)

*NOTE*: The Project has a dependency on `hyperledger/identus-cloud-agent-client` which is the GitHub packages repository.
To install this dependency, you need to have an environment variable `GITHUB_TOKEN` with the scope `read:packages` set, you can install the dependency by running the following command:

```bash
yarn install
```

# Running the test

To run a test written in TypeScript, we first have to transpile the TypeScript code into JavaScript and bundle the project

```bash
yarn webpack
```

This command creates the final test files to the `./dist` folder.

Once that is done, we can run our script the same way we usually do, for instance:

```bash
k6 run dist/connection-flow-test.js
```

## Debugging Tests

k6 can be configured to log the HTTP request and responses that it makes during test execution. This is useful to debug errors that happen in tests when logs or k6 output does not contain the reason for a failure.

For example, if many requests result in 503s due to HTTP timeouts, there aren't many logs available to show when and why this happened.

To enable k6 to output requests and responses - add the `--http-debug` flag to the k6 test execution command

For example: `k6 run -e SCENARIO_LABEL=create-prism-did-smoke dist/create-prism-did-test.js -o experimental-prometheus-rw --http-debug`

By default, k6 does not output the body of the request/response - only the headers.

Add the flag `--http-debug="full"` to include the body of both request/response.

For example: `k6 run -e SCENARIO_LABEL=create-prism-did-smoke dist/create-prism-did-test.js -o experimental-prometheus-rw --http-debug=full`
