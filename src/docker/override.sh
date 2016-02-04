#!/bin/bash

# Make sure ORCH_URL_ROOT is set
if [ -z "$ORCH_URL_ROOT" ] ; then
    echo "ORCH_URL_ROOT not set.  Exiting!!!"
    exit 1
fi

if [ "${HTTPS_ONLY}" = 'true' ]; then
    a2dissite site
    a2ensite site-https-only
else
    a2dissite site-https-only
    a2ensite site
fi
