FROM broadinstitute/openidc-baseimage:1.8.6

# How to install OpenJDK 8 from:
# http://ubuntuhandbook.org/index.php/2015/01/install-openjdk-8-ubuntu-14-04-12-04-lts/

# Add repo, update, cleanup all in one command to minimize layer size.
RUN \
  add-apt-repository ppa:openjdk-r/ppa && apt-get update --fix-missing \
  && apt-get install -y -qq --no-install-recommends \
    openjdk-8-jdk \
    php5-cli \
    rlfe \
  && apt-get -yq autoremove && apt-get -yq clean && rm -rf /var/lib/apt/lists/* \
  && rm -rf /tmp/* && rm -rf /var/tmp/*

RUN curl https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein > /usr/bin/lein
RUN chmod 755 /usr/bin/lein
# Tell lein that running as root is okay.
ENV LEIN_ROOT=1
# Actually install leiningen.
RUN lein --version

WORKDIR /app

ENV SERVER_NAME=dhost
ENV BUILD_TYPE=minimized

COPY project.clj project.clj
# Download deps and plugins.
RUN lein cljsbuild once

# File copies are explicit to ensure rebuilds use as much cache as possible.
COPY src/cljs src/cljs
COPY src/static src/static
COPY script/common script/common
RUN ./script/common/build.sh once

COPY src/docker/override.sh /etc/apache2/
COPY src/docker/site*.conf /etc/apache2/sites-available/
COPY src/docker/locations.conf /etc/apache2/

# openidc-baseimage requires this unused variable.
ENV CALLBACK_URI=http://example.com/

EXPOSE 80
EXPOSE 443

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
