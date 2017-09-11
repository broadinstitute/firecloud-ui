#!/usr/bin/env bash

sbt 'testOnly *.MethodConfigSpec -- -z "launch analysis button should be disabled and show error if clicked"' -Djsse.enableSNIExtension=false -Dheadless=true
TEST_EXIT_CODE=$?
sbt clean

if [[ $TEST_EXIT_CODE != 0 ]]; then exit $TEST_EXIT_CODE; fi
