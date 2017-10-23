#!/bin/bash

if [ -z ${1+x} ]; then
  echo "Must specify where Firecloud is running."
  exit 1
fi

# Defaults
WORKING_DIR=$PWD
ENV=dev
VAULT_TOKEN=$(cat ~/.vault-token)
NUM_NODES=2
TEST_ENTRYPOINT="testOnly -- -l ProdTest"
TEST_CONTAINER="$(cat /dev/urandom | env LC_CTYPE=C tr -dc 'a-zA-Z0-9' | fold -w 8 | head -n 1)"

# Parameters
FC_INSTANCE=${1}
ENV=${2:-$ENV}
VAULT_TOKEN=${3:-$VAULT_TOKEN}
WORKING_DIR=${4:-$WORKING_DIR}

export FC_INSTANCE WORKING_DIR ENV

if [ "$ENV" = "prod" ]; then
  TEST_ENTRYPOINT="testOnly -- -n ProdTest"
fi

if [ "$FC_INSTANCE" = "local" ]; then
  FC_INSTANCE="172.19.0.1"
fi

if [ "$FC_INSTANCE" = "fiab" ]; then
  FC_INSTANCE="$(grep 'firecloud-fiab.dsde-dev.broadinstitute.org' /etc/hosts | awk '{print $1}')"
fi

if [ "$FC_INSTANCE" = "alpha" ] || [ "$FC_INSTANCE" = "prod" ]; then
  HUB_COMPOSE=hub-compose.yml
else
  HOST_MAPPING="--add-host=firecloud-fiab.dsde-${ENV}.broadinstitute.org:${FC_INSTANCE} --add-host=firecloud-orchestration-fiab.dsde-${ENV}.broadinstitute.org:${FC_INSTANCE} --add-host=rawls-fiab.dsde-${ENV}.broadinstitute.org:${FC_INSTANCE} --add-host=thurloe-fiab.dsde-${ENV}.broadinstitute.org:${FC_INSTANCE} --add-host=sam-fiab.dsde-${ENV}.broadinstitute.org:${FC_INSTANCE} -e SLACK_API_TOKEN=$SLACK_API_TOKEN -e BUILD_NUMBER=$BUILD_NUMBER -e SLACK_CHANNEL=${SLACK_CHANNEL}"
  HUB_COMPOSE=hub-compose-fiab.yml
fi


echo "Building test image..."
TEST_IMAGE=$(docker build -f ../Dockerfile-tests -q ..)

cleanup () {
  # kill and remove any running containers
  docker-compose -f ${HUB_COMPOSE} stop
  docker stop "$TEST_IMAGE"
  docker image rm -f "$TEST_IMAGE"
  printf "$(tput setaf 1)Tests Failed For Unexpected Reasons$(tput setaf 0)\n"
}

# catch unexpected failures, do cleanup and output an error message
trap cleanup EXIT HUP INT QUIT PIPE TERM 0 20

printf "FIRECLOUD LOCATION: $FC_INSTANCE\n"
docker-compose -f ${HUB_COMPOSE} pull
docker-compose -f ${HUB_COMPOSE} up -d --scale chrome=$NUM_NODES

# build and run the composed services
if [ $? -ne 0 ]; then
  printf "$(tput setaf 1)Docker Compose Failed$(tput setaf 0)\n"
  exit -1
fi

# render ctmpls
docker pull broadinstitute/dsde-toolbox:dev
docker run --rm -e VAULT_TOKEN=${VAULT_TOKEN} \
    -e ENVIRONMENT=${ENV} -e ROOT_DIR=/app -v ${WORKING_DIR}:/working \
    -e OUT_PATH=/working/target -e INPUT_PATH=/working -e LOCAL_UI=false \
    broadinstitute/dsde-toolbox:dev render-templates.sh


# run tests
docker run -e FC_INSTANCE=$FC_INSTANCE \
    --net=docker_default \
    -e ENV=$ENV \
    -P --rm -t -e CHROME_URL="http://hub:4444/" ${HOST_MAPPING} \
    -v $WORKING_DIR/target/application.conf:/app/src/test/resources/application.conf \
    -v $WORKING_DIR/target/firecloud-account.pem:/app/src/test/resources/firecloud-account.pem \
    -v $WORKING_DIR/target/users.json:/app/src/test/resources/users.json \
    -v $WORKING_DIR/failure_screenshots:/app/failure_screenshots \
    -v $WORKING_DIR/output:/app/output \
    -v jar-cache:/root/.ivy -v jar-cache:/root/.ivy2 \
    --link docker_hub_1:hub --name ${TEST_CONTAINER} -w /app \
    ${TEST_IMAGE} "${TEST_ENTRYPOINT}"


# Grab exit code
TEST_EXIT_CODE=$?


# inspect the output of the test and display respective message
if [ -z ${TEST_EXIT_CODE+x} ] || [ $TEST_EXIT_CODE -ne 0 ]; then
  printf "$(tput setaf 1)Tests Failed$(tput setaf 0) - Exit Code: $TEST_EXIT_CODE\n"
else
  printf "$(tput setaf 2)Tests Passed$(tput setaf 0)\n"
fi

# call the cleanup fuction
cleanup

# exit the script with the same code as the test service code
exit $TEST_EXIT_CODE
