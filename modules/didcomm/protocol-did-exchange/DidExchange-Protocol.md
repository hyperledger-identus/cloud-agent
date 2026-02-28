# DidExchange Protocol

This Protocol is part of the DIDComm Messaging Specification.

Protocol to exchange DIDs between agents when establishing a DID-based relationship

See [https://github.com/hyperledger/aries-rfcs/tree/main/features/0023-did-exchange]

## PIURI

`https://didcomm.org/didexchange/1.0/request`
`https://didcomm.org/didexchange/1.0/problem_report`
`https://didcomm.org/didexchange/1.0/response`
`https://didcomm.org/didexchange/1.0/complete`

### Roles

- Requester(Is the receiver in out-of-band protocol)
  - Will initiate the did-exchange 
- Responder (Is the invitation sender in out-of-band protocol)
  - Will respond to did exchange

  

### Requester request did-exchange
step1-->step2-->step3 Is a happy path flow

error - error received or sent and state transition

```mermaid
stateDiagram-v2
  [*] --> invitation_received:out-of-band Invitation
  invitation_received --> request_sent:step1-send did-exchange request
  invitation_received --> abandoned:eror1 unable send problem report
  request_sent --> response_received: step2-received did-exchange Response
  request_sent --> invitation_received: eror2 received problem report
  request_sent --> abandoned:eror2 unable continue after problem report
  response_received --> completed:step3-send complete
  response_received --> request_sent:eror3 send problem report
  response_received --> abandoned:eror3 unable send problem report
  abandoned --> [*]
  completed --> [*]
```


### Responder responds to did-exchange
step1-->step2-->step3 Is a happy path flow

error received or sent and state transition
```mermaid
stateDiagram-v2
  [*] --> invitation_sent:out-of-band send Invitation
  invitation_sent --> request_received:step1. received did-exchange request
  invitation_sent --> abandoned:eror1 unable send problem report
  request_received --> response_sent: step2. send did-exchange Response
  request_received --> abandoned:eror2 unable send problem report
  request_received --> invitation_sent: error2 send problem report
  response_sent --> completed:  step3. received complete
  response_sent --> request_received: error3 receive problem report
  response_sent --> abandoned:eror3 unable continue after received problem report

  abandoned --> [*]
  completed --> [*]
```
