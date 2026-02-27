# OutOfBand Login Protocol

Its a out-of-band style protocol.

## PIURI

- `https://atalaprism.io/mercury/outofband-login/1.0/invitation`
  **- Invitation to login**
- `https://atalaprism.io/mercury/outofband-login/1.0/reply`
  **- Mediate Reply**

### Roles

- WebServer
  - The DID Comm agent (server) that invite to create a session
- Client
  - The DID Comm agent that want to start the session (by login with is DID).

### Messagem Flow Diagram

```mermaid
flowchart TD
  WebServer --> Client
  Client --> WebServer
```

### Example of Sequence Diagram

In the following we use 4 participant, but you can view the `DIDWallet` and `Client` as being one and same for `WebServer` and `CloudAgent`.

```mermaid
sequenceDiagram
  participant DIDWallet
  participant Client
  participant WebServer
  participant CloudAgent

  Note over Client,WebServer: Communication via HTTPS

  Client->>+WebServer: Ask for a website
  WebServer-->>-Client: Deliver WebApp


  Note over Client,WebServer: Communication via WebSocket Secure (WSS)

  Client->>+WebServer: Open a WWS (create a session)
  WebServer-->>-Client: WSS stablish

  rect rgb(0, 100, 0)
    WebServer->>+CloudAgent: Generate Invitation for this session
    CloudAgent-->>-WebServer: Unique Invitation (Msg signed)
  end
  WebServer->>Client: Send Out-of-Band Invitation
  rect rgb(0, 100, 0)
    alt Open Invitation in Wallet
      Client->> DIDWallet: Bootstrap Wallet (by open oob url)
    else
      Client->>DIDWallet: QR code Scann
    end
    DIDWallet->>+CloudAgent: Resolve Challenge by Reply to Invitation (Msg Encrypted)
  end

  CloudAgent->>WebServer: Infor of login for invitation id awith DID
  WebServer->>WebServer: Match unique id invitation with the session and store DID of client

  WebServer->>Client: ...
```
