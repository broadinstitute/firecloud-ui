#!/usr/bin/env bash

ENV=${1-prod}
VAULT_TOKEN=${2:-$VAULT_TOKEN}

echo ""
echo "Running tests against real env $ENV"
echo ""

docker build -f Dockerfile-tests -t automation .
cd docker
./run-tests.sh 4 ${ENV} ${ENV} automation ${VAULT_TOKEN}
