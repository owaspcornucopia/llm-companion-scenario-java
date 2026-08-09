FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /application
COPY pom.xml ./
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /application
RUN apt-get update \
	&& apt-get install -y --no-install-recommends libgomp1 \
	&& rm -rf /var/lib/apt/lists/*
COPY --from=build /application/target/pwnednext-java-1.0.0.jar app.jar
EXPOSE 9000 9001
ENTRYPOINT ["java", "-jar", "/application/app.jar"]