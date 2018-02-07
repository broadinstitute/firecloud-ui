#!/usr/bin/env bash
set -e

## Run from automation/
## Clones the firecloud-automated-testing repo, pulls templatized configs, and renders them to src/test/resources

# Defaults
WORKING_DIR=$PWD
VAULT_TOKEN=$(cat ~/.vault-token)
FIRECLOUD_AUTOMATED_TESTING_BRANCH=master
ENV=dev
LOCAL_UI=false
SERVICE=firecloud-ui

# Parameters
WORKING_DIR=${1:-$WORKING_DIR}
VAULT_TOKEN=${2:-$VAULT_TOKEN}
FIRECLOUD_AUTOMATED_TESTING_BRANCH=${3:-$FIRECLOUD_AUTOMATED_TESTING_BRANCH}
ENV=${3:-$ENV}
if [ "$4" = "local_ui" ]; then
  LOCAL_UI=true 
fi

confirm () {
    # call with a prompt string or use a default
    read -r -p "${1:-Are you sure?} [y/N] " response
    case $response in
        [yY])
            shift
            $@
            ;;
        *)
            ;;
    esac
}

# clone the firecloud-automated-testing repo
clone_repo() {
    original_dir=$PWD
    if [[ $PWD == *"${SERVICE}"* ]]
        then
        cd ..
    fi

    echo "Currently in" $PWD
    confirm "OK to clone here?" git clone https://github.com/broadinstitute/firecloud-automated-testing.git

    cd $original_dir
}

pull_configs() {
    original_dir=$WORKING_DIR
    if [[ $PWD == *"${SERVICE}"* ]]; then
        cd ../..
    fi
    cd firecloud-automated-testing
    echo $PWD
    git pull
    git checkout ${FIRECLOUD_AUTOMATED_TESTING_BRANCH}
    cd $original_dir
}

render_configs() {
    original_dir=$WORKING_DIR
    if [[ $PWD == *"${SERVICE}"* ]]; then
        cd ../..
    fi
    docker pull broadinstitute/dsde-toolbox:dev
    docker run -it --rm -e VAULT_TOKEN=${VAULT_TOKEN} \
        -e ENVIRONMENT=${ENV} -e ROOT_DIR=${WORKING_DIR} -v $PWD/firecloud-automated-testing/configs:/input -v $PWD/$SERVICE/automation:/output \
        -e OUT_PATH=/output/src/test/resources -e INPUT_PATH=/input -e LOCAL_UI=$LOCAL_UI \
        broadinstitute/dsde-toolbox:dev render-templates.sh

    # pull service-specific application.conf
    docker run -it --rm -e VAULT_TOKEN=${VAULT_TOKEN} \
        -e ENVIRONMENT=${ENV} -e ROOT_DIR=${WORKING_DIR} -v $PWD/firecloud-automated-testing/configs/$SERVICE:/input -v $PWD/$SERVICE/automation:/output \
        -e OUT_PATH=/output/src/test/resources -e INPUT_PATH=/input -e LOCAL_UI=$LOCAL_UI \
        broadinstitute/dsde-toolbox:dev render-templates.sh
    cd $original_dir
}

confirm "Clone firecloud-automated-testing repo?" clone_repo
pull_configs
render_configs
