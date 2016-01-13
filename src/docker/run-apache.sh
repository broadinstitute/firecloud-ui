#!/bin/bash
set -euo pipefail
IFS=$'\n\t'


SITE_CONF=$(cat << EOF
ServerAdmin ${SERVER_ADMIN}
ServerName ${SERVER_NAME}

LogFormat "%h %l %u %t \"%r\" %>s %b \"%{Referer}i\" \"%{User-Agent}i\"" combined
LogLevel ${LOG_LEVEL}

Header always append X-Frame-Options SAMEORIGIN
ServerTokens ProductOnly
TraceEnable off

<VirtualHost _default_:${HTTPD_PORT}>
  ErrorLog /dev/stdout
  CustomLog "/dev/stdout" combined

  # Allow for proxying requests to HTTPS endpoints.
  SSLProxyEngine on

  --EXTRA_VHOST_HTTP--
</VirtualHost>

<VirtualHost _default_:${SSL_HTTPD_PORT}>
  ErrorLog /dev/stdout
  CustomLog "/dev/stdout" combined

  SSLEngine on
  SSLProxyEngine on
  SSLProtocol -SSLv3 -TLSv1 -TLSv1.1 +TLSv1.2
  SSLCipherSuite ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-RSA-AES256-GCM-SHA384:DHE-RSA-AES128-GCM-SHA256:DHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-AES128-SHA:ECDHE-ECDSA-AES256-SHA:ECDHE-ECDSA-DES-CBC3-SHA:ECDHE-RSA-AES128-SHA:ECDHE-RSA-AES256-SHA:ECDHE-RSA-DES-CBC3-SHA:DHE-RSA-AES128-SHA:DHE-RSA-AES256-SHA:EDH-RSA-DES-CBC3-SHA:AES128-GCM-SHA256:AES256-GCM-SHA384:AES128-SHA:AES256-SHA:!aNULL:!eNULL:!EXPORT:!DES:!RC4:!3DES:!ADH!MD5:!PSK:!aECDH:!EDH-DSS-DES-CBC3-SHA:!EDH-RSA-DES-CBC3-SHA:!KRB5-DES-CBC3-SHA:!DH
  SSLCertificateFile "/etc/ssl/certs/server.crt"
  SSLCertificateKeyFile "/etc/ssl/private/server.key"
  SSLCertificateChainFile "/etc/ssl/certs/ca-bundle.crt"

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
  <LocationMatch "/service/api/workspaces/[^/]+/[^/]+/entities/[^/]+/tsv">
    RewriteEngine On
    RewriteCond %{HTTP_COOKIE} FCtoken=([^;]+)
    RewriteRule .* - [END,QSD,env=ACCESSTOKEN:%1]
    RequestHeader set Authorization "Bearer %{ACCESSTOKEN}e"
    ProxyPass ${ORCH_URL_ROOT}
    ProxyPassReverse ${ORCH_URL_ROOT}
  </LocationMatch>

  <Location "/service">
    ProxyPass ${ORCH_URL_ROOT}
    ProxyPassReverse ${ORCH_URL_ROOT}
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

echo "$SITE_CONF" > /etc/apache2/sites-available/site.conf
exec /usr/sbin/apachectl -DNO_DETACH -DFOREGROUND 2>&1
