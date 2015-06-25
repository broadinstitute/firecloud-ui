FROM centos:7

RUN curl https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein > /usr/bin/lein
RUN chmod 755 /usr/bin/lein
RUN yum -y install java-1.8.0-openjdk php-cli ruby && yum clean all

EXPOSE 8000

COPY project.clj /usr/firecloud-ui/project.clj
COPY src  /usr/firecloud-ui/src
COPY script /usr/firecloud-ui/script

WORKDIR /usr/firecloud-ui
RUN ./script/dev/build-once.sh
RUN lein test

CMD ./script/dev/serve-locally.sh

