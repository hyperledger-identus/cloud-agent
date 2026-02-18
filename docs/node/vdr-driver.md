# PRISM node VDR driver – current behaviour (Feb 2026)

This note captures how the PRISM node implements the VDR storage driver after the recent fixes.

## Entry identifiers and versions
- **entry_id** is the immutable identifier returned when a VDR entry is first created.  
- Each mutation (update / deactivate) produces a new **entry_hash**. The head table keeps the latest hash for a given `entry_id`.
- Clients must use the original `entry_id` in URLs; the node resolves it to the latest hash before applying a new operation.

## Operation rules
1. **Create**  
   - Stores entry and inserts a head record with state `ACTIVE` and the creation hash.
2. **Update**  
   - Node fetches the latest head for `entry_id`.  
   - Rejects the request if the provided `previous_hash` is stale.  
   - On success, stores the new entry row and updates the head to the new hash (state remains `ACTIVE`).
3. **Deactivate**  
   - Same head lookup as update; rejects stale hashes.  
   - Stores a deactivate event and marks the head `DEACTIVATED`.

## Resolution
- `ResolveEntry` returns the latest **entry_hash** data for an `entry_id`.  
- If the head is `DEACTIVATED`, the node returns `FAILED_PRECONDITION` (`vdr-entry-deactivated`) so API layers can translate this to HTTP 404.

## Error signalling
- **Stale hash** during update/deactivate ⇒ `INVALID_ARGUMENT` with message about previous hash mismatch.
- **Deactivated entry** ⇒ `FAILED_PRECONDITION` (domain error: entry deactivated).
- **Unknown entry_id** ⇒ `UNKNOWN` (unknown-value) today; consider mapping to NOT_FOUND in API layers.

## Logging
- VDR operations log at INFO with: entry_id, op type, previous hash, new hash, and resulting state. This replaces ad‑hoc `println`.

## Testing expectations
- Unit tests cover head updates on create/update/deactivate.  
- E2E tests assert that after deactivation the HTTP layer returns 404 while gRPC returns FAILED_PRECONDITION.

## Client guidance
- Always keep the original `entry_id` (URL) returned from the first create.  
- For mutations, fetch the latest head/hash first, then sign the operation with that hash.  
- Treat FAILED_PRECONDITION as “entry was deactivated”; do not retry with the same hash.

## VDR key type (cloud-agent)
- A new internal VDR key type is used for signing VDR operations.  
- Validation rules now reject unsupported combinations (e.g., SECp256k1 used for key agreement, non‑VDR internal purposes).  
- Templates that add VDR internal keys must use the dedicated `InternalKeyPurpose.VDR`; other purposes are rejected.

## Runtime parameters
- Prism-node image is parameterised: `PRISM_NODE_VERSION` (defaults to `edge` in e2e). Override to a released tag for stability.
- Docker API requirement: e2e and CI set `DOCKER_API_VERSION=1.44` to satisfy the GitHub-hosted Docker daemon. Set this env when running tests locally if you hit client/daemon version errors.
- CI runs two jobs in parallel:
  - **neoprism**: uses `basic_neoprism.conf`, memory/db VDR drivers enabled.
  - **prism-node**: uses `basic.conf`, prism-node driver enabled.
