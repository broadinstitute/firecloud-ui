#!/bin/bash
# Include script for perf tests

set -e

checkToken () {
    user=$1

    # Verify that user does not need to refresh their token
    if
        curl \
            -f -v --silent \
            -X GET \
            --header "Accept: application/json" \
            --header "Authorization: Bearer ${ACCESS_TOKEN}" \
            "https://firecloud-orchestration.dsde-${ENV}.broadinstitute.org/api/refresh-token-status" 2>&1 \
        | grep '"requiresRefresh": true'
    then
        echo "$1 needs its refresh token refreshed"
        NEED_TOKEN=true
        export NEED_TOKEN
    fi

}

callbackToNIH() {
    user=$1

    echo "
    Launching calback to NIH for:
    user=$1
    "
    ACCESS_TOKEN=$(
        docker \
            run \
            --rm \
            -v "${WORKING_DIR}:/app/populate" \
            -w /app/populate \
            broadinstitute/dsp-toolbox \
            python get_bearer_token.py "${user}" "${JSON_CREDS}"
    )

    curl \
        -X POST \
        --header "Content-Type: application/json" \
        --header "Accept: application/json" \
        --header "Authorization: Bearer ${ACCESS_TOKEN}" \
        -d "{\"jwt\":\"eyJhbGciOiJIUzI1NiJ9.ZmlyZWNsb3VkLWRldg.NPXbSpTmAOUvJ1HX85TauAARnlMKfqBsPjumCC7zE7s\"}" \
        "https://firecloud-orchestration.dsde-${ENV}.broadinstitute.org/api/nih/callback"
}

launchSubmission() {
    user="$1"
    shift 1
    namespace="$1"
    shift 1
    name="$1"
    shift 1
    methodConfigurationNamespace="$1"
    shift 1
    methodConfigurationName="$1"
    shift 1
    entityType="$1"
    shift 1
    entityName="$1"
    shift 1
    useCallCache="$1"
    shift 1
    deleteIntermediateOutputFiles="$1"
    shift 1

    expression="$1"  #optional

    echo "
    Launching submission for:
        user=${user}
        namespace=${namespace}
        name=${name}
        methodConfigurationNamespace=${methodConfigurationNamespace}
        methodConfigurationName=${methodConfigurationName}
        entityType=${entityType}
        entityName=${entityName}
        useCallCache=${useCallCache}
        deleteIntermediateOutputFiles=${deleteIntermediateOutputFiles}
        expression=${expression}
    "

     ACCESS_TOKEN=$(
        docker \
            run \
            --rm \
            -v "${WORKING_DIR}:/app/populate" \
            -w /app/populate \
            broadinstitute/dsp-toolbox \
            python get_bearer_token.py "${user}" "${JSON_CREDS}"
     )

    # check if $expression is set
    if [[ -z ${expression} ]] ; then
        optionalExpressionField=""
    else
        optionalExpressionField="\"expression\":\"${expression}\","
    fi

    curl \
        -f \
        "https://firecloud-orchestration.dsde-${ENV}.broadinstitute.org/api/workspaces/${namespace}/${name}/submissions" \
        -H "origin: https://firecloud.dsde-${ENV}.broadinstitute.org" \
        -H "accept-encoding: gzip, deflate, br" \
        -H "authorization: Bearer ${ACCESS_TOKEN}" \
        -H "content-type: application/json" \
        --data-binary "
        {
            \"deleteIntermediateOutputFiles\":${deleteIntermediateOutputFiles},
            ${optionalExpressionField}
            \"methodConfigurationNamespace\":\"${methodConfigurationNamespace}\",
            \"methodConfigurationName\":\"${methodConfigurationName}\",
            \"entityType\":\"${entityType}\",
            \"entityName\":\"${entityName}\",
            \"useCallCache\":${useCallCache}
        }
        " \
        --compressed
}

findSubmissionID() {
    user=$1
    namespace=$2
    name=$3

    ACCESS_TOKEN=$(
        docker \
            run \
            --rm \
            -v "${WORKING_DIR}:/app/populate" \
            -w /app/populate \
            broadinstitute/dsp-toolbox \
            python get_bearer_token.py "${user}" "${JSON_CREDS}"
    )

    submissionID=$(
        curl \
            -X GET \
            --header 'Accept: application/json' \
            --header "Authorization: Bearer ${ACCESS_TOKEN}" \
            "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/$namespace/$name/submissions" \
        | jq -r '.[] | select(.status == ("Submitted")) | .submissionId')

    export ACCESS_TOKEN
    export submissionID
}

monitorSubmission() {
    user=$1
    namespace=$2
    name=$3
    submissionId=$4

    ACCESS_TOKEN=$(
        docker \
            run \
            --rm \
            -v "${WORKING_DIR}:/app/populate" \
            -w /app/populate \
            broadinstitute/dsp-toolbox \
            python get_bearer_token.py "${user}" "${JSON_CREDS}"
    )

    # curl -s -X GET --header 'Accept: application/json' --header "Authorization: Bearer $ACCESS_TOKEN" "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/submissions/queueStatus" | jq -r '"\(now),\(.workflowCountsByStatus.Queued),\(.workflowCountsByStatus.Running),\(.workflowCountsByStatus.Submitted)"' | tee -a workflow-progress-$BUILD_NUMBER.csv
    submissionStatus=$(
        curl \
            -X GET \
            --header 'Accept: application/json' \
            --header "Authorization: Bearer ${ACCESS_TOKEN}" \
            "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/${namespace}/${name}/submissions/${submissionId}" \
        | jq -r '.status'
    )
    workflowsStatus=$(
        curl \
            -X GET \
            --header 'Accept: application/json' \
            --header "Authorization: Bearer ${ACCESS_TOKEN}" \
            "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/${namespace}/${name}/submissions/${submissionId}" \
         | jq -r '.workflows[] | .status'
    )
    workflowFailures=$(
        curl \
            -X GET \
            --header 'Accept: application/json' \
            --header "Authorization: Bearer ${ACCESS_TOKEN}" \
            "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/${namespace}/${name}/submissions/${submissionId}" \
         | jq -r '[.workflows[] | select(.status == "Failed")] | length'
    )

    export ACCESS_TOKEN
    export submissionStatus
    export workflowsStatus
    export workflowFailures
}
