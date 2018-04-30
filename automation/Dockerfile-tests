FROM iflavoursbv/sbt-openjdk-8-alpine

COPY src /app/src
COPY test.sh /app
COPY project /app/project
COPY build.sbt /app
COPY logback-test.xml /app

WORKDIR /app

ENTRYPOINT ["bash", "test.sh"]
