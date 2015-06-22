#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

mkdir -p target
# Instead of removing the directory, we remove all files in it. This ensures that if there is
# already something using the directory (e.g., a web server serving files from it) it doesn't get
# screwed up.
rm -rf target/*
