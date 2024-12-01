FROM maven:3.9.4 AS build

WORKDIR /app

COPY pom.xml ./

COPY src ./src

RUN mvn clean package -DskipTests

FROM openjdk:22

WORKDIR /app

COPY --from=build app/target/pass-generator.jar /app/pass-generator.jar

CMD ["java", "-jar", "/app/pass-generator.jar"]