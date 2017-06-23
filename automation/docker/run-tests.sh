#!/bin/bash

# Parameters
NUM_NODES="${1:-4}"  # default to 4
ENV="${2:-dev}"  # default to dev
export ENV=$ENV

#if test runs against a remote FIAB on a GCE node, put IP in param 3
#if test runs against a local FIAB on a Dokcer, put "local" in param 3
#else leave blank (test runs against a non FIAB host, such as real dev)
DOCKERHOST="127.0.0.1"
DOCKERHOST=${3:-$DOCKERHOST}
export DOCKERHOST=$DOCKERHOST
TEST_CONTAINER=${4:-automation}
VAULT_TOKEN=$5

if [ -z VAULT_TOKEN ]; then
    VAULT_TOKEN=$(cat ~/.vault-token)
fi

if [ $DOCKERHOST == "local" ]
  then
    docker pull chickenmaru/nettools
    export DOCKERHOST=`docker run --net=docker_default -it --rm chickenmaru/nettools sh -c "ip route|grep default|cut -d' ' -f 3"`
    echo "Docker Host from Docker Perspective: $DOCKERHOST"
fi

# define some colors to use for output
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

# start up
startup() {
    containers=$(docker ps | grep python_chrome_*)
    if [[ -z $containers ]]; then
        # cleanup old containers
        docker rm -v $(docker ps -a -q -f status=exited)
    else
        echo "Tests are already running on this host.  Kill current tests or try again later."
        exit 1
    fi
}
# kill and remove any running containers
cleanup () {
  docker-compose -f hub-compose.yml stop
  docker stop $TEST_CONTAINER
}

# cleanup old containers
docker rm -v $(docker ps -a -q -f status=exited)

# catch unexpected failures, do cleanup and output an error message
trap 'cleanup ; printf "${RED}Tests Failed For Unexpected Reasons${NC}\n"'\
  HUP INT QUIT PIPE TERM

# build and run the composed services
echo "HOST IP: $DOCKERHOST"
docker-compose -f hub-compose.yml up -d
docker-compose -f hub-compose.yml scale chrome=$NUM_NODES

# render ctmpls
#docker run --rm -e VAULT_TOKEN=${VAULT_TOKEN} \
    #-e ENVIRONMENT=${ENV} -v $(pwd):/working \
    #broadinstitute/dsde-toolbox:dev consul-template \
        #-config=/etc/consul-template/config/config.json \
        #-template=application.conf.ctmpl:application.conf \
        #-once

# run tests
docker run -e DOCKERHOST=$DOCKERHOST \
    --net=docker_default \
    -e ENV=$ENV \
    --add-host=firecloud-fiab.dsde-${ENV}.broadinstitute.org:${DOCKERHOST} \
    --add-host=firecloud-orchestration-fiab.dsde-${ENV}.broadinstitute.org:${DOCKERHOST} \
    -P --rm -t -e CHROME_URL="http://hub:4444/" \
    -v $PWD/application.conf:/app/automation/src/test/resources/application.conf \
    -v $PWD/firecloud-qa.pem:/app/automation/src/test/resources/firecloud-qa.pem \
    -v jar-cache:/root/.ivy -v jar-cache:/root/.ivy2 \
    --link docker_hub_1:hub --name ${TEST_CONTAINER} -w /app \
    ${TEST_CONTAINER}:latest

# Grab exit code of tests
TEST_EXIT_CODE=$?

if [ $? -ne 0 ] ; then
  printf "${RED}Docker Compose Failed${NC}\n"
  exit -1
fi

# inspect the output of the test and display respective message
if [ -z ${TEST_EXIT_CODE+x} ] || [ "$TEST_EXIT_CODE" -ne 0 ] ; then
  printf "${RED}Tests Failed${NC} - Exit Code: $TEST_EXIT_CODE\n"
else
  printf "${GREEN}Tests Passed${NC}\n"
fi

# call the cleanup fuction
cleanup

# exit the script with the same code as the test service code
exit $TEST_EXIT_CODE
