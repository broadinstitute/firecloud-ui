#!/usr/bin/env bash

sbt test -Djsse.enableSNIExtension=false -Dheadless=true
TEST_EXIT_CODE=$?
sbt clean

if [[ $TEST_EXIT_CODE != 0 ]]; then exit $TEST_EXIT_CODE; fi
