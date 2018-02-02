#!/bin/bash
# Single source of truth for building Firecloud-Ui.
# @ Jackie Roberti and Isaac Zarsky
#
# Provide command line options to do one or several things:
#   compile: compile clojure proj into /target
#   -d | --docker : provide arg either "build" or "push", to build and push docker image
# Jenkins build job should run with all options, for example,
#   ./docker/build.sh compile -d push

IFS=$'\n\t'
set -exo pipefail

function clj_build() {
    docker pull broadinstitute/clojure-node
    docker run --rm \
      -w /work \
      -v "$PWD":/work \
      -v maven-cache:/root/.m2 \
      -e npm_config_unsafe_perm="true" \
      broadinstitute/clojure-node \
      bash -c 'lein with-profile deploy do clean, cljsbuild once && npm install && NODE_ENV=production npm run webpack'

}

function docker_cmd()
{
    if [ $DOCKER_CMD = "build" ] || [ $DOCKER_CMD = "push" ]; then
        echo "building $PROJECT docker image..."
        if [ "$ENV" != "dev" ] && [ "$ENV" != "alpha" ] && [ "$ENV" != "staging" ] && [ "$ENV" != "perf" ]; then
            DOCKER_TAG=${BRANCH}
            DOCKER_TAG_TESTS=${BRANCH}
        else
            GIT_SHA=$(git rev-parse origin/${BRANCH})
            echo GIT_SHA=$GIT_SHA > env.properties
            DOCKER_TAG=${GIT_SHA:0:12}
            DOCKER_TAG_TESTS=latest
        fi
        echo "building $PROJECT-tests docker image..."
        docker build -t $REPO:${DOCKER_TAG} .
        cd automation
        docker build -f Dockerfile-tests -t $TESTS_REPO:${DOCKER_TAG_TESTS} .
        cd ..

        if [ $DOCKER_CMD = "push" ]; then
            echo "pushing $PROJECT docker image..."
            docker push $REPO:${DOCKER_TAG}
            echo "pushing $PROJECT-tests docker image..."
            docker push $TESTS_REPO:${DOCKER_TAG_TESTS}
        fi
    else
        echo "Not a valid docker option!  Choose either build or push (which includes build)"
    fi
}

# parse command line options
PROJECT=${PROJECT:-firecloud-ui}
DOCKER_CMD=
BRANCH=${BRANCH:-$(git rev-parse --abbrev-ref HEAD)}  # default to current branch
REPO=${REPO:-broadinstitute/$PROJECT}
TESTS_REPO=$REPO-tests
ENV=${ENV:-""}  # if env is not set, push an image with branch name

while [ "$1" != "" ]; do
    case $1 in
        compile) clj_build ;;
        -d | --docker) shift
                       echo $1
                       DOCKER_CMD=$1
                       docker_cmd
                       ;;
    esac
    shift
done
