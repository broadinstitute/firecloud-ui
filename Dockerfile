FROM broadinstitute/apache-proxy:latest

WORKDIR /app

EXPOSE 80
EXPOSE 443

# Override HTTPS_ONLY in development since figwheel doesn't support secure websockets.
ENV HTTPS_ONLY=true

COPY target /app/target

COPY src/docker/override.sh /etc/apache2/
COPY src/docker/site.conf src/docker/site-https-only.conf src/docker/stackdriver.conf /etc/apache2/sites-available/
