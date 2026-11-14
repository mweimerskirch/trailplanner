FROM maven:3.10-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B package -DskipTests -Dspotless.check.skip=true

# trailplanner-mtp: USB access to devices (MTP) for syncing routes. Same Ubuntu as the runtime image.
FROM ubuntu:26.04 AS mtp
RUN apt-get update && apt-get install -y --no-install-recommends gcc libc6-dev libmtp-dev pkg-config \
    && rm -rf /var/lib/apt/lists/*
COPY mtp/trailplanner-mtp.c /src/
RUN gcc -O2 -Wall -Wextra -Werror -o /trailplanner-mtp /src/trailplanner-mtp.c $(pkg-config --cflags --libs libmtp)

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends libmtp9t64 \
    && rm -rf /var/lib/apt/lists/*
# Same uid as the BRouter image, so both can use the shared custom profile directory.
# Ubuntu 24.04 based images ship an "ubuntu" user with uid/gid 1000; free those ids first.
RUN (userdel --remove ubuntu 2>/dev/null || true) \
    && groupadd --gid 1000 trailplanner \
    && useradd --uid 1000 --gid trailplanner --no-create-home trailplanner \
    && mkdir -p /data /customprofiles \
    && chown trailplanner:trailplanner /data /customprofiles
COPY --from=build /src/target/trailplanner-backend-*.jar /app/app.jar
COPY --from=mtp /trailplanner-mtp /usr/local/bin/trailplanner-mtp
USER trailplanner
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
