#!/bin/sh

# If this directory does not exist, shibd silently fail to create the listener socket.
mkdir -p /var/run/shibboleth

shib-keygen -f -e "https://$SERVER_NAME/shibboleth"

exec /usr/sbin/shibd -F -f
