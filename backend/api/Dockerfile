# ─── Etapa 1: Build ────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copia o pom.xml primeiro para aproveitar cache do Docker
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia o código e compila
COPY src ./src
RUN mvn clean package -DskipTests

# ─── Etapa 2: Runtime ──────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]