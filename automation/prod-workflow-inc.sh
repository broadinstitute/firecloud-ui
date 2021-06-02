#!/usr/bin/env bash

# Script to start complex workflow test in Production

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

    # Verify that user is authorized to make the API call and does not need to refresh their token
    if
        curl \
            -f -v --silent \
            -X GET \
            --header "Accept: application/json" \
            --header "Authorization: Bearer ${ACCESS_TOKEN}" \
            "https://api.firecloud.org/api/refresh-token-status" 2>&1 \
        | grep '"requiresRefresh": true\|error: 401'
    then
        NEED_TOKEN=true
        export NEED_TOKEN
    fi
}

getAccessToken() {
  user=$1

  if [ "${ACCESS_TOKEN_USER-}" = "${user}" -a -n "${ACCESS_TOKEN-}" ]
  then
    checkToken "$user"
  else
    NEED_TOKEN=true
  fi

  if [ "${NEED_TOKEN}" = "true" ]
  then
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
  fi

  export ACCESS_TOKEN
  export ACCESS_TOKEN_USER="${user}"
  export NEED_TOKEN=false
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

    submissionDetails=$(
        curl \
            -X POST \
            "https://api.firecloud.org/api/workspaces/${namespace}/${name}/submissions" \
            -H "origin: https://portal.firecloud.org" \
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
            --compressed)
    submissionId=$(jq -r '.submissionId' <<< "${submissionDetails}")
}

monitorSubmission() {
    user=$1
    namespace=$2
    name=$3
    submissionId=$4

    getAccessToken "$user"

    printf "\nFetching status for submission ID '%s':" "${submissionId}"

    submissionDetails=$(curl \
            -X GET \
            --header 'Accept: application/json' \
            --header "Authorization: Bearer ${ACCESS_TOKEN}" \
            "https://api.firecloud.org/api/workspaces/$namespace/$name/submissions/$submissionId")

    submissionStatus=$(jq -r '.status' <<< "${submissionDetails}")
    workflowsStatus=$(jq -r '.workflows[] | .status' <<< "${submissionDetails}")


    export submissionStatus
    export workflowsStatus
}
