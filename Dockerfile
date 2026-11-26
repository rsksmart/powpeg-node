FROM eclipse-temurin:17-jdk@sha256:b077006a4e93bbc3c07575d09b2fd528d7f720b3ee31ee879d2433e437dbcbb1 AS build

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
    mkdir -p artifacts && \
    cp build/libs/federate-node-*.jar artifacts/

FROM eclipse-temurin:17-jre@sha256:ce7dece87d45d67f2534fa4941e0db105c0daf5094600d1cbf1eee30cc8173d6
LABEL org.opencontainers.image.authors="ops@rootstocklabs.com"

RUN useradd -ms /sbin/nologin -d /var/lib/rsk rsk
USER rsk

WORKDIR /var/lib/rsk
COPY --from=build --chown=rsk:rsk /home/rsk/artifacts/ ./
RUN ln -s $(ls federate-node-*-all.jar) rsk.jar

ENV DEFAULT_JVM_OPTS="-Xss4M"
ENV RSKJ_SYS_PROPS="-Drsk.conf.file=/etc/rsk/node.conf"
ENV RSKJ_LOG_PROPS="-Dlogging.stdout=INFO"
ENV RSKJ_CLASS=co.rsk.federate.FederateRunner
ENV RSKJ_OPTS=""

# The Base64 intrinsic stays off: it is the code path affected by CVE-2026-114938 in JDK 17 builds before 17.0.21.1.
# Placing the flag after DEFAULT_JVM_OPTS, RSKJ_SYS_PROPS and RSKJ_LOG_PROPS prevents those variables from re-enabling it; _JAVA_OPTIONS can still override it.
ENTRYPOINT ["/bin/sh", "-c", "exec java $DEFAULT_JVM_OPTS $RSKJ_SYS_PROPS $RSKJ_LOG_PROPS -XX:-UseBASE64Intrinsics -cp rsk.jar $RSKJ_CLASS $RSKJ_OPTS \"${@}\"", "--"]
