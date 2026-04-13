# Webhook Notifications

## Introduction

Welcome to the tutorial on webhook notifications in the Cloud Agent. In this tutorial, we will explore how webhook
notifications can enhance your experience with the Cloud Agent by providing real-time updates on events. By leveraging
webhook notifications, you can stay informed about important changes happening within the agent.

## Understanding Webhook Notifications

### What are Webhooks?

Webhooks enable real-time communication between applications by sending HTTP requests containing event data to specified
endpoints (webhook URLs) when events occur. They establish a direct communication channel, allowing applications to
receive instant updates and respond in a timely manner, promoting efficient integration between event-driven
systems.

### Purpose of Webhook Notifications in the Cloud Agent

Webhook notifications in the CLoud Agent serve as a vital feature, enabling you to receive timely updates on various events
occurring within the agent. Webhooks allow you to receive HTTP requests containing event details at a specified
endpoint (webhook URL). These events are specifically related to the execution of
the [Connect](/tutorials/connections/connection), [Issue](/tutorials/credentials/didcomm/issue),
and [Presentation](/tutorials/credentials/didcomm/present-proof) flows. Webhook notifications will be sent each time there is a
state
change during the execution of these protocols.

By leveraging webhooks, you can integrate the Cloud Agent seamlessly into your applications and systems. You can track and
monitor the progress of the main flows, receiving timely updates about changes and events.

## Configuring the Webhook Feature

### Enabling the Webhook Feature

There are two kinds of webhook notifications: global webhooks and wallet webhooks.
Global webhooks capture all events that happen on the Cloud Agent across all wallets,
whereas wallet webhooks only capture events that are specific to assets within a particular wallet.

#### Enable global webhook using environment variables

The Cloud Agent uses the following environment variables to manage global webhook notifications:

| Name                     | Description                                                              | Default |
|--------------------------|--------------------------------------------------------------------------|---------|
| `GLOBAL_WEBHOOK_URL`     | The webhook endpoint URL where the notifications will be sent            | null    |
| `GLOBAL_WEBHOOK_API_KEY` | The optional API key (bearer token) to use as the `Authorization` header | null    |

#### Enable wallet webhook for default wallet using environment variables

In a multi-tenant scenario, the Cloud Agent can optionally create a default wallet to simplify the development and deployment process.
The webhook configuration for this default wallet can be defined using environment variables.
After the default wallet is created, its webhook settings are stored in the system and are no longer influenced by these environment variables.

| Name                             | Description                                                              | Default |
|----------------------------------|--------------------------------------------------------------------------|---------|
| `DEFAULT_WALLET_ENABLED`         | Automatically create default on the Cloud Agent startup                  | true    |
| `DEFAULT_WALLET_WEBHOOK_URL`     | The webhook endpoint URL where the notifications will be sent            | null    |
| `DEFAULT_WALLET_WEBHOOK_API_KEY` | The optional API key (bearer token) to use as the `Authorization` header | null    |

#### Enable wallet hook using REST API

In a multi-tenant scenario, there is an option to configure wallet webhook parameters using a REST API, which offers more flexibility.
For each individual wallet, users can create a new webhook by making a POST request to `/events/webhooks`,
which in turn creates a new webhook resource specific to their wallet.

```bash
curl --location --request POST 'http://localhost:8080/cloud-agent/events/webhooks' \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json' \
  --header "apiKey: $API_KEY" \
  --data-raw '{
    "url": "http://localhost:9095"
  }'
```

Response Example:

```json
{
    "id": "e9569dd0-bffa-4be4-94fe-f5025a79029a",
    "url": "http://localhost:9095",
    "customHeaders": {},
    "createdAt": "2023-09-12T08:39:03.871339Z"
}
```

### Securing the Webhook Endpoint

It is essential to secure the webhook endpoint to protect the integrity and confidentiality of the event data. Consider
the following best practices when securing your webhook endpoint:

- Use HTTPS to encrypt communication between the Cloud Agent and the webhook endpoint.
- Implement authentication mechanisms (e.g., API keys, tokens) to verify the authenticity of incoming requests.
- Validate and sanitize incoming webhook requests to mitigate potential security risks.

One of the authorization mechanism for the Cloud Agent's webhook notifications is the bearer token. If
configured, the token will be included in the `Authorization` header of the HTTP request sent by the agent to the
webhook endpoint. You can configure this bearer token by setting the value of the
`GLOBAL_WEBHOOK_API_KEY` or `DEFAULT_WALLET_WEBHOOK_API_KEY` environment variable.

