# BRouter server built from the official release download.
# (The Dockerfile shipped with BRouter pins a deprecated base image, 128 MB and one thread.)
FROM alpine:3.24 AS fetch
ARG BROUTER_VERSION=1.7.10
RUN apk add --no-cache curl unzip \
 && curl -fsSL -o /tmp/brouter.zip \
      "https://github.com/abrensch/brouter/releases/download/v${BROUTER_VERSION}/brouter-${BROUTER_VERSION}.zip" \
 && unzip -q /tmp/brouter.zip -d /tmp \
 && mv /tmp/brouter-${BROUTER_VERSION}/brouter-${BROUTER_VERSION}-all.jar /tmp/brouter.jar \
 && mv /tmp/brouter-${BROUTER_VERSION}/profiles2 /tmp/profiles2

FROM eclipse-temurin:25-jre
# Ubuntu 24.04 based images ship an "ubuntu" user with uid/gid 1000; free those ids first.
RUN (userdel --remove ubuntu 2>/dev/null || true) \
 && groupadd --gid 1000 brouter \
 && useradd --uid 1000 --gid brouter --no-create-home brouter \
 && mkdir -p /app /segments4 /customprofiles \
 && chown brouter:brouter /app /customprofiles
COPY --from=fetch /tmp/brouter.jar /app/brouter.jar
COPY --from=fetch /tmp/profiles2 /profiles2
WORKDIR /app
USER brouter
ENV JAVA_OPTS="-Xmx512M -Xms128M -Xmn16M -DmaxRunningTime=120 -DuseRFCMimeType=false" \
    MAXTHREADS=4
EXPOSE 17777
# The custom profile directory MUST be relative: BRouter resolves custom profiles against the
# profile directory (/profiles2/../customprofiles -> /customprofiles). An absolute path would be
# looked up as /profiles2/customprofiles and every custom profile would be "not found".
CMD ["sh", "-c", "exec java $JAVA_OPTS -cp /app/brouter.jar btools.server.RouteServer /segments4 /profiles2 ../customprofiles 17777 $MAXTHREADS 0.0.0.0"]
