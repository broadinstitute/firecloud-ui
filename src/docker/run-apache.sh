#!/bin/sh

if [ "$HTTPS_ONLY" = 'true' ]; then
  export OPTIONAL_HTTP_REDIRECT_URL="Redirect / https://$SERVER_NAME/"
else
  export OPTIONAL_HTTP_REDIRECT_URL=
fi

# Rebuild index.html with a new GOOGLE_CLIENT_ID since it may have been overridden.
(cd /app && ./script/common/create-index-html.sh release)

exec /usr/sbin/apachectl -DNO_DETACH -DFOREGROUND 2>&1
