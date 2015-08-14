#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

php "src/static/index.php" $@ > "target/index.html"
