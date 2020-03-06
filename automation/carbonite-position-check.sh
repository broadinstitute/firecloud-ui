#!/bin/bash

set -e

VAULT_TOKEN=${1:-$(cat $HOME/.vault-token)}
WORKING_DIR=$PWD

JSON_CREDS=`docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN -e VAULT_ADDR=https://clotho.broadinstitute.org:8200 broadinstitute/dsde-toolbox vault read -format=json secret/dsde/firecloud/dev/common/firecloud-account.json | jq '.data'`

# Substantially all workflows on alpha were run by the `PerformanceTest-against-Alpha` job, which uses the Harry Potter users.
# The two target workflows happen to have been run by Hermione, so use her token to inspect metadata.
ACCESS_TOKEN=$(
        docker \
            run \
            --rm \
            -v "${WORKING_DIR}:/app/populate" \
            -w /app/populate \
            broadinstitute/dsp-toolbox \
            python get_bearer_token.py "hermione.owner@test.firecloud.org" "${JSON_CREDS}"
     )

# The archiving start position is set by config [0] as `services.MetadataService.config.carbonite-metadata-service.minimum-summary-entry-id`
# [0] https://github.com/broadinstitute/firecloud-develop/blob/dev/base-configs/cromwell/cromwell.conf.ctmpl#L458-L469

# The last unarchived workflow, position 91824073
curl \
  -f \
  -X GET \
  -H 'Accept: application/json' \
  -H "authorization: Bearer ${ACCESS_TOKEN}" \
  'https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workflows/v1/c4414fa5-4268-41f4-ad0f-eebb0ba358b2/metadata?expandSubWorkflows=false' | jq . > unarchived.json

grep "\"metadataSource\": \"Unarchived\"" unarchived.json

# The first archived workflow, position 91824074
curl \
  -f \
  -X GET \
  -H 'Accept: application/json' \
  -H "authorization: Bearer ${ACCESS_TOKEN}" \
  'https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workflows/v1/91d695f3-3326-4478-89bb-39813b1fb98c/metadata?expandSubWorkflows=false' | jq . > archived.json

# grep "\"metadataSource\": \"Archived\"" archived.json # Uncomment once alpha starts carboniting
