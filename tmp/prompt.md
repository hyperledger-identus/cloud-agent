## Objective

Implement a fully functional `getScheduledDIDOperationDetail` method for NeoPRismDIDService.
Current, we return a statuc result of status pending to the caller.
However, you should check the neoprism endpoint `/api/transactions/{tx_id}` to the the transaction detail.

For this task, you only need to check the status code.
If the status code is 200, it means that the transaction is confirm and you can return status "Confirmed".
Otherwise, you must return the status "Pending".

## Constraints

- Testing and documentation update are out of scope.

## Resources

- For the endpoint of neprism openapi, you can find it at `./tmp/neoprism-openapi.yml`
