#!/bin/bash

set -x

hash fswatch 2>/dev/null || {
    echo >&2 "This script requires fswatch (https://github.com/emcrisostomo/fswatch), but it's not installed. On Darwin, just \"brew install fswatch\".  Aborting."; exit 1;
}

if [ -a ./.docker-rsync-local.pid ]; then
    echo "Looks like clean-up wasn't completed, doing it now..."
    docker rm -f ui-rsync-container
    docker rm -f ui-mock-server
    docker rm -f ui-clojure
    docker rm -f ui-webpack
    pkill -P $(< "./.docker-rsync-local.pid")
    rm ./.docker-rsync-local.pid
    rm ./.docker-rsync-local.log
fi

clean_up () {
    echo
    echo "Cleaning up after myself..."
    docker rm -f ui-mock-server
    docker rm -f ui-rsync-container
    docker rm -f ui-webpack
    pkill -P $$
    rm ./.docker-rsync-local.pid
    rm ./.docker-rsync-local.log
}
trap clean_up EXIT HUP INT QUIT PIPE TERM 0 20

echo $$ > ./.docker-rsync-local.pid
# Truncate the log (rather than delete) in case someone is tailing it.
> .docker-rsync-local.log

echo "Launching mock server..."

DOCKERHOST=$(docker network inspect bridge -f='{{(index .IPAM.Config 0).Gateway}}')

if [[ -z $DOCKERHOST ]]; then
    echo "IPAM.Config.Gateway not found.  The Docker daemon may need a reload."
    echo "See https://github.com/moby/moby/issues/26799 for details."
    exit 2
fi

docker run -d \
  --name ui-mock-server \
  -p 8080:8080 \
  --add-host local.broadinstitute.org:$DOCKERHOST \
  rodolpheche/wiremock:2.13.0-alpine

echo "Launching rsync container..."
docker run -d \
    --name ui-rsync-container \
    -v ui-shared-source:/working \
    -e DAEMON=docker \
    tjamet/rsync

sync_src () {
    rsync --blocking-io -azlv --delete -e "docker exec -i" . ui-rsync-container:working \
        --filter='+ /project.clj' \
        --filter='+ /src/***' \
        --filter='+ webpack.config.js' \
        --filter='+ package.json' \
        --filter='+ package-lock.json' \
        --filter='- *' \
        >> ./.docker-rsync-local.log
}

echo "Performing initial file sync..."
sync_src; fswatch -o project.clj src/ | while read f; do sync_src; done &
ENV="${ENV:-}"
docker exec ui-rsync-container mkdir -p /working/resources/public
if [[ "$ENV" = 'qa' ]]; then
      docker cp config/config.json.qa ui-rsync-container:/working/resources/public/config.json
else
      docker cp config/config.json ui-rsync-container:/working/resources/public/config.json
fi
docker cp config/tcell.js ui-rsync-container:/working/resources/public/tcell.js

echo "Standing up webpack watcher..."
docker pull broadinstitute/clojure-node:latest
docker run -d \
      --name ui-webpack \
      -w /work -v ui-shared-source:/work -v node-modules:/work/node_modules \
      -e npm_config_unsafe_perm='true' \
      broadinstitute/clojure-node \
      bash -c 'npm install && npm run webpack -- --watch'

echo "Configuring mock server..."
LOCAL_ORCH="${LOCAL_ORCH:-}"
FIAB="${FIAB:-}"
if [[ "$LOCAL_ORCH" = 'true' ]]; then
    echo "Running with local orch"
    curl --data-binary @config/wiremock-proxy-local.json http://localhost:8080/__admin/mappings/new
elif [[ "$FIAB" = 'true' ]]; then
    if [[ "$ENV" = 'qa' ]]; then
        echo "Running with qa FiaB orch"
        curl --data-binary @config/wiremock-proxy-fiab-qa.json http://localhost:8080/__admin/mappings/new
    else
        echo "Running with dev FiaB orch"
        curl --data-binary @config/wiremock-proxy-fiab-dev.json http://localhost:8080/__admin/mappings/new
    fi
else
    echo "Running with dev orch"
    curl --data-binary @config/wiremock-proxy-dev.json http://localhost:8080/__admin/mappings/new
fi
echo
echo
echo "Mock server is running. Mock requests like so:"
echo
echo "  ./scripts/addmockresponse.sh src/wiremock/billing-pending.json"
echo
echo

start_server () {
    # Starts figwheel so changes are immediately loaded into the running browser window and appear
    # without requiring a page reload.

    echo "Checking if port 80 is free..."
    set +e
    if docker run --rm -p 80:3449 broadinstitute/clojure-node true; then
      PORT_80_FREE=true
    else
      PORT_80_FREE=false
    fi
    set -e

    if "$PORT_80_FREE"; then
      EXPOSED_PORTS=(-p 80:3449 -p 3449:3449)
    else
      EXPOSED_PORTS=(-p 3449:3449)
    fi

    echo "Launching Clojure docker container..."
    docker run --rm -it \
      --name ui-clojure \
      -w /work -v ui-shared-source:/work -v jar-cache:/root/.m2 \
      ${EXPOSED_PORTS[*]} \
      broadinstitute/clojure-node \
      bash -c 'sleep 1; rlwrap lein figwheel'
}
start_server
