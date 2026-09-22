FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /workspace
ENV MAVEN_OPTS="-Dhttps.protocols=TLSv1.2 -Dmaven.wagon.http.pool=false -Dmaven.wagon.httpconnectionManager.ttlSeconds=25 -Dmaven.wagon.http.retryHandler.count=10 -Dmaven.wagon.http.retryHandler.requestSentEnabled=true"
COPY pom.xml .
COPY proto proto
COPY common common
COPY outbox outbox
COPY gateway gateway
COPY user-service user-service
COPY wallet-service wallet-service
COPY payment-service payment-service
COPY notification-service notification-service
COPY audit-service audit-service
COPY analytics-service analytics-service
RUN --mount=type=cache,target=/root/.m2 \
    for i in 1 2 3 4 5 6; do \
      mvn -B -pl ${MODULE} -am -Dmaven.test.skip=true \
        -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn \
        package && exit 0; \
      echo "Maven attempt $i failed, retrying in 10s..."; sleep 10; \
    done; exit 1

FROM eclipse-temurin:21-jre
ARG MODULE
ARG PORT=8080
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/${MODULE}-1.0.0.jar app.jar
ENV PORT=${PORT} JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC -XX:TieredStopAtLevel=1"
EXPOSE ${PORT}
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=6 \
  CMD bash -c "echo > /dev/tcp/localhost/${PORT}" || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]