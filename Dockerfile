# Stage 1: Build da aplicação com Maven + Java 25
FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /app

# Baixa dependências primeiro para aproveitar o cache de camadas
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia o código-fonte e gera o JAR executável
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime leve com JRE 25 e usuário não-root
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /app/target/*.jar app.jar
RUN chown spring:spring app.jar

USER spring:spring

EXPOSE 8080

ENV OPENAI_API_KEY="" \
    GEMINI_API_KEY="" \
    OLLAMA_BASE_URL="http://host.docker.internal:11434"

ENTRYPOINT ["java", "-jar", "app.jar"]
