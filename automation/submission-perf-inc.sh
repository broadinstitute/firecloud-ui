#!/bin/bash
# Include script for perf tests

set -e
set -x

checkToken () {
    user=$1

    # Get access token if it hasn't been initialized. This usually happens during the start of a test.
    # For more details see https://broadworkbench.atlassian.net/browse/BW-721
    if [ -z "${ACCESS_TOKEN-}" ]
    then
      getAccessToken "$user"
    fi

    tokenStatus=$(
      curl \
        -v --silent \
        -X GET \
        --header "Accept: application/json" \
        --header "Authorization: Bearer ${ACCESS_TOKEN}" \
        "https://firecloud-orchestration.dsde-${ENV}.broadinstitute.org/api/refresh-token-status" 2>&1
    )

    # Verify that user is authorized to make the API call and does not need to refresh their token
    if [[ "${tokenStatus}" =~ '"requiresRefresh":true' ]] || [[ ${tokenStatus} =~ "401 Unauthorized" ]]
    then
        export NEED_TOKEN=true
    else if [ "$(( $(date +%s) - ${TOKEN_CREATION_TIME-0} ))" -gt "1800" ]
        export NEED_TOKEN=true
    else
        export NEED_TOKEN=false
    fi
}

getAccessToken() {
  user=$1

  # This checks that we are getting an access token for the same user as before. If the user changed
  # we will get a new access token
  if [ "${ACCESS_TOKEN_USER-}" = "${user}" -a -n "${ACCESS_TOKEN-}" ]
  then
    checkToken "$user"
  else
    NEED_TOKEN=true
  fi

  while [ "${NEED_TOKEN}" = "true" ]
  do
    printf "\nRetrieving new ACCESS_TOKEN for user '%s'" "${user}"
    ACCESS_TOKEN=$(
      docker \
        run \
        --rm \
        -v "${WORKING_DIR}:/app/populate" \
        -w /app/populate \
        broadinstitute/dsp-toolbox \
        python get_bearer_token.py "${user}" "${JSON_CREDS}"
    )
    export ACCESS_TOKEN
    export ACCESS_TOKEN_USER="${user}"
    export TOKEN_CREATION_TIME=$(date +%s)
    checkToken()
  done
}

callbackToNIH() {
    user=$1

    getAccessToken "$user"

    echo "
    Launching callback to NIH for:
    user=$1
    "

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
    entityType="$1" #optional
    shift 1
    entityName="$1" #optional
    shift 1
    useCallCache="$1"
    shift 1
    deleteIntermediateOutputFiles="$1"
    shift 1

    expression="$1" #optional

    getAccessToken "$user"

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

    # check if $expression is set
    if [[ -z ${expression} ]] ; then
        optionalExpressionField=""
    else
        optionalExpressionField="\"expression\":\"${expression}\","
    fi
    if [[ -z ${entityType} ]] ; then
        optionalEntityTypeField=""
    else
        optionalEntityTypeField="\"entityType\":\"${entityType}\","
    fi
    if [[ -z ${entityName} ]] ; then
        optionalEntityNameField=""
    else
        optionalEntityNameField="\"entityName\":\"${entityName}\","
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
            ${optionalEntityTypeField}
            ${optionalEntityNameField}
            \"useCallCache\":${useCallCache}
        }
        " \
        --compressed
}

findSubmissionID() {
    user=$1
    namespace=$2
    name=$3
    methodConfigurationNamespace=${4-} # optional
    methodConfigurationName=${5-}      # optional

    getAccessToken "$user"

    selectorString=".status == (\"Submitted\")"
    if [[ -n "$methodConfigurationNamespace" && -n "$methodConfigurationName" ]]
        then
            printf "\nFetching last submission ID for workspace '%s' in namespace '%s':" "${name}" "${namespace}"
            selectorString="$selectorString and .methodConfigurationNamespace == (\"$methodConfigurationNamespace\") and .methodConfigurationName == (\"$methodConfigurationName\")"
    else
            printf "\nFetching submission ID for workspace '%s' in namespace '%s':" "${name}" "${namespace}"
    fi
    submissionID=$(
        curl \
            -X GET \
            --header 'Accept: application/json' \
            --header "Authorization: Bearer ${ACCESS_TOKEN}" \
            "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/$namespace/$name/submissions" \
        | jq -r "[.[] | select($selectorString)] | sort_by(.submissionDate) | reverse[0] | .submissionId")

    export submissionID
}

