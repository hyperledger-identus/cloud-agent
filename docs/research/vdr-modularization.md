# VDR modularization plan (cloud-agent)

## Goal
Decouple VDR functionality into focused modules that can be reused by prism-node, neoprism, and future backends while keeping CI/build times manageable.

## Phase 1 (foundation)
- Add `vdr-core` subproject with shared types:
  - `VdrService`, `VdrServiceError`, `VdrOperationSigner`
  - `PrismVdrLogic` helpers
  - `PrismNodeClient` abstraction
- Wire `cloud-agent-vdr` to depend on `vdr-core`.
- Keep existing backends in place; only move shared files.

## Phase 2 (backends)
- Create `vdr-memory` for `InMemoryDriver`.
- Create `vdr-database` for `DatabaseDriver` (Postgres/Testcontainers ITs).
- Create `vdr-prism-node` for gRPC backend (uses `PrismVdrLogic`).
- Create `vdr-neoprism` mirroring prism-node.
- Update `cloud-agent-vdr` assembly to select backend(s) via config.

## Phase 3 (HTTP surface)
- Extract VDR HTTP controller/routes into `vdr-http`.
- Route tests stubbed against `vdr-core` services; optional ITs with selected backend.

## Phase 4 (cleanup/CI)
- Move remaining VDR sources into appropriate modules.
- Tag heavy Testcontainers suites; keep unit tests fast.
- Ensure coverage aggregate spans all VDR modules.

## Notes / constraints
- Keep dependency graph acyclic: backends depend on `vdr-core`; `vdr-http` depends on `vdr-core` + chosen backends.
- Avoid leaking generated protobufs into coverage (already excluded).
- Keep existing configs working during migration; only flip wiring after each phase passes tests.
