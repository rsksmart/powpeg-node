FROM openjdk:8-jdk-slim-buster AS build

ARG RSK_RELEASE
ENV RSK_VERSION $RSK_RELEASE
ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update -y && \
    apt-get install -y git curl gnupg

RUN useradd -ms /bin/bash rsk
USER rsk

WORKDIR /home/rsk
COPY --chown=rsk:rsk . ./

RUN gpg --keyserver https://secchannel.rsk.co/SUPPORT.asc --recv-keys 1DC9157991323D23FD37BAA7A6DBEAC640C5A14B && \
    gpg --verify --output SHA256SUMS SHA256SUMS.asc && \
    sha256sum --check SHA256SUMS && \
    ./configure.sh && \
    ./gradlew --no-daemon clean build -x test && \
    cp "build/libs/federate-node-$RSK_VERSION-all.jar" rsk.jar

FROM openjdk:8-jre-slim-buster
LABEL org.opencontainers.image.authors="ops@rootstocklabs.com"

RUN useradd -ms /sbin/nologin -d /var/lib/rsk rsk
USER rsk

WORKDIR /var/lib/rsk
COPY --from=build --chown=rsk:rsk /home/rsk/rsk.jar ./

ENV DEFAULT_JVM_OPTS="-Xss4M"
ENV RSKJ_SYS_PROPS="-Dlogback.configurationFile='/etc/rsk/logback.xml' -Drsk.conf.file=/etc/rsk/node.conf"
ENV RSKJ_CLASS=co.rsk.federate.FederateRunner
ENV RSKJ_OPTS=""

ENTRYPOINT ["/bin/sh", "-c", "exec java $DEFAULT_JVM_OPTS $RSKJ_SYS_PROPS -cp rsk.jar $RSKJ_CLASS $RSKJ_OPTS \"${@}\"", "--"]
