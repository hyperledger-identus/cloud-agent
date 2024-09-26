import json
import time
import urllib

import jwt
import requests
from cryptography.hazmat.primitives.asymmetric import ec

MOCKSERVER_URL = "http://mockserver:1080"
LOGIN_REDIRECT_URL = "http://localhost:7777/cb"

AGENT_URL = "http://caddy-issuer:8080/cloud-agent"
CREDENTIAL_ISSUER = None
CREDENTIAL_ISSUER_DID = None
CREDENTIAL_CONFIGURATION_ID = "UniversityDegreeCredential"
AUTHORIZATION_SERVER = "http://external-keycloak-issuer:8080/realms/students"

ALICE_CLIENT_ID = "alice-wallet"

HOLDER_LONG_FORM_DID = "did:prism:67c303e6129250ccaf8bc027d8d7ede1d6232bf3427b62d4a8b486a28e3444bd:CrcBCrQBEjkKBWlzcy0xEAJKLgoJc2VjcDI1NmsxEiEDbKCvbUqisyoIcbA6QXBJ6t49mC0d1m20rDWcTpzcC48SOgoGYXV0aC0xEARKLgoJc2VjcDI1NmsxEiEDofvTGCeRa6u409XYGzSOhO9iz1SxUhPFE6S45ghLeJMSOwoHbWFzdGVyMBABSi4KCXNlY3AyNTZrMRIhAh3UQ_vdi9Em1I_GqhXeI21fe5wpV_Pa2bhmuLH21i2w"
HOLDER_ASSERTION_PRIVATE_KEY_HEX = (
    "d68cc4364d1d7215085c9ae02696f361f2040b68432d68203cf5c1d3808aa0ea"
)
HOLDER_AUTH_PRIVATE_KEY_HEX = (
    "e5918230337434fb3fd5b04eab749eba2c441114624100859ca8994096ffa1fd"
)


def prepare_mock_server():
    """
    Prepare mock server for authorization redirect from front-end channel
    """

    requests.put(f"{MOCKSERVER_URL}/mockserver/reset")
    requests.put(
        f"{MOCKSERVER_URL}/mockserver/expectation",
        json={
            "httpRequest": {"path": "/cb"},
            "httpResponse": {
                "statusCode": 200,
                "body": {"type": "string", "string": "Login Successful"},
            },
        },
    )


def prepare_issuer():
    """
    Prepare an oid4vci issuer
    1. Create issuing DID
    2. Publish issuing DID
    3. Create credential schema
    4. Create oid4vci issuer
    5. Create oid4vci credential configuration from schema
    """

    # 1. Create issuing DID
    dids = requests.get(f"{AGENT_URL}/did-registrar/dids").json()["contents"]
    if len(dids) == 0:
        requests.post(
            f"{AGENT_URL}/did-registrar/dids",
            json={
                "documentTemplate": {
                    "publicKeys": [{"id": "iss", "purpose": "assertionMethod"}],
                    "services": [],
                }
            },
        )
        dids = requests.get(f"{AGENT_URL}/did-registrar/dids").json()["contents"]

    # 2. Publish issuing DID
    issuer_did = dids[0]
    while issuer_did["status"] != "PUBLISHED":
        time.sleep(2)
        canonical_did = issuer_did["did"]
        issuer_did = requests.get(
            f"{AGENT_URL}/did-registrar/dids/{canonical_did}"
        ).json()

        # publish if not pending
        if issuer_did["status"] == "CREATED":
            requests.post(
                f"{AGENT_URL}/did-registrar/dids/{canonical_did}/publications"
            )
    canonical_did = issuer_did["did"]
    global CREDENTIAL_ISSUER_DID
    CREDENTIAL_ISSUER_DID = canonical_did

    # 3. Create credential schema
    schema = requests.post(
        f"{AGENT_URL}/schema-registry/schemas",
        json={
            "name": "UniversityDegree",
            "version": "1.0.0",
            "type": "https://w3c-ccg.github.io/vc-json-schemas/schema/2.0/schema.json",
            "schema": {
                "$id": "https://example.com/driving-license-1.0",
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "type": "object",
                "properties": {
                    "firstName": {"type": "string"},
                    "degree": {"type": "string"},
                    "grade": {"type": "number"},
                },
                "required": ["firstName", "grade"],
                "additionalProperties": False,
            },
            "tags": [],
            "author": canonical_did,
        },
    ).json()
    schema_guid = schema["guid"]

    # 4. Create oid4vci issuer
    credential_issuer = requests.post(
        f"{AGENT_URL}/oid4vci/issuers",
        json={
            "authorizationServer": {
                "url": "http://external-keycloak-issuer:8080/realms/students",
                "clientId": "cloud-agent",
                "clientSecret": "secret",
            }
        },
    ).json()
    issuer_id = credential_issuer["id"]
    global CREDENTIAL_ISSUER
    CREDENTIAL_ISSUER = f"{AGENT_URL}/oid4vci/issuers/{issuer_id}"

    # 5. Create oid4vci credential configuration from schema
    cred_config = requests.post(
        f"{CREDENTIAL_ISSUER}/credential-configurations",
        json={
            "configurationId": CREDENTIAL_CONFIGURATION_ID,
            "format": "jwt_vc_json",
            "schemaId": f"{AGENT_URL}/schema-registry/schemas/{schema_guid}/schema",
        },
    ).json()


