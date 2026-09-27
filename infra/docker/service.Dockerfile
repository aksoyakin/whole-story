# syntax=docker/dockerfile:1.7
# Builds one Spring Boot service: docker build -f infra/docker/service.Dockerfile --build-arg SERVICE=api .
#
# There is no official eclipse-temurin:27 image yet, so the Temurin 27 GA binaries are installed from
# Adoptium and verified against their published SHA-256 checksums. Replace with eclipse-temurin:27-jre once published.

ARG TEMURIN_RELEASE=27%2B35
ARG TEMURIN_BUILD=27_35

FROM debian:trixie-slim AS jdk
ARG TARGETARCH
ARG TEMURIN_RELEASE
ARG TEMURIN_BUILD
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl && rm -rf /var/lib/apt/lists/*
RUN set -eux; \
    case "$TARGETARCH" in \
      amd64) arch=x64;     sha=1cf69a4848ffb728b3b260dfd45206a51566ab571a02a30092271d4c580bccbc ;; \
      arm64) arch=aarch64; sha=e4ec5c7276290c7bde0baecba9a29be4962f5388684296406a9799d465936087 ;; \
      *) echo "unsupported architecture $TARGETARCH"; exit 1 ;; \
    esac; \
    curl -fsSL -o /tmp/jdk.tar.gz "https://github.com/adoptium/temurin27-binaries/releases/download/jdk-${TEMURIN_RELEASE}/OpenJDK27U-jdk_${arch}_linux_hotspot_${TEMURIN_BUILD}.tar.gz"; \
    echo "$sha  /tmp/jdk.tar.gz" | sha256sum -c -; \
    mkdir -p /opt/java && tar -xzf /tmp/jdk.tar.gz -C /opt/java --strip-components=1 && rm /tmp/jdk.tar.gz
ENV JAVA_HOME=/opt/java PATH=/opt/java/bin:$PATH

FROM debian:trixie-slim AS jre
ARG TARGETARCH
ARG TEMURIN_RELEASE
ARG TEMURIN_BUILD
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl && rm -rf /var/lib/apt/lists/*
RUN set -eux; \
    case "$TARGETARCH" in \
      amd64) arch=x64;     sha=2cb1b81ab49f516e5aeb28ee8acf3c73d64c77ca432ac959c6511a335342d8e9 ;; \
      arm64) arch=aarch64; sha=a41b54098373f1ca8f75ee344db19b93ac39eca6c529c3ee9dc50f4b3e7857de ;; \
      *) echo "unsupported architecture $TARGETARCH"; exit 1 ;; \
    esac; \
    curl -fsSL -o /tmp/jre.tar.gz "https://github.com/adoptium/temurin27-binaries/releases/download/jdk-${TEMURIN_RELEASE}/OpenJDK27U-jre_${arch}_linux_hotspot_${TEMURIN_BUILD}.tar.gz"; \
    echo "$sha  /tmp/jre.tar.gz" | sha256sum -c -; \
    mkdir -p /opt/java && tar -xzf /tmp/jre.tar.gz -C /opt/java --strip-components=1 && rm /tmp/jre.tar.gz
ENV JAVA_HOME=/opt/java PATH=/opt/java/bin:$PATH

FROM jdk AS build
ARG SERVICE
WORKDIR /src
# Poms first: dependency resolution is cached until a pom changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY libs/event-contracts/pom.xml libs/event-contracts/
COPY services/ingest/pom.xml services/ingest/
COPY services/processor/pom.xml services/processor/
COPY services/api/pom.xml services/api/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl services/${SERVICE} -am dependency:go-offline
COPY libs libs
COPY services services
# Tests run in CI before images are built.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl services/${SERVICE} -am package -DskipTests \
 && java -Djarmode=tools -jar services/${SERVICE}/target/${SERVICE}-*.jar extract --layers --launcher --destination /layers

FROM jre
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
# Layers ordered from least to most frequently changing.
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
