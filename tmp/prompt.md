## Objective

Now we have added the support for having multiple Node implementation.

- PrismNode (via `PrismNodeDIDService`)
- NeoPRISM (via `NeoPrismDIDService`)

Currently, we have hardcoded the cloud agent to use neoprism via dependency injection in `MainApp.scala`.
We want the user to be able to choose between these node implementation.

Please design the configuration to let user choose the implementation.
Also expose the configuration for `NeoPrismDIDService` which is currently hardcoded the host and ports.

__Draft design__

The new configuration in the `application.conf` might look something like

```hocon
didNode {
  didBackend = "prism-node"

  prismNode { ... }

  neoprism { ... }
}
```

## Constraints

- When designing the configuration, please keep in mind that the user might have PrismNode running already, we want to keep backward compatible configuration for people already using PrismNode.

