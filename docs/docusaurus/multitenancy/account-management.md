# Account Management

**Status**: draft

**Date**: 2021-06-02

**NOTE**: this document is a draft and is not implemented yet. Statement in this document might be changed in the future.

## Introduction

This document describes the account management in the Identus Platform, types of accounts, and their usage, account authentication, and logical isolation of accounts.

## Technical Overview

Account management is a set of operations that allow users to manage their accounts.
Account is required for both single and multi-tenant configurations.
In the Identus Platform, the account owns the corresponding Wallet managed by the Cloud Agent.
The account is identified by the tenant ID and represented by the Entity in the Vault service.

### Account Types

The Identus Platform supports the following types of accounts:

- Cloud Agent Account - application account used by the Cloud Agent to authenticate itself to the Vault service
- Wallet Account - application or user account used to access the Wallet assets over the REST API or WEB UI

### Account Creation

The Cloud Agent Account is created by before the start of the Cloud Agent using the Vault cli, REST API or WEB UI.
The Wallet account can be created on the go by the Agent or using the Vault cli, REST API or WEB UI.

### Cloud Agent Account

The Cloud Agent account required to authenticate the instance of the Cloud Agent.
The account is responsible for creating the Wallet accounts and issuing the tokens to the Wallet instance.
The Cloud Agent account doesn't have the access to the Wallet secrets.
The Cloud Agent uses [AppRole](https://www.vaultproject.io/docs/auth/approle) authentication method to authenticate itself to the Vault service.

### Wallet Account

The Wallet account is required to authenticate the entity to the Identus Platform and give it the access to Wallet.
The Wallet account can be authenticated by the following methods:

- JWT/OIDC token
- user/password
- AppRole method (for the applications)
- other methods supported by the Vault service

The Wallet is initialized by the Agent on demand and internally communicates with the Agent to reissue the access token.
The Wallet account must guarantee the data isolation between tenants in the multi-tenant configuration by leveraging the policies of the Vault service and Row Based Policies in the PostgreSQL database.

**NOTE**: It's still discussed which component will be responsible for issuing the JWT token. The Vault service looks promising for configuring OIDC authentication method and use 3rd party system for authentication.

### Account Deactivation

Any account can be deactivated by deactivating the entity in the Vault service using the REST API or cli.
The deactivated account can be reactivated by the SRE team.
In the case when the account must be deleted, the entity must be deleted from the Vault service.
Retention policy is a matter of the configuration and must be discussed separately.

## Links

- [Vault AppRole](https://www.vaultproject.io/docs/auth/approle)
- [Vault Entities and Groups](https://developer.hashicorp.com/vault/tutorials/auth-methods/identity)
- [Vault JWT/OIDC](https://developer.hashicorp.com/vault/api-docs/auth/jwt)
