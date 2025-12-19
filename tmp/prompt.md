## Objective

Create an implementation of method `scheduleOperation` of the `NeoPrismDIDService`
which submit SignedPrismOperation to the endpoint `/api/signed-operation-submissions`.

The response of the neoprism endpoint will return the transaction id.
Transaction ID is a hex string representing the 32-bytes.
This can be considered an `operationId`.

## Constraints

- Testing and documentation update are out of scope.

## Resources

- For the endpoint of neprism openapi, you can find it at `./tmp/neoprism-openapi.yml`
