FROM nginx
#Once lein builds site/content, copy target/ over to be served up
COPY target/ /usr/share/nginx/html

