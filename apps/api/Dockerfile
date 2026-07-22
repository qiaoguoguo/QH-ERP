FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build

WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c

ARG QHERP_SOURCE_COMMIT=unknown
LABEL org.opencontainers.image.title="QH ERP API" \
      org.opencontainers.image.revision="${QHERP_SOURCE_COMMIT}"

RUN apk upgrade --no-cache \
    && addgroup -S qherp \
    && adduser -S -D -G qherp -h /app -s /sbin/nologin qherp
WORKDIR /app
COPY --from=build --chown=qherp:qherp /workspace/target/api-0.0.1-SNAPSHOT.jar /app/app.jar

USER qherp
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
