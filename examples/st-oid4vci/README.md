# How to Run Issuance Flow

## Prerequisites

- Docker version 2.24.0 or later

### 1. Start the Agent Stack with Pre-configured Keycloak

```bash
docker-compose up
```

The Keycloak UI is available at [http://localhost:9980](http://localhost:9980).
Admin username: `admin`
Admin password: `admin`

### 2. Run the Issuance Demo Script

Build and run the demo application:

```bash
docker build -t identus-oid4vci-demo:latest ./demo
docker run --network <NETWORK_NAME> -it identus-oid4vci-demo:latest
```

Where `<NETWORK_NAME>` is the Docker Compose network name from the agent stack (default: `st-oid4vci_default`).

- Follow the instructions in the terminal. The holder will be prompted to log in via a browser.
- Use username `alice` and password `1234` to log in.
- Grant access for the scopes displayed on the consent UI.

The credential will be issued at the end of the flow and logged to the terminal.
