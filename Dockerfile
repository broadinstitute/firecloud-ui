FROM broadinstitute/openidc-baseimage

# How to install OpenJDK 8 from:
# http://ubuntuhandbook.org/index.php/2015/01/install-openjdk-8-ubuntu-14-04-12-04-lts/
RUN add-apt-repository ppa:openjdk-r/ppa

RUN apt-get update --fix-missing
RUN apt-get install -qy openjdk-8-jdk php5-cli rlfe

# Standard apt-get cleanup.
RUN apt-get -yq autoremove && \
    apt-get -yq clean && \
    rm -rf /var/lib/apt/lists/* && \
    rm -rf /tmp/* && \
    rm -rf /var/tmp/*

RUN curl https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein > /usr/bin/lein
RUN chmod 755 /usr/bin/lein

EXPOSE 80

WORKDIR /app

ENV SERVER_NAME=dhost
ENV GOOGLE_CLIENT_ID=not-valid
ENV BUILD_TYPE=minimized

COPY project.clj project.clj
# Tell lein that running as root is okay.
ENV LEIN_ROOT=1
RUN lein deps

# File copies are explicit to ensure rebuilds use as much cache as possible.
COPY src src
COPY script/common script/common
RUN ./script/common/build.sh once

COPY src/docker/apache-site.conf /etc/apache2/sites-available/site.conf
COPY src/docker/run-apache.sh /etc/service/apache2/run

# openidc-baseimage requires this unused variable.
ENV CALLBACK_URI=http://example.com/

ENV HTTPD_PORT=80 SSL_HTTPD_PORT=443
ENV SERVER_ADMIN=devops@broadinstitute.org
ENV LOG_LEVEL=warn
ENV ORCH_URL_ROOT=http://orch:8080

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
