#!/bin/bash
set -euo pipefail
IFS=$'\n\t'
set -x

php "src/static/index.php" $@ > "target/index.html"
