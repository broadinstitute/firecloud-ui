#!/bin/bash
# Simple script to start perf test in alpha
echo "Starting Perf test in alpha"
echo "FOR EASE OF USE: IF YOU RUN THIS SCRIPT AND A BROWSER OPENS WITH GOOGLE LOGIN, KILL THE SCRIPT AND GCLOUD AUTH INTO EACH USER FIRST (see script comments)"

# Log in with each user:
# gcloud auth login dominique.testerson@gmail.com
# gcloud auth login gary.testerson1@gmail.com
# gcloud auth login felicity.testerson@gmail.com
# gcloud auth login frida.testerson@gmail.com
# gcloud auth login elvin.testerson@gmail.com
# gcloud auth login test.firec@gmail.com


echo "Logging in with dominique.testerson@gmail.com"
gcloud auth login dominique.testerson@gmail.com
ACCESS_TOKEN=$(gcloud auth print-access-token)
curl 'https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/perf-test-a/Perf-test-A-workspace/submissions' -H 'origin: https://firecloud.dsde-alpha.broadinstitute.org' -H 'accept-encoding: gzip, deflate, br' -H "authorization: Bearer $ACCESS_TOKEN" -H 'content-type: application/json' --data-binary '{"methodConfigurationNamespace":"qamethods","methodConfigurationName":"sleep1hr_echo_strings","entityType":"sample_set","entityName":"sample_set6k","useCallCache":false,"expression":"this.samples"}' --compressed

echo "Logging in with gary.testerson1@gmail.com"
gcloud auth login gary.testerson1@gmail.com
ACCESS_TOKEN=$(gcloud auth print-access-token)
curl 'https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/perf-test-b/Perf-Test-B-W/submissions' -H 'origin: https://firecloud.dsde-alpha.broadinstitute.org' -H 'accept-encoding: gzip, deflate, br' -H "authorization: Bearer $ACCESS_TOKEN" -H 'content-type: application/json' --data-binary '{"methodConfigurationNamespace":"alex_methods","methodConfigurationName":"sleep_echo_strings","entityType":"sample_set","entityName":"sample_set6k","useCallCache":false,"expression":"this.samples"}' --compressed

echo "Logging in with felicity.testerson@gmail.com"
gcloud auth login felicity.testerson@gmail.com
ACCESS_TOKEN=$(gcloud auth print-access-token)
curl 'https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/perf-test-d/Perf-Test-D-W_copy/submissions' -H 'origin: https://firecloud.dsde-alpha.broadinstitute.org' -H 'accept-encoding: gzip, deflate, br' -H "authorization: Bearer $ACCESS_TOKEN" -H 'content-type: application/json' --data-binary '{"methodConfigurationNamespace":"alex_methods","methodConfigurationName":"sleep_echo_strings","entityType":"sample_set","entityName":"sample_set6k","useCallCache":false,"expression":"this.samples"}' --compressed

echo "Logging in with frida.testerson@gmail.com"
gcloud auth login frida.testerson@gmail.com
ACCESS_TOKEN=$(gcloud auth print-access-token)
curl 'https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/perf-test-e/Perf-Test_E_W/submissions' -H 'origin: https://firecloud.dsde-alpha.broadinstitute.org' -H 'accept-encoding: gzip, deflate, br' -H "authorization: Bearer $ACCESS_TOKEN" -H 'content-type: application/json' --data-binary '{"methodConfigurationNamespace":"qamethods","methodConfigurationName":"sleep1hr_echo_strings","entityType":"sample_set","entityName":"sample_set6k","useCallCache":false,"expression":"this.samples"}' --compressed

echo "Logging in with elvin.testerson@gmail.com"
gcloud auth login elvin.testerson@gmail.com
ACCESS_TOKEN=$(gcloud auth print-access-token)
curl 'https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/aa-test041417/Perf-Test-G-W/submissions' -H 'origin: https://firecloud.dsde-alpha.broadinstitute.org' -H 'accept-encoding: gzip, deflate, br' -H "authorization: Bearer $ACCESS_TOKEN" -H 'content-type: application/json' --data-binary '{"methodConfigurationNamespace":"alex_methods","methodConfigurationName":"sleep_echo_strings","entityType":"sample_set","entityName":"sample_set6k","useCallCache":false,"expression":"this.samples"}' --compressed

echo "Logging in with test.firec@gmail.com"
gcloud auth login test.firec@gmail.com
ACCESS_TOKEN=$(gcloud auth print-access-token)
curl 'https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/aa-test-042717a/test-042717/submissions' -H 'origin: https://firecloud.dsde-alpha.broadinstitute.org' -H 'accept-encoding: gzip, deflate, br' -H "authorization: Bearer $ACCESS_TOKEN" -H 'content-type: application/json' --data-binary '{"methodConfigurationNamespace":"anuMethods","methodConfigurationName":"callCacheWDL","entityType":"participant","entityName":"subject_HCC1143","useCallCache":true}' --compressed


echo "Done"