An alternative approach is to make use of the `customHeaders` property within the REST API for configuring webhooks.
This option offers increased flexibility when custom or multiple headers are needed.

## Event Format and Types

### Event Format

Webhook notifications from the Cloud Agent are sent as JSON payloads in the HTTP requests.

The event format is consistent across all events. Each event follows a common structure, while the 'data' field
within the event payload contains information specific to the type of event. Here is an example of the JSON payload
format:

```json
{
  "id": "cb8d4e96-30f0-4892-863f-44d49d634211",
  "ts": "2023-07-06T12:01:19.769427Z",
  "type": "xxxx",
  "data": {
    // Event-specific data goes here 
  },
  "walletId": "00000000-0000-0000-0000-000000000000"
}
```

This event format ensures consistency and allows you to handle webhook notifications uniformly while easily extracting
the relevant data specific to each event type from the `data` field.

Here is a complete example of a webhook notification event related to a connection flow state change (invitation
generated):

```json
{
  "id": "cb8d4e96-30f0-4892-863f-44d49d634211",
  "ts": "2023-07-06T12:01:19.769427Z",
  "type": "ConnectionUpdated",
  "data": {
    "connectionId": "c10787cf-99bb-47f4-99bb-1fdcca32b673",
    "label": "Connect with Alice",
    "role": "Inviter",
    "state": "InvitationGenerated",
    "invitation": {
      "id": "c10787cf-99bb-47f4-99bb-1fdcca32b673",
      "type": "https://didcomm.org/out-of-band/2.0/invitation",
      "from": "did:peer:2.Ez6LS...jIiXX0",
      "invitationUrl": "https://my.domain.com/path?_oob=eyJpZCI6...bXX19"
    },
    "createdAt": "2023-07-06T12:01:19.760126Z",
    "self": "c10787cf-99bb-47f4-99bb-1fdcca32b673",
    "kind": "Connection"
  },
  "walletId": "00000000-0000-0000-0000-000000000000"
}
```

### Common Event Types

The Cloud Agent sends webhook notifications for events related to protocol state changes in
the [Connect](/tutorials/connections/connection), [Issue](/tutorials/credentials/didcomm/issue),
[Presentation](/tutorials/credentials/didcomm/present-proof) flows, and also [DID publication](/tutorials/dids/publish)
state changes. These events allow you to track the progress and updates within these flows in real-time.

The `id` field of the common event structure is the unique identifier (UUID) of the event and is randomly generated at
event creation time.

The `ts` field contains the timestamp (date + time) at which the event was created.

The `walletId` field contains information about the wallet from which the event originates.

The `type` field indicates to which flow/process the received event is related, and hence the type of JSON payload that
can be expected in the inner `data` field. Possible values are:

| Value                          | Description                                 |
|--------------------------------|---------------------------------------------|
| `ConnectionUpdated`            | An update in the connection flow state      |
| `IssueCredentialRecordUpdated` | An update in the VC issuance flow state     |
| `PresentationUpdated`          | An update in the VC presentation flow state |
| `DIDStatusUpdated`             | An update in the DID publication state      |

State change notifications that you can expect to receive through webhook notifications include:

