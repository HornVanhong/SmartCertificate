FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Install curl for container health probes
RUN apk add --no-cache curl \
    && addgroup -g 10001 -S smartcert \
    && adduser -u 10001 -S smartcert -G smartcert

COPY --chown=smartcert:smartcert target/smart-certificate-backend-*.jar /app/app.jar

USER smartcert:smartcert

ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
