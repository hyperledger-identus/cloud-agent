# Delete the Credential Definition

Unfortunately, once published (especially in the [Verifiable Data Registry (VDR)](/home/concepts/glossary#verifiable-data-registry), deleting the credential definition becomes unfeasible.

In the Identus Platform, credential definitions aren't published to the VDR. This functionality will be incorporated in subsequent versions of the platform. Hence, the platform currently doesn't provide a REST API for deletion.

If you need to `delete` the credential definition, it's advisable to contact the database administrator or directly remove it from the Postgres instance using its `guid`.

For example:

```sql
DELETE
FROM credential_definition
WHERE guid = '3f86a73f-5b78-39c7-af77-0c16123fa9c2'
