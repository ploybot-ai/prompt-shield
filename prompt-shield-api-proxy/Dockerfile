# Build stage
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY target/prompt-shield-api-proxy-*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

LABEL maintainer="Ploybot Intelligence S.L."
LABEL description="Prompt Shield API Proxy - OpenAI-compatible proxy with automatic PII obfuscation"

COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
