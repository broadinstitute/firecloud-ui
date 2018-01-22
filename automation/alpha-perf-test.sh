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

launchSubmission() {
    user=$1
    namespace=$2
    name=$3
    methodConfigurationNamespace=$4
    methodConfigurationName=$5
    entityType=$6
    entityName=$7
    useCallCache=$8
    expression=$9  #optional

    echo "
    Launching submission for:
        user=$1
        namespace=$2
        name=$3
        methodConfigurationNamespace=$4
        methodConfigurationName=$5
        entityType=$6
        entityName=$7
        useCallCache=$8
        expression=$9
    "

    gcloud auth login $user
    ACCESS_TOKEN=$(gcloud auth print-access-token)

    #check if $9 is set for 'expression'
    if [ -z ${9+x} ] ; then
        curl "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/$namespace/$name/submissions" -H "origin: https://firecloud.dsde-alpha.broadinstitute.org" -H "accept-encoding: gzip, deflate, br" -H "authorization: Bearer $ACCESS_TOKEN" -H "content-type: application/json" --data-binary "{\"methodConfigurationNamespace\":\"$methodConfigurationNamespace\",\"methodConfigurationName\":\"$methodConfigurationName\",\"entityType\":\"$entityType\",\"entityName\":\"$entityName\",\"useCallCache\":$useCallCache}" --compressed
    else
        curl "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/$namespace/$name/submissions" -H "origin: https://firecloud.dsde-alpha.broadinstitute.org" -H "accept-encoding: gzip, deflate, br" -H "authorization: Bearer $ACCESS_TOKEN" -H "content-type: application/json" --data-binary "{\"methodConfigurationNamespace\":\"$methodConfigurationNamespace\",\"methodConfigurationName\":\"$methodConfigurationName\",\"entityType\":\"$entityType\",\"entityName\":\"$entityName\",\"useCallCache\":$useCallCache,\"expression\":\"$expression\"}" --compressed
    fi
}

launchSubmission dominique.testerson@gmail.com perf-test-a Perf-test-A-workspace qamethods sleep1hr_echo_strings sample_set sample_set6k false "this.samples"
launchSubmission gary.testerson1@gmail.com perf-test-b Perf-Test-B-W alex_methods sleep_echo_strings sample_set sample_set6k false "this.samples"
launchSubmission felicity.testerson@gmail.com perf-test-d Perf-Test-D-W_copy alex_methods sleep_echo_strings sample_set sample_set6k false "this.samples"
launchSubmission frida.testerson@gmail.com perf-test-e Perf-Test_E_W qamethods sleep1hr_echo_strings sample_set sample_set6k false "this.samples"
launchSubmission elvin.testerson@gmail.com aa-test041417 Perf-Test-G-W alex_methods sleep_echo_strings sample_set sample_set6k false "this.samples"
launchSubmission test.firec@gmail.com aa-test-042717a test-042717 anuMethods callCacheWDL participant subject_HCC1143 true

printf "\nDone"
