#!/bin/bash


docker build -f Dockerfile-tests -t automation .
cd docker
./run-tests.sh 4 alpha alpha automation $(cat ~/.vault-token)
