# Present Proof Protocol

This Protocol is part of the **WACI-DIDComm Interop Profile v1.0**
- See [https://github.com/decentralized-identity/waci-didcomm/blob/main/present_proof/present-proof-v3.md]

Its a Present Proof protocol based on DIDCOMMv2 message format.

A protocol supporting a general purpose verifiable presentation exchange regardless of the specifics of the underlying verifiable presentation request and verifiable presentation format

More Info:
- See [https://didcomm.org/present-proof/3.0/]
- Its base on **DIDComm Messaging Specification** - [https://identity.foundation/didcomm-messaging/spec]
- Its similar to **00454-present-proof-v2** - [Hyperledger present-proof-v2](https://github.com/hyperledger/aries-rfcs/tree/main/features/0454-present-proof-v2) also see
[Hyperledger 0453-issue-credential-v2](https://github.com/hyperledger/aries-rfcs/blob/main/features/0453-issue-credential-v2/README.md)

## PIURI

Version 3.0:
- `https://didcomm.atalaprism.io/present-proof/3.0/propose-presentation`
- `https://didcomm.atalaprism.io/present-proof/3.0/request-presentation`
- `https://didcomm.atalaprism.io/present-proof/3.0/presentation`

Note: `https://didcomm.atalaprism.io/present-proof/3.0` is equivalent to `https://didcomm.org/present-proof/3.0` with the different of how to specific types of proofs.

### Roles

- Prover
  - Begin with a Propose Presentation Proof
- Verifier
  - Begin with a Request Presentation

### Prover received request presentation (Flow Diagram)

```mermaid
stateDiagram-v2
  [*] --> request_received: Presentation request received
  [*] --> proposal_sent: Send presentation Proposal
  proposal_sent --> request_received: Presentation request received
  proposal_sent --> abandoned: Send problem
  proposal_sent --> abandoned: Receive problem
  request_received --> proposal_sent: Send presentation Proposal
  request_received --> presentation_sent:Presentation proof sent
  request_received --> abandoned: Send problem
  request_received --> abandoned: Receive problem
  presentation_sent --> done
  presentation_sent --> abandoned: Receive problem
  abandoned --> [*]
  done --> [*]
```

### Verifier request presentation sent  (Flow Diagram)


```mermaid
stateDiagram-v2
  [*] --> request_sent: Send request presentation
  [*] --> proposal_received: Receive proposal presentation
  proposal_received --> request_sent: Resend request
  proposal_received --> abandoned: Send problem
  proposal_received --> abandoned: Receive problem
  request_sent --> request_sent: Resend request
  request_sent --> proposal_received: Receive proposal presentation
  request_sent --> presentation_received: Receive presentation
  request_sent --> done: Receive presentation
  request_sent --> abandoned: Receive problem
  presentation_received --> done: Send ACK
  presentation_received --> abandoned: Send problem
  presentation_received --> abandoned: Receive problem
  abandoned --> [*]
  done --> [*]
```

### Holder State Machine (TODO update this)

```mermaid
stateDiagram-v2
  state if_state_Proposal <<choice>>
  state if_state_Presentation <<choice>>
  state if_reveal_data <<choice>>
  state if_continue <<choice>>
  state if_ack_expected <<choice>>

  state Verifier_Request_Proof {

    [*] --> Send_Presentation_Request: Send Presentation Proof Request 
    Send_Presentation_Request --> Recive_Presentation_Proof : Receive Presentation Proof
    Send_Presentation_Request --> Recive_Presentation_Proposal : Receive Presentation Proposal
    Send_Presentation_Request --> verfier_problem_report: Receive Problem Report
    Recive_Presentation_Proposal --> if_state_Proposal : Is proposal valid
    if_state_Proposal --> Send_Presentation_Request: Yes
    if_state_Proposal --> verfier_problem_report: No, Sent problem report
    verfier_problem_report --> [*]
    
    Recive_Presentation_Proof --> Presentation_Received
    Presentation_Received --> if_state_Presentation : Is presentation valid
    if_state_Presentation --> Send_Ack_Presentation_Proof: Yes valid
    if_state_Presentation --> verfier_problem_report: Yes Invalid, sent problem report
    if_state_Presentation --> Ack_Expected?: No
    Ack_Expected? --> [*]
    Send_Ack_Presentation_Proof --> [*]
    verfier_problem_report --> [*]
  }
    
  state Prover_Proposal_Proof {     
   [*] --> Receive_Presentation_Request: Receive Presentation Proof Request
    Receive_Presentation_Request --> if_reveal_data:Do you want to reveal data? 
    if_reveal_data --> if_continue : No Do you want to continue?
    if_continue --> send_presentation_proposal :  Yes
    if_continue --> Problem_report : No,Sent problem report
    send_presentation_proposal --> Receive_Presentation_Request
    Problem_report --> [*]
    if_reveal_data --> send_presentation_proof : Yes
    send_presentation_proof --> if_ack_expected: sent
    if_ack_expected --> receive_ack_presentation: Yes Received Ack
    if_ack_expected --> Problem_report: Yes,Received problem report
    if_ack_expected --> [*] : No
    receive_ack_presentation --> [*]
  }


```
