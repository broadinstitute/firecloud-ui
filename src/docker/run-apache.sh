#!/bin/bash
set -euo pipefail
IFS=$'\n\t'


SITE_CONF=$(cat << EOF
ServerAdmin ${SERVER_ADMIN}
ServerName ${SERVER_NAME}

LogFormat "%h %l %u %t \"%r\" %>s %b \"%{Referer}i\" \"%{User-Agent}i\"" combined
LogLevel ${LOG_LEVEL}

<VirtualHost _default_:${HTTPD_PORT}>
  ErrorLog /dev/stdout
  CustomLog "/dev/stdout" combined

  # Allow for proxying requests to HTTPS endpoints.
  SSLProxyEngine on

  # Causes Shibboleth to play nice with other authentication schemes.
  ShibCompatValidUser On

  --EXTRA_VHOST_HTTP--
</VirtualHost>

<VirtualHost _default_:${SSL_HTTPD_PORT}>
  ErrorLog /dev/stdout
  CustomLog "/dev/stdout" combined

  SSLEngine on
  SSLProxyEngine on
  SSLCertificateFile "/etc/ssl/certs/server.crt"
  SSLCertificateKeyFile "/etc/ssl/private/server.key"
  SSLCertificateChainFile "/etc/ssl/certs/ca-bundle.crt"

  # Causes Shibboleth to play nice with other authentication schemes.
  ShibCompatValidUser On

  --EXTRA_VHOST_HTTPS--
</VirtualHost>

DocumentRoot /app/target

<Directory "/app/target">
  AllowOverride All
  Order allow,deny
  Allow from all
  Require all granted
</Directory>
EOF
)

LOCATION_DIRECTIVES=$(cat << EOF
  <Location "/service">
    ProxyPass ${ORCH_URL_ROOT}
    ProxyPassReverse ${ORCH_URL_ROOT}
  </Location>

  <Location "/service/api">
    AuthType oauth20
    Require valid-user
    ProxyPass ${ORCH_URL_ROOT}/api
    ProxyPassReverse ${ORCH_URL_ROOT}/api
  </Location>

  <Location "/service/link-nih-account">
    AuthType shibboleth
    ShibRequestSetting requireSession 1
    Require valid-user
  </Location>
EOF
)

if [ "$HTTPS_ONLY" = 'true' ]; then
  SITE_CONF=${SITE_CONF/'--EXTRA_VHOST_HTTP--'/'Redirect / '"https://$SERVER_NAME/"}
else
  SITE_CONF=${SITE_CONF/'--EXTRA_VHOST_HTTP--'/"$LOCATION_DIRECTIVES"}
fi

SITE_CONF=${SITE_CONF/'--EXTRA_VHOST_HTTPS--'/"$LOCATION_DIRECTIVES"}

set -x

# Rebuild index.html with a new GOOGLE_CLIENT_ID since it may have been overridden.
(cd /app && ./script/common/create-index-html.sh release)

echo "$SITE_CONF" > /etc/apache2/sites-available/site.conf
exec /usr/sbin/apachectl -DNO_DETACH -DFOREGROUND 2>&1
