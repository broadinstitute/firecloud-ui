#!/usr/bin/env bash

sbt test -Dlocal.browser=true -Djsse.enableSNIExtension=false
sbt clean