# Issue Credential Protocol

This Protocol is part of the **DIDComm Messaging Specification** but also **0453-issue-credential-v2**

Its a Issue Credential protocol based on DIDCOMMv2 message format.

A standard protocol for issuing credentials. This is the basis of interoperability between Issuers and Holders.

- See [https://identity.foundation/didcomm-messaging/spec]
- See [https://github.com/hyperledger/aries-rfcs/tree/main/features/0453-issue-credential-v2]

Others: 
- See [https://didcomm.org/issue-credential/3.0]
- See [https://github.com/decentralized-identity/waci-didcomm/tree/main/issue_credential]

## PIURI

Version 1.0: `https://didcomm.org/issue-credential/1.0/propose-credential`


### Roles

- Issuer
  - Begin with a offer credential
- Holder
  - Begin with a proposal credential
  - Begin with a request credential

### Issuer received credential proposal (Flow Diagram)

```mermaid
stateDiagram-v2
  [*] --> Initial
  Initial --> proposal_received:credential proposal received as DIDCOMMV2 message
  proposal_received --> offer_sent:credential-offer-sent as DIDCOMMV2 message
  proposal_received --> error:send problem report response
  offer_sent --> [*]:follow credential sent offer flow
```

### Issuer Send offer credential  (Flow Diagram)

```mermaid
stateDiagram-v2
  [*] --> Initial
  Initial --> await_response:credential-offer-sent DIDCOMMV2 message
  await_response --> request_received:received DIDCOMMV2 credential request
  request_received --> credential_issued:send DIDCOMMV2 issue credential message
  credential_issued --> done
  await_response --> error:recieve problem report response
  done --> [*]
```

### Holder proposal credential (Flow Diagram)

```mermaid
stateDiagram-v2
  [*] --> Initial
  Initial --> await_response:credential-proposal-sent DIDCOMMV2 message
  await_response --> offer_received:recieved credential offer DIDCOMMV2 message
  offer_received --> [*]:follow credential request flow for Holder
  await_response --> error:recieve problem report response
```

### Holder request credential (Flow Diagram)

```mermaid
stateDiagram-v2
  [*] --> Initial
  Initial --> offer_received:recieved credential offer DIDCOMMV2 message
  offer_received --> request_sent: send DIDCOMMV2 credential request
  request_sent --> credential_received:received DIDCOMMV2 issue credentia message
  credential_received --> done
  offer_received --> error:send problem report response
  done --> [*]
```

### Issuer State Machine

TODO See <https://github.com/hyperledger/aries-rfcs/blob/main/features/0453-issue-credential-v2/README.md>

### Holder State Machine

```mermaid
stateDiagram-v2
  state fork_state <<fork>>
  state if_state_AcceptOffer <<choice>>
  state if_state_Retry <<choice>>

  [*] --> fork_state
  fork_state --> Holder_Proposal
  fork_state --> Issuer_Offer
  fork_state --> Request_Credential


  Holder_Proposal -->RequestCredential
  state Holder_Proposal {
    [*] --> Propose_Credential: Send Propose
    if_state_Retry --> Propose_Credential: Yes
    Propose_Credential --> Recive_Problem_report: Receive Problem Report
    Recive_Problem_report --> [*]
    Propose_Credential --> Issuer_Offer : Recive Offer
    Issuer_Offer --> [*]
  }

  state Issuer_Offer {
    [*] --> Receive_Offer_Credential
    Receive_Offer_Credential --> if_state_AcceptOffer: Accept Offer?
    if_state_AcceptOffer --> Request_Credential: Yes
    if_state_AcceptOffer --> if_state_Retry: No
    if_state_Retry --> Send_Problem_report: No
    Send_Problem_report --> [*]
    Request_Credential --> [*]
  }

  state Request_Credential {
    [*] --> RequestCredential
    RequestCredential --> Receive_issue_credential_&_Store: Send Request Credential
    Receive_issue_credential_&_Store --> Send_Credential_ack: Get Credential
    Send_Credential_ack --> [*]: Ack
  }
```
