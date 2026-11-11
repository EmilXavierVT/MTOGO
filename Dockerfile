FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp package dependency:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory=/build/lib \
    && cp target/*.jar app.jar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/app.jar ./app.jar
COPY --from=build /build/lib ./lib
USER 10001:10001
EXPOSE 7072
ENTRYPOINT ["java", "-cp", "app.jar:lib/*", "app.Main"]
