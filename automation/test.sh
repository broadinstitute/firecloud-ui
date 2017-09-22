#!/usr/bin/env bash

set -o pipefail

sbt test -Djsse.enableSNIExtension=false -Dheadless=true | tee testout.txt
TEST_EXIT_CODE=$?
sbt clean

cat testout.txt | sed -n -e '/Run completed/,$p' | sed $'s,\x1b\\[[0-9;]*[a-zA-Z],,g' > output/testsummary.txt

curl -F file=@output/testsummary.txt -F channels=#${TEST_CHANNEL} -F token=${SLACK_API_TOKEN} -F filename=fiab-test-${BUILD_NUMBER} https://slack.com/api/files.upload


if [[ $TEST_EXIT_CODE != 0 ]]; then exit $TEST_EXIT_CODE; fi
