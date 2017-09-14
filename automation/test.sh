#!/usr/bin/env bash

sbt test -Djsse.enableSNIExtension=false -Dheadless=true | tee testout.txt
TEST_EXIT_CODE=$?
sbt clean

cat testout.txt | sed -n -e '/Run completed/,$p' | sed $'s,\x1b\\[[0-9;]*[a-zA-Z],,g' > output/testsummary.txt

if [[ $TEST_EXIT_CODE != 0 ]]; then exit $TEST_EXIT_CODE; fi
