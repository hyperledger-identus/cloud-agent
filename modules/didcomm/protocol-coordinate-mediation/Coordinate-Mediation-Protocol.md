# Coordinate Mediation Protocol

This Protocol is part of the **DIDComm.org**

Is a protocol to coordinate mediation configuration between a mediating agent and the recipient.

This protocol follows the request-response message exchange pattern, and only requerires the simple state of waiting for a response or to produce a response.

See [https://didcomm.org/mediator-coordination/2.0/]

## PIURI

- `https://didcomm.org/coordinate-mediation/2.0/mediate-request`
  **- Mediate Request**
- `https://didcomm.org/coordinate-mediation/2.0/mediate-deny`
  **- Mediate Deny** (possible response to deny mediate-request)
- `https://didcomm.org/coordinate-mediation/2.0/mediate-grant`
  **- Mediate Grant** (possible response to grant mediate-request)

- `https://didcomm.org/coordinate-mediation/2.0/keylist-update`
  **- Keylist Update**
- `https://didcomm.org/coordinate-mediation/2.0/keylist-update-response`
  **- Keylist Response** (response to keylist-update)

- `https://didcomm.org/coordinate-mediation/2.0/keylist-query`
  **- Keylist Query**
- `https://didcomm.org/coordinate-mediation/2.0/keylist`
  **- Keylist** (response to keylist-query)

### Roles

- mediator
  - The agent that will be receiving forward messages on behalf of the recipient.
- recipient
  - The agent for whom the forward message payload is intended.

### Messagem Flow Diagram

```mermaid
flowchart TD
  MediateRequest --> MediateDeny
  MediateRequest --> MediateGrant

  KeylistUpdate --> KeylistUpdateResponse

  KeylistQuery --> Keylist
```

### Mediator state machine

```mermaid
stateDiagram-v2
  state MediateRequested <<choice>>
  [*] --> MediateRequested: Receive 'mediate-request'
  MediateRequested --> StoreNewMediateConfig
  StoreNewMediateConfig --> [*]: 'mediate-grant'
  MediateRequested --> [*]: 'mediate-deny'

  state if_sate2 <<choice>>
  [*] --> if_sate2: Receive 'keylist-update'
  if_sate2 --> UpdateKeylist
  UpdateKeylist -->  [*]: 'keylist-update-response'
  if_sate2 --> [*]: ignore

  state KeylistQueried <<choice>>
  [*] --> KeylistQueried: Receive 'keylist-query'
  KeylistQueried --> [*]: 'keylist'
  KeylistQueried --> [*]: ignore
```

### Recipient state machine

```mermaid
stateDiagram-v2
  direction LR
  state fork_state <<fork>>
  [*] --> fork_state
  fork_state --> RequestMediation
  fork_state --> RequestKeylistUpdate
  fork_state --> QueryKeylist

  state RequestMediation {
    [*] --> MediationRequested: send 'mediate-request'
    MediationRequested --> MediationDenied: 'mediate-deny'
    MediationRequested --> MediationRequested: timeout (retry)
    MediationRequested --> [*]: giveup
    MediationDenied --> [*]
    MediationDenied --> MediationRequested: retry?
    MediationRequested --> MediationGrant: 'mediate-grant'
    MediationGrant --> [*]
  }

  state RequestKeylistUpdate {
    [*] --> KeylistUpdateRequested: send 'keylist-update'
    KeylistUpdateRequested --> KeylistUpdateRequested: timeout (retry)
    KeylistUpdateRequested --> [*]: giveup
    KeylistUpdateRequested --> KeylistUpdated: 'keylist-update-response'
    KeylistUpdated --> [*]
  }

  state QueryKeylist {
    [*]--> KeylistQueried: send 'keylist-query'
    KeylistQueried --> KeylistQueried: timeout (retry)
    KeylistQueried --> [*]: giveup
    KeylistQueried --> [*]: 'keylist'
  }
```
