# syntax=docker/dockerfile:1.7

# Build container

FROM --platform=$BUILDPLATFORM eclipse-temurin:25-alpine AS build
# Add tools to run gradle wrapper
RUN apk add --no-cache bash unzip coreutils

WORKDIR /app

# Build scripts only. This layer stays cached until the build configuration
# changes, so editing sources does not re-download every dependency.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle version.gradle version.properties ./

RUN chmod +x gradlew

# The cache mount keeps the Gradle home across local rebuilds. Note it is not
# exported by `cache-to: type=gha`, so CI still resolves from the layer above.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew resolveDependencies --no-daemon

COPY src ./src

# Tests are run by CI before the image is built, so skip them here.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew shadowJar --no-daemon -x test

# Run container

FROM eclipse-temurin:25-jre-alpine AS runtime

# Alpine ships no fontconfig. AWT needs it to rasterise text for the level
# cards and the JFreeChart metric images.
RUN apk add --no-cache fontconfig freetype ttf-dejavu

WORKDIR /opt/votl

# Pinned so a bind-mounted host directory can be chowned to a known id pair.
ARG UID=10101
ARG GID=10102
RUN addgroup --gid "${GID}" votl && \
    adduser \
      --disabled-password \
      --gecos "" \
      --ingroup votl \
      --uid "${UID}" \
      votl && \
    mkdir -p /opt/votl/data /opt/votl/logs && \
    chown -R "${UID}:${GID}" /opt/votl

USER votl

COPY --from=build /app/VOTL-*.jar /bin/VOTL.jar

# Exec form keeps java as PID 1, so signals (and `docker stop`) reach it.
# MaxRAMPercentage makes the heap follow the container limit rather than the
# host's RAM — the default of 25% badly under-uses a small container.
# The jar stays outside WORKDIR because docker-compose bind-mounts over it.
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-Djava.awt.headless=true", \
            "--enable-native-access=ALL-UNNAMED", \
            "-jar","/bin/VOTL.jar"]

# Appended to the entrypoint, so `docker run votl --shards 0-9 --debug` works.
CMD []