FROM broadinstitute/openidc-baseimage

RUN add-apt-repository ppa:openjdk-r/ppa
RUN apt-get update
RUN apt-get install -qy openjdk-8-jdk php5-cli curl rlfe

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

COPY project.clj project.clj
# Tell lein that running as root is okay.
ENV LEIN_ROOT=1
RUN lein deps

# File copies are explicit to ensure rebuilds use as much cache as possible.
COPY src src
COPY script/common script/common
COPY script/release/build-once.sh script/release/build-once.sh
COPY script/release/build-release.sh script/release/build-release.sh

ENV GOOGLE_CLIENT_ID=806222273987-2ntvt4hnfsikqmhhc18l64vheh4cj34q.apps.googleusercontent.com
RUN ./script/release/build-release.sh

COPY script/release/apache-site.conf /etc/apache2/sites-available/site.conf
COPY script/release/run-apache.sh /etc/service/apache2/run

# openidc-baseimage requires this unused variable.
ENV CALLBACK_URI=http://example.com/

ENV HTTPD_PORT=80 SSL_HTTPD_PORT=443
ENV SERVER_ADMIN=devops@broadinstitute.org
ENV LOG_LEVEL=warn
ENV SERVER_NAME=docker-machine
ENV ORCH_URL_ROOT=https://orch

# Override in development since figwheel does not support secure websockets.
ENV HTTPS_ONLY=true
