# Build container

FROM gradle:jdk21-alpine AS build

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon

# Run container

FROM eclipse-temurin:21-alpine AS runtime

WORKDIR /opt/votl

ARG UID=10101
RUN adduser \
    --disabled-password \
    --gecos "" \
    --uid "${UID}" \
    votl; \
    chown votl:votl -R /opt/votl; \
    chmod u+w /opt/votl; \
    chmod 0755 -R /opt/votl

USER votl

COPY --from=build /home/gradle/src/VOTL.jar /bin/

CMD ["java","-jar","/bin/VOTL.jar"]