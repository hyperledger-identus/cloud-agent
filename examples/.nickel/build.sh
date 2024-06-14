#!/usr/bin/env bash

find . -name "*.ncl" -print0 | xargs -0 -I _ nickel format _

nickel export ./root.ncl -f yaml --field st >../st/compose.yaml
nickel export ./root.ncl -f yaml --field st-vault >../st-vault/compose.yaml
nickel export ./root.ncl -f yaml --field st-multi >../st-multi/compose.yaml
nickel export ./root.ncl -f yaml --field st-oid4vci >../st-oid4vci/compose.yaml

nickel export ./root.ncl -f yaml --field mt >../mt/compose.yaml
nickel export ./root.ncl -f yaml --field mt-keycloak >../mt-keycloak/compose.yaml
nickel export ./root.ncl -f yaml --field mt-keycloak-vault >../mt-keycloak-vault/compose.yaml