findFirstWorkflowIdInSubmission() {
    user=$1
    namespace=$2
    name=$3
    submissionId=$4

    getAccessToken "$user"

    printf "\nFetching first workflow ID for submission ID '%s':" "${submissionId}"

    workflowID=$(
        curl \
            -X GET \
            --header 'Accept: application/json' \
            --header "Authorization: Bearer ${ACCESS_TOKEN}" \
            "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/$namespace/$name/submissions/$submissionId" \
        | jq -r '.workflows[0].workflowId')

    export workflowID
}

checkIfWorkflowErrorMessageContainsSubstring() {
    user=$1
    namespace=$2
    name=$3
    workflowId=$4
    expectedSubstring=$5

    getAccessToken "$user"

    printf "\nChecking if workflow contains expected error string:"

    workflowErrorMessage=$(
        curl \
            -X GET \
            --header 'Accept: application/json' \
            --header "Authorization: Bearer ${ACCESS_TOKEN}" \
            "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workflows/v1/$workflowId/metadata?includeKey=failures&expandSubWorkflows=false" \
        | jq -r '.failures')

    if [[ "$workflowErrorMessage" != *"$expectedSubstring"* ]]; then
      echo "Workflow error message doesn't contain expected text: $expectedSubstring for workflow $workflowId. Actual error message: $workflowErrorMessage"
      exit 1
    fi
}

monitorSubmission() {
    user=$1
    namespace=$2
    name=$3
    submissionId=$4

    getAccessToken "$user"

    printf "\nFetching status for submission ID '%s':" "${submissionId}"

    submissionDetails=$(
        curl \
            -X GET \
            --header 'Accept: application/json' \
            --header "Authorization: Bearer ${ACCESS_TOKEN}" \
            "https://firecloud-orchestration.dsde-${ENV}.broadinstitute.org/api/workspaces/${namespace}/${name}/submissions/${submissionId}")

    export submissionStatus=$(echo "$submissionDetails" | jq -r '.status')
    export workflowsStatus=$(echo "$submissionDetails" | jq -r '.workflows[] | .status')
    export workflowFailures=$(echo "$submissionDetails" | jq -r '[.workflows[] | select(.status == "Failed")] | length')
}

waitForSubmissionAndWorkflowStatus() {
  numOfAttempts=$1
  expectedSubmissionStatus=$2
  expectedWorkflowStatus=$3
  user=$4
  namespace=$5
  name=$6
  submissionId=$7

  monitorSubmission $user $namespace $name $submissionId
  i=1
  while [ "$submissionStatus" != "$expectedSubmissionStatus" ] && [ "$i" -le "$numOfAttempts" ]
    do
      echo "Submission $submissionId is not yet $expectedSubmissionStatus (got: ${submissionStatus}). Will make attempt $(($i+1)) after 60 seconds"
      sleep 60
      monitorSubmission $user $namespace $name $submissionId
      ((i++))
    done

  if [ "$submissionStatus" == "$expectedSubmissionStatus" ] && [ "$workflowsStatus" == "$expectedWorkflowStatus" ]; then
    echo "Workflow finished within $i minutes with expected status: $workflowsStatus"
    echo "$workflowFailures"
    echo "${submissionStatus}" "${workflowsStatus}" > submissionResults.txt 2>&1
  else
    if [ "$i" -gt "$numOfAttempts" ]; then
      echo "Failed to reach expected submission/workflow $expectedSubmissionStatus/$expectedWorkflowStatus statuses within $i minutes. Last submission/workflow statuses: ${submissionStatus}/${workflowsStatus}"
    else
      echo "Failing with submission status: $submissionStatus and workflow status: $workflowsStatus"
    fi
    echo "${submissionStatus}" "${workflowsStatus}" > submissionResults.txt 2>&1
    exit 1
  fi
}
