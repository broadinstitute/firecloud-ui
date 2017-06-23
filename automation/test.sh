#!/usr/bin/env bash

sbt test -Djsse.enableSNIExtension=false -Dheadless=true
sbt clean
