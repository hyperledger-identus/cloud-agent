# Delete the credential schema

Unfortunately, after publishing (especially in the [Verifiable Data Registry (VDR)](/home/concepts/glossary#verifiable-data-registry), deleting the credential schema is impossible.

The Identus Platform doesn't publish the credential schema in the VDR. This capability will get implemented in the later version of the platform. That's why the platform does not expose the REST API for deletion.

If you need to `delete` the credential schema, you can ask the database administrator or delete it from the
Postgres instance by `guid`.

For example:

```sql
DELETE
FROM credential_schema
WHERE guid = '3f86a73f-5b78-39c7-af77-0c16123fa9c2'
```
