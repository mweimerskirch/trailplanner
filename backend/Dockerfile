FROM maven:3.10-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B package -DskipTests -Dspotless.check.skip=true

FROM eclipse-temurin:25-jre
# Same uid as the BRouter image, so both can use the shared custom profile directory.
# Ubuntu 24.04 based images ship an "ubuntu" user with uid/gid 1000; free those ids first.
RUN (userdel --remove ubuntu 2>/dev/null || true) \
    && groupadd --gid 1000 trailplan \
    && useradd --uid 1000 --gid trailplan --no-create-home trailplan \
    && mkdir -p /data /customprofiles \
    && chown trailplan:trailplan /data /customprofiles
COPY --from=build /src/target/trailplan-backend-*.jar /app/app.jar
USER trailplan
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