- Connection State Change: Notifies about state changes in the connection flow, such as `InvitationGenerated`,
  `ConnectionRequestSent`, `ConnectionResponseReceived`, etc. Please refer to the `state` field of
  the [connection resource](/agent-api/#tag/Connections-Management/operation/getConnection)
  for an exhaustive list of states.
- Credential State Change: Indicates changes in the credential issuance flow, such as `OfferSent`, `RequestReceived`,
  `CredentialSent`, etc. Please refer to the `protocolState` field of
  the [credential resource](/agent-api/#tag/Issue-Credentials-Protocol/operation/getCredentialRecord)
  for an exhaustive list of states.
- Presentation State Change: Notifies about changes in the presentation flow, such as `RequestReceived`,
  `PresentationGenerated`, `PresentationVerified`, etc. Please refer to the `status` field of
  the [presentation resource](/agent-api/#tag/Present-Proof/operation/getPresentation) for an
  exhaustive list of states.
- DID State Change: Notifies about DID-related state changes. Currently, only the `Published` DID publication state
  event will be notified.

## Processing Webhook Notifications

### Handling Incoming Webhook Requests

To handle incoming webhook notifications from the Cloud Agent in your application, follow these general steps:

1. Receive the HTTP request at your specified webhook endpoint.
2. Parse the JSON payload of the request to extract the event details.
3. Process the event data according to your application's requirements.
4. Send a response back to acknowledge the successful receipt of the webhook notification. For a successful reception,
   the response status code should be `>= 200` and `< 300`. Any other response status code will lead to a new attempt
   from the Cloud Agent.

### Error Handling and Retry Mechanisms

When working with webhook notifications in the Cloud Agent, it is important to consider error handling and retry mechanisms.
In case of failed webhook notifications or errors, the Cloud Agent employs an automatic retry mechanism to ensure delivery.
The agent will attempt to send the webhook notification up to three times, with a five-second interval between each
attempt. Please note that the number of retries and the interval duration are currently not configurable in the Cloud Agent.

By default, this retry mechanism provides a reasonable level of reliability for delivering webhook notifications,
allowing for temporary network issues or intermittent failures.

### A basic Webhook implementation for logging requests

In the following example, we will demonstrate a simple Python code snippet that sets up a webhook endpoint and logs
incoming HTTP requests to the console. This basic implementation can serve as a starting point for building more
advanced webhook systems.

In the provided Python code snippet, the port on which the webhook listener will listen for incoming requests should be
passed as a command-line parameter. This allows flexibility in starting multiple webhooks in parallel, which is useful
when testing multiple locally running agents, e.g. for a holder, an issuer, and/or a verifier.

```python
#!/usr/bin/env python3
"""
Very simple HTTP server in python for logging requests
Usage::
    ./server.py [<port>]
"""
import logging
import json
from http.server import BaseHTTPRequestHandler, HTTPServer

grey = "\x1b[38;20m"
yellow = "\x1b[33;20m"
green = "\x1b[32;20m"
red = "\x1b[31;20m"
bold_red = "\x1b[31;1m"
reset = "\x1b[0m"

consoleHandler = logging.StreamHandler()
formatter = logging.Formatter(f"""%(asctime)s - %(levelname)s - %(name)s
--------------------------------------- request ---------------------------------------
{green}%(method)s %(path)s{reset}
%(headers)s
{yellow}%(data)s{reset}
-----------------------------------
"""
                              )
consoleHandler.setFormatter(formatter)
consoleHandler.setLevel(logging.INFO)

logger = logging.getLogger('http-request')
logger.setLevel(logging.INFO)
logger.addHandler(consoleHandler)

class S(BaseHTTPRequestHandler):

    def _set_response(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/html')
        self.end_headers()

    def do_GET(self):
        logging.info("GET request,\nPath: %s\nHeaders:\n%s\n", str(self.path), str(self.headers))
        self._set_response()
        self.wfile.write("GET request for {}".format(self.path).encode('utf-8'))

    def do_POST(self):
        content_length = int(self.headers['Content-Length'])  # <--- Gets the size of data
        post_data = self.rfile.read(content_length)  # <--- Gets the data itself
        json_obj = json.loads(post_data.decode('utf-8'))
        json_data = json.dumps(json_obj, indent=2)
        logger.info(msg="Request content", extra={
            'method': "POST",
            'path': str(self.path),
            'headers': str(self.headers),
            'data': json_data
        })
        self._set_response()
        self.wfile.write("POST request for {}".format(self.path).encode('utf-8'))

    def log_message(self, format, *args):
        pass


def run(server_class=HTTPServer, handler_class=S, port=80):
    server_address = ('', port)
    httpd = server_class(server_address, handler_class)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()


if __name__ == '__main__':
    from sys import argv

    if len(argv) == 2:
        run(port=int(argv[1]))
    else:
        run()
```

## Conclusion

Congratulations! You've learned about webhook notifications in the Cloud Agent. By leveraging this feature, you can receive
real-time updates on events happening within the agent, enabling you to integrate the Cloud Agent seamlessly into your
applications. Remember to secure your webhook endpoint and handle webhook notifications effectively to maximize the
benefits of this feature.

Start integrating webhook notifications into your Cloud Agent workflow and unlock the power of real-time event updates!

If you have any further questions or need assistance, don't hesitate to reach out to the Identus support team or
refer to the official documentation for more details.
