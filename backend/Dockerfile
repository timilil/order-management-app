# Multi-stage build: the first stage compiles with the JDK and Maven, the final image has only a JRE and the jar.
# Build from the backend/ directory:  docker build -t order-management-api backend

# ---- build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Dependencies first, so this layer is cached until pom.xml changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in the CI pipeline (they need Docker for Testcontainers), not inside the image build.
RUN ./mvnw -B -q -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /workspace/target/order-management-api-*.jar app.jar

# The application never runs as root.
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# The runtime image has no curl or wget, so the check opens a TCP connection with bash.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]

ENTRYPOINT ["java", "-jar", "app.jar"]
