FROM broadinstitute/openidc-baseimage:1.8.5

WORKDIR /app
COPY target /app/target

COPY src/docker/run-apache.sh /etc/service/apache2/run

# openidc-baseimage requires this unused variable.
ENV CALLBACK_URI=http://example.com/

EXPOSE 80
EXPOSE 443

ENV HTTPD_PORT=80 SSL_HTTPD_PORT=443
ENV SERVER_ADMIN=devops@broadinstitute.org
ENV LOG_LEVEL=warn
ENV ORCH_URL_ROOT=FIXME

# Override in development since figwheel does not support secure websockets.
ENV HTTPS_ONLY=true

# TODO(dmohs): openidc-baseimage warns about these undefined environment variables, though they are
# not used in our site.conf.
# - CLIENTID
# - CLIENTSECRET
# - OIDC_SCOPES
# - OIDC_COOKIE
# - CLIENTID
# - CLIENTSECRET
