#!/usr/bin/env bash

set -e


for i in {1..501}; do
    curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' --header 'Authorization:
    Bearer ya29.GltaBjfPdBbkVXbuYsOiCIcguA4zjyVZJGZ5nqb9q5Gt3qgEHMjolrPSdPkmtHJB8l6tfC5cqJ3Rus3iEf8wNzUifh4VmYZWb47gFvgAxC31FFcmAClaTPTELgSD' -d '{ \
   "namespace": "aj-gatling-tests-1", \
   "name": "GD-2000-WS-noAD-$i", \
   "attributes": {}, \
   "authorizationDomain": [ \
     \
   ] \
   }' 'https://firecloud-orchestration.dsde-perf.broadinstitute.org/api/workspaces'
 done
