#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

ln -snf "$PWD/src/static/assets" "target"
