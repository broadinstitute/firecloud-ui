#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# Performs a release build and removes files from build directory that are unnecessary for
# deployment.

./script/release/build-once.sh
rm -rf target/build

