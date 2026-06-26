# Build stage
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY prompt-shield-api-proxy/target/prompt-shield-api-proxy-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

LABEL maintainer="Ploybot Intelligence S.L."
LABEL description="Prompt Shield API Proxy - OpenAI-compatible proxy with automatic PII obfuscation"

COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "application.jar"]
