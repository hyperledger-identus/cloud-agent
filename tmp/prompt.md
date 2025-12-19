## Objective

Implement the `resolveDID` method of the NeoPrismDIDService.
To do this we must call 2 endpoints from NeoPRISM.

- GET `/api/dids/{didRef}`
- GET `/api/did-data/`

The result from `/api/dids/{didRef}` returns the DID resolution result which we will use the resolution metadata.
If the status code from this endpoint is 404, we return `None`.

The result from `/api/did-data/` returns the serialized DIDData protobuf bytes as hex string.

## Constraints

- Testing are out of scope
- At the end of each steps, verify the build with `sbt scalafmtAll compile`

## Resources

- The openapi specification of NeoPRISM can be found in the `./tmp/openap.yml`.