def issuer_create_credential_offer(claims):
    response = requests.post(
        f"{CREDENTIAL_ISSUER}/credential-offers",
        json={
            "credentialConfigurationId": CREDENTIAL_CONFIGURATION_ID,
            "issuingDID": CREDENTIAL_ISSUER_DID,
            "claims": claims,
        },
    )
    return response.json()["credentialOffer"]


def holder_get_issuer_metadata(credential_issuer: str):
    metadata_url = f"{credential_issuer}/.well-known/openid-credential-issuer"
    response = requests.get(metadata_url).json()
    return response


def holder_get_issuer_as_metadata(authorization_server: str):
    metadata_url = f"{authorization_server}/.well-known/openid-configuration"
    response = requests.get(metadata_url)
    metadata = response.json()
    return metadata


def holder_start_login_flow(auth_endpoint: str, token_endpoint: str, issuer_state: str):
    def wait_redirect_authorization_code() -> str:
        print("wating for authorization redirect ...")
        while True:
            response = requests.put(
                f"{MOCKSERVER_URL}/mockserver/retrieve?type=REQUESTS",
                json={"path": "/cb", "method": "GET"},
            ).json()
            if len(response) > 0:
                break
            time.sleep(1)

        authorzation_code = response[0]["queryStringParameters"]["code"][0]
        print(f"code: {authorzation_code}")
        return authorzation_code

    def start_authorization_request(auth_endpoint: str, issuer_state: str):
        # Authorization Request
        queries = urllib.parse.urlencode(
            {
                "redirect_uri": LOGIN_REDIRECT_URL,
                "response_type": "code",
                "client_id": ALICE_CLIENT_ID,
                "scope": "openid " + CREDENTIAL_CONFIGURATION_ID,
                "issuer_state": issuer_state,
            }
        )
        login_url = f"{auth_endpoint}?{queries}"
        print("\n##############################\n")
        print("Open this link in the browser to login\n")
        print(login_url)
        print("\n##############################\n")

        # wait for authorization redirect
        authorzation_code = wait_redirect_authorization_code()
        return authorzation_code

    def start_token_request(token_endpoint: str, authorization_code: str):
        # Token Request
        response = requests.post(
            token_endpoint,
            data={
                "grant_type": "authorization_code",
                "code": authorization_code,
                "client_id": ALICE_CLIENT_ID,
                "redirect_uri": LOGIN_REDIRECT_URL,
            },
        )
        return response.json()

    authorization_code = start_authorization_request(auth_endpoint, issuer_state)
    token_response = start_token_request(token_endpoint, authorization_code)
    return token_response


