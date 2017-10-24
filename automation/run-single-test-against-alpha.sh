#!/usr/bin/env bash

set -x

if [[ $# < 1 ]]; then
  echo "usage: ./run-single-test-on-alpha <Spec> <optional test name>"
  exit 1
fi

MODULE="$1"
TEST_TEXT=""

if [[ $# = 2 ]]; then
  TEST_TEXT="-- -z \"$2\""
fi
./render-local-env.sh $PWD $(cat ~/.vault-token) alpha


sbt -Djsse.enableSNIExtension=false -Dheadless=false "testOnly $MODULE $TEST_TEXT"

