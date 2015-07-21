FROM centos:7

RUN curl https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein > /usr/bin/lein
RUN chmod 755 /usr/bin/lein
RUN yum -y install httpd java-1.8.0-openjdk php-cli ruby && yum clean all

EXPOSE 80

COPY project.clj /usr/firecloud-ui/project.clj
COPY src  /usr/firecloud-ui/src
COPY script /usr/firecloud-ui/script

WORKDIR /usr/firecloud-ui
RUN ./script/dev/build-once.sh
RUN lein test

RUN ./script/release/build-release.sh
RUN cp -R /usr/firecloud-ui/target/* /var/www/html
CMD /usr/sbin/httpd -e info -DFOREGROUND

