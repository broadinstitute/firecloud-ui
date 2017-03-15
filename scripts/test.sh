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

docker volume create --name=jar-cache
docker volume create --name=node-modules

docker-compose -p fcuitests -f scripts/.tests-compose.yaml up -d

docker cp project.clj fcuitests_clojure_1:/w
docker cp src fcuitests_clojure_1:/w
docker cp webpack.config.js fcuitests_clojure_1:/w

compose_exec clojure lein with-profile deploy npm install
compose_exec clojure ./node_modules/webpack/bin/webpack.js -p
compose_exec clojure lein cljsbuild once
compose_exec clojure lein resource

docker cp scripts/.phantom-run-tests.js fcuitests_clojure_1:/w/run-tests.js
compose_exec phantomjs phantomjs run-tests.js
