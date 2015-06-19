FROM nginx
#Once lein builds site/content, copy target/ over to be served up
RUN mkdir /usr/share/nginx/html/target
RUN mkdir /usr/share/nginx/html/src
COPY target/ /usr/share/nginx/html/target
COPY src/ /usr/share/nginx/html/src
