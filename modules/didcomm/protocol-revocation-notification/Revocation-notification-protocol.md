# Revocation notification protocol

This Protocol for an Issuer to notify the revocation of a credential to the holder.



## PIURI

Version 1.0: <https://atalaprism.io/revocation_notification/1.0/revoke>

### Roles

- Issuer
  - Will create the message and send it to the holder via previously established connection
- Holder
  - Will process the message as they see fit, protocol does not require any actions from the holder


### Revocation notification DIDcomV2 message as JSON

```json

{
  "from": "fromDID_value",
  "to": "toDID_value",
  "piuri":"https://atalaprism.io/revocation_notification/1.0/revoke",
  "body": {
    "issueCredentialProtocolThreadId": "issueCredentialProtocolThreadId_value",
    "comment": "Thread Id used to issue this credential withing issue credential protocol"
  }
}

```
