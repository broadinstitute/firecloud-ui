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
    docker run --rm \
      -w /work \
      -v "$PWD":/work \
      -v maven-cache:/root/.m2 \
      -e npm_config_unsafe_perm="true" \
      broadinstitute/clojure-node \
      bash -c 'lein with-profile deploy do clean, resource, cljsbuild once && npm install && NODE_ENV=production npm run webpack'

}

function docker_cmd()
{
    DOCKER_CMD=$1
    REPO=broadinstitute/$PROJECT
    if [ $DOCKER_CMD = "build" ] || [ $DOCKER_CMD = "push" ]; then
        echo "building docker image..."
        GIT_SHA=$(git rev-parse ${GIT_BRANCH})
        echo GIT_SHA=$GIT_SHA > env.properties  # for jenkins jobs
        docker build  -t $REPO:${GIT_SHA:0:12} .

        if [ $DOCKER_CMD = "push" ]; then
            echo "pushing docker image..."
            docker push $REPO:${GIT_SHA:0:12}
        fi
    else
        echo "Not a valid docker option!  Choose either build or push (which includes build)"
    fi
}

# parse command line options
PROJECT=${PROJECT:-firecloud-ui}
GIT_BRANCH=${GIT_BRANCH:-$(git rev-parse --abbrev-ref HEAD)}  # default to current branch

while [ "$1" != "" ]; do
    case $1 in
        compile) clj_build ;;
        -d | --docker) shift
                       docker_cmd $1
                       ;;
    esac
    shift
done
