#!/bin/bash

# Make sure config.json, and tcell.js are in the right spot
CONFIG_FILES="/config/config.json /config/tcell.js"
cp -f ${CONFIG_FILES} /var/www/html

if [ "${HTTPS_ONLY}" = 'true' ]; then
    a2dissite site
    a2ensite site-https-only
else
    a2dissite site-https-only
    a2ensite site
fi

if [ "$ENABLE_STACKDRIVER" = "yes" ]; then
    /usr/sbin/a2ensite stackdriver
fi
