#!/usr/bin/env bash

echo ""
echo "!!! Checkout alpha branch of firecloud-ui !!!"
echo ""
docker build -f Dockerfile-tests -t automation .
cd docker
./run-tests.sh 4 alpha alpha automation $(cat ~/.vault-token)
