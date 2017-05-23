#!/bin/bash
set -euxo pipefail
IFS=$'\n\t'

clean_up () {
  docker-compose -p fcuitests -f scripts/.tests-compose.yaml down -v
}

compose_exec() {
  docker-compose -p fcuitests -f scripts/.tests-compose.yaml exec $@
}

trap clean_up EXIT HUP INT QUIT PIPE TERM 0 20

docker-compose -p fcuitests -f scripts/.tests-compose.yaml pull

docker volume create --name=jar-cache
docker volume create --name=node-modules

docker-compose -p fcuitests -f scripts/.tests-compose.yaml up -d

docker cp project.clj fcuitests_clojure-node_1:/w
docker cp src fcuitests_clojure-node_1:/w
docker cp webpack.config.js fcuitests_clojure-node_1:/w
docker cp package.json fcuitests_clojure-node_1:/w

compose_exec clojure-node npm install
compose_exec clojure-node npm run webpack -- -p
compose_exec clojure-node sh -c 'lein cljsbuild once 2>&1 | tee /tmp/cljsbuild.log'
set +e
compose_exec clojure-node grep WARNING /tmp/cljsbuild.log
if [[ $? -eq 0 ]]; then
  exit 1
fi
set -e
compose_exec clojure-node lein resource

docker cp scripts/.phantom-run-tests.js fcuitests_clojure-node_1:/w/run-tests.js

set +x
echo "======================================="
set -x
compose_exec phantomjs phantomjs run-tests.js
set +x
echo "======================================="
