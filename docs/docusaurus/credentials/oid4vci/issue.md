# Issue credentials (OID4VCI)

[OID4VCI](/home/concepts/glossary#oid4vci) (OpenID for Verifiable Credential Issuance) is a protocol that extends OAuth2 to issue credentials.
It involves a Credential Issuer server and an Authorization server working together,
using the authorization and token endpoints on the Authorization Server to grant holders access to credentials on the Credential Issuer server.
These servers may or may not be the same, depending on the implementation.

The Identus Cloud Agent can act as a Credential Issuer server and integrate with any Authorization Server that follows the integration contract. The contract for the Authorization Server in the OID4VCI flow can be found [here](https://github.com/hyperledger-identus/cloud-agent/blob/main/docs/general/authserver-oid4vci-contract.md).

## Example: OID4VCI Authorization Code Issuance

Example is available [here](https://github.com/hyperledger-identus/cloud-agent/tree/main/examples/st-oid4vci).

Following the instructions, the example demonstrates a single-tenant agent setup using an external Keycloak as the Issuer Authorization Server. The demo application walks through the authorization code issuance flow step-by-step.

#### 1. Launching Local Example Stack

```bash
docker-compose up
```

After running the `docker-compose up` command, all the containers should be running and initialized with the necessary configurations. The following logs should appear indicating that the stack is ready to execute the flow

```
   _   _   _        _ _
  | |_| |_| |_ _ __| | | ___
  | ' \  _|  _| '_ \_  _(_-<
  |_||_\__|\__| .__/ |_|/__/
              |_|
 2024-07-16_11:51:01.301 INFO  o.h.b.s.BlazeServerBuilder@L424:[ZScheduler-Worker-5] {} - http4s v0.23.23 on blaze v0.23.15 started at http://0.0.0.0:8085/

```

#### 2. Building the demo application

```bash
docker build -t identus-oid4vci-demo:latest ./demo
```

#### 3. Running the demo application

```bash
docker run --network <NETWORK_NAME> -it identus-oid4vci-demo:latest
```
The parameter `NETWORK_NAME` should be the same as the network name in docker-compose.
This name can be discovered by running the `docker network ls` command.

The demo application acts as both issuer and Holder in the same script.
See the source code for detailed steps on how to implement this flow.
The demo application will interactively prompt the next step in the issuance flow.
Keep continuing until this log appears asking the user to log in using the browser.

```
##############################

Open this link in the browser to login

http://localhost:9980/realms/students/protocol/openid-connect/auth?redirect_uri=.....

##############################

wating for authorization redirect ...
```

Open this URL in the browser. Enter `alice` for the username and `1234` for the password.

After a successful login, this log should appear indicating the demo application has received the credentials.

```
::::: Credential Received :::::
{
  "credential": "eyJ0eXAiOiJKV1QiLC...SK1vJK-fx6zjXw"
}
```
