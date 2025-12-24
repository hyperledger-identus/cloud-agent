## Objective

As we support neoprism in the DIDService, we also want to test the implementation in the integration-test stack.
The integraion tests are in the directory `./tests/integration-tests`.
We need to change the prism-node container to the neoprism container, but we don't want to completely replace the container as it still serve checks in the QA pipeline.
We want to add a new configuration that would trigger the cloud-agent to use neoprism as a node backend.

