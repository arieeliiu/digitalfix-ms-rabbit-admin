FROM eclipse-temurin:21-jdk AS compilacion
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM compilacion AS pruebas
ENTRYPOINT ["./mvnw", "-B", "test"]

FROM eclipse-temurin:21-jre
RUN groupadd --gid 10002 digitalfix && useradd --uid 10002 --gid digitalfix --no-create-home digitalfix
WORKDIR /app
COPY --from=compilacion /app/target/*.jar app.jar
USER digitalfix
ENTRYPOINT ["java", "-jar", "app.jar"]
