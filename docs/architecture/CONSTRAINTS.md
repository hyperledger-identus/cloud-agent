# Architectural Constraints

## Dependency Direction
The following dependency direction must be enforced to ensure a modular architecture:

1.  **API modules (`*-api`)** should be stable and have minimal dependencies.
2.  **Domain modules** should depend on API modules and stable core/shared modules.
3.  **Adapters** (e.g., persistence, external integrations) should depend on Domain or API modules.
4.  **Composition layer** (e.g., `api-server`) should depend on all other modules to wire them together.

## Forbidden Dependencies
- Domain modules must NOT depend on Adapters.
- Core/Shared modules must NOT depend on Domain modules.
- API modules must NOT depend on Domain or Adapter modules.

## Target Bounded Context Dependencies
The goal is to move towards the following dependency flow:
`api-server` -> `credentials`, `did`, `didcomm`, `connections`, `wallet-management`, `notifications` -> `core`, `crypto`, `vdr`
