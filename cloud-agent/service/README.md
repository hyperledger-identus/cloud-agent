# CloudAgent service

## Quickstart

### Running CloudAgent service locally

see `./infrastucture/local/README.md` for instruction

---

## DID key management

`cloud-agent` is a cloud agent that represents the digital identity (is a DID controller)
of the Issuing / Verification organization. As a DID controller, it needs to perform
the operation with private and public keys through the Wallet API abstraction level.
The interface for key-management is heavily inspired by
[indy-sdk-java-wrapper](https://github.com/hyperledger/indy-sdk/tree/main/wrappers/java).

There is an `wallet-api` subproject which is responsible for managing and storing DID key-pairs.
The main goal is to wrap Castor and Pollux libraries which does not handle private-keys
and ease the usage by providing key-management capabilities.
Similar to [Indy Wallet SDK - secret API](https://github.com/hyperledger/indy-sdk/tree/main/docs/design/003-wallet-storage#secrets-api),
*it does not expose a private-key* for external use, instead it provide functions to perform cryptographic actions using internally stored private-keys.

---

## Connect flow

Basic documentation on how to execute the Connect flow from command line can be found [here](./connect.md).

---

## Issue flow

Basic documentation on how to execute the Issue flow from the command line can be found [here](./issue.md).

---

## Present Proof flow

Basic documentation on how to execute the Present Proof flow from the command line can be found [here](./present-proof.md).

---

## Known limitations

---
