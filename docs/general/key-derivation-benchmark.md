# Key derivation benchmark

This document provides a performance benchmark of a key derivation used inside the Cloud Agent
in comparison with a key retrieval from HashiCorp Vault. It should provide a baseline for
future decisions in managing the key material on the agent.

## Test setup

### Environment

__System information__

- Platform: Linux (6.3.7)
- CPU: AMD Ryzen 7 PRO 6850U
- Memory: 30856MiB
- JDK version: OpenJDK 11.0.18
- SBT version: 1.8.0

__JVM options__

- Xmx:4G

The tests can be run by running the `org.hyperledger.identus.agent.walletapi.benchmark.KeyDerivation`.
The tests are being ignored to avoid running them on CI. When running locally,
the ignore aspect should be removed and the test can be run by

```bash
sbt agentWalletAPI/'testOnly -- -tag benchmark'
```

## Scenario

### Key Derivation

__Setup__

1. Warm-up JVM by running key derivation for 10k iterations
2. Running key derivation for 50k iterations with `N` parallelism
3. Measure the average, maximum and percentile of execution time (p50, p90, p99).
   The measurements consider derivation execution time of a single key.

__Results__

Duration in *microseconds*. Lower is better.

| Parallelism | Avg     | P50    | P90     | P99      | Max      |
|-------------|---------|--------|---------|----------|----------|
| 1           | 406.97  | 402.91 | 418.56  | 437.83   | 4550.13  |
| 8           | 499.59  | 475.20 | 544.83  | 826.22   | 2928.68  |
| 16          | 877.71  | 831.53 | 931.34  | 2278.10  | 10306.08 |
| 32          | 1772.57 | 821.06 | 1327.90 | 20331.60 | 61460.31 |

### Key retrieval from Vault

__Setup__

1. Warm-up JVM, Vault and their connections by setting/getting 100 keys
2. Running querying the KV from Vault for 50k iterations with `N` parallelism
3. Measure the average, maximum and percentile of execution duration (p50, p90, p99).
   The measurements consider the query time and serialization time of a single key.

Note: Vault server runs in a docker container on the same machine using in-memory storage.
So the setup may yield optimistic results.

__Results__

Duration in *microseconds*. Lower is better.

| Parallelism | Avg     | P50     | P90     | P99     | Max      |
|-------------|---------|---------|---------|---------|----------|
| 1           | 575.62  | 535.61  | 703.44  | 798.64  | 11388.56 |
| 8           | 717.67  | 666.64  | 913.04  | 1424.77 | 10854.26 |
| 16          | 1193.11 | 1098.47 | 1690.94 | 2818.88 | 11410.41 |
| 32          | 2379.26 | 2146.38 | 3954.72 | 6376.56 | 22210.13 |
