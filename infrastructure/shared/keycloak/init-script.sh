#!/usr/bin/env bash

set -e
set -u

KEYCLOAK_BASE_URL=$KEYCLOAK_BASE_URL
KEYCLOAK_ADMIN_USER=$KEYCLOAK_ADMIN_USER
KEYCLOAK_ADMIN_PASSWORD=$KEYCLOAK_ADMIN_PASSWORD
REALM_NAME=$REALM_NAME
PRISM_AGENT_CLIENT_SECRET=$PRISM_AGENT_CLIENT_SECRET

function get_admin_token() {
	local response
	response=$(
		curl --request POST "$KEYCLOAK_BASE_URL/realms/master/protocol/openid-connect/token" \
			--fail -s \
			-d "grant_type=password" \
			-d "client_id=admin-cli" \
			-d "username=$KEYCLOAK_ADMIN_USER" \
			-d "password=$KEYCLOAK_ADMIN_PASSWORD"
	)
	local access_token
	access_token=$(echo "$response" | jq -r '.access_token')
	echo $access_token
}

function create_realm() {
	local access_token=$1

	curl --request POST "$KEYCLOAK_BASE_URL/admin/realms" \
		--fail -s \
		-H "Authorization: Bearer $access_token" \
		-H "Content-Type: application/json" \
		--data-raw "{
			\"realm\": \"$REALM_NAME\",
			\"enabled\": true
		}"
}

function create_client() {
	local access_token=$1
	local client_id=$2
	local client_secret=$3

	curl --request POST "$KEYCLOAK_BASE_URL/admin/realms/$REALM_NAME/clients" \
		--fail -s \
		-H "Authorization: Bearer $access_token" \
		-H "Content-Type: application/json" \
		--data-raw "{
			\"id\": \"$client_id\",
			\"directAccessGrantsEnabled\": true,
			\"authorizationServicesEnabled\": true,
			\"serviceAccountsEnabled\": true,
			\"secret\": \"$client_secret\"
		}"
}

function create_user() {
	local access_token=$1
	local username=$2
	local password=$3

	curl --request POST "$KEYCLOAK_BASE_URL/admin/realms/$REALM_NAME/users" \
		--fail -s \
		-H "Authorization: Bearer $access_token" \
		-H "Content-Type: application/json" \
		--data-raw "{
			\"id\": \"$username\",
			\"username\": \"$username\",
			\"firstName\": \"$username\",
			\"enabled\": true,
			\"credentials\": [{\"value\": $password, \"temporary\": false}]
		}"
}

echo "Getting admin access token ..."
ADMIN_ACCESS_TOKEN=$(get_admin_token)

echo "Creating a new test realm ..."
create_realm $ADMIN_ACCESS_TOKEN

echo "Creating a new prism-agent client ..."
create_client $ADMIN_ACCESS_TOKEN "prism-agent" $PRISM_AGENT_CLIENT_SECRET

echo "Creating a new sample user ..."
create_user $ADMIN_ACCESS_TOKEN "alice" "1234"

echo "Creating a new sample user ..."
create_user $ADMIN_ACCESS_TOKEN "bob" "1234"
