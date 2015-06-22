FROM httpd:2.4
#Once lein builds site/content, copy target/ over to be served up
RUN mkdir /usr/local/apache2/htdocs/target
RUN mkdir /usr/local/apache2/htdocs/src
COPY target/ /usr/local/apache2/htdocs/target
COPY src/ /usr/local/apache2/htdocs/src