def holder_extract_credential_offer(offer_uri: str):
    queries = urllib.parse.urlparse(credential_offer_uri).query
    credential_offer = urllib.parse.parse_qs(queries)["credential_offer"]
    return json.loads(credential_offer[0])


def holder_get_credential(credential_endpoint: str, token_response):
    access_token = token_response["access_token"]
    c_nonce = token_response["c_nonce"]

    # generate proof
    private_key_bytes = bytes.fromhex(HOLDER_ASSERTION_PRIVATE_KEY_HEX)
    private_key = ec.derive_private_key(
        int.from_bytes(private_key_bytes, "big"), ec.SECP256K1()
    )
    public_key = private_key.public_key()
    jwt_proof = jwt.encode(
        headers={
            "typ": "openid4vci-proof+jwt",
            "kid": HOLDER_LONG_FORM_DID + "#iss-1",
        },
        payload={
            "iss": ALICE_CLIENT_ID,
            "aud": CREDENTIAL_ISSUER,
            "iat": int(time.time()),
            "nonce": c_nonce,
        },
        key=private_key,
        algorithm="ES256K",  # TODO: switch to EdDSA alg (Ed25519)
    )

    response = requests.post(
        credential_endpoint,
        headers={"Authorization": f"Bearer {access_token}"},
        json={
            "format": "jwt_vc_json",
            "credential_definition": {
                "type": ["VerifiableCredential", CREDENTIAL_CONFIGURATION_ID],
                "credentialSubject": {},
            },
            "proof": {"proof_type": "jwt", "jwt": jwt_proof},
        },
    )
    return response.json()


if __name__ == "__main__":
    prepare_mock_server()
    prepare_issuer()

    # step 1: Issuer create CredentialOffer
    credential_offer_uri = issuer_create_credential_offer(
        {"firstName": "Alice", "degree": "ChemicalEngineering", "grade": 3.2}
    )

    # step 2: Issuer present QR code container CredentialOffer URI
    credential_offer = holder_extract_credential_offer(credential_offer_uri)
    credential_offer_pretty = json.dumps(credential_offer, indent=2)
    issuer_state = credential_offer["grants"]["authorization_code"]["issuer_state"]
    print("\n##############################\n")
    print(f"QR code scanned, got credential-offer\n\n{credential_offer_uri}\n")
    print(f"\n{credential_offer_pretty}\n")
    print("\n##############################\n")
    input("\nEnter to continue ...")

    # step 3: Holdler retreive Issuer's metadata
    issuer_metadata = holder_get_issuer_metadata(CREDENTIAL_ISSUER)
    authorzation_server = issuer_metadata["authorization_servers"][0]
    print("\n::::: Issuer Metadata :::::")
    print(json.dumps(issuer_metadata, indent=2))
    input("\nEnter to continue ...")

    # step 3.1: Holder retreive Issuer's AS metadata
    issuer_as_metadata = holder_get_issuer_as_metadata(authorzation_server)
    issuer_as_token_endpoint = issuer_as_metadata["token_endpoint"]
    issuer_as_authorization_endpoint = issuer_as_metadata["authorization_endpoint"]
    print("\n::::: Issuer Authorization Server Metadata :::::")
    print(json.dumps(issuer_as_metadata, indent=2))
    print(f"issuer_as_auth_endpoint: {issuer_as_authorization_endpoint}")
    print(f"issuer_as_token_endpoint: {issuer_as_token_endpoint}")
    input("\nEnter to continue ...")

    # step 4: Holder start authorization flow
    token_response = holder_start_login_flow(
        issuer_as_authorization_endpoint, issuer_as_token_endpoint, issuer_state
    )
    print("::::: Token Response :::::")
    print(json.dumps(token_response, indent=2))
    input("\nEnter to continue ...")

    # step 5: Holder use access_token to get credential
    credential_endpoint = issuer_metadata["credential_endpoint"]
    jwt_credential = holder_get_credential(credential_endpoint, token_response)
    print("\n::::: Credential Received :::::")
    print(json.dumps(jwt_credential, indent=2))
