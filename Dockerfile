# ======================================================
# OMNIMART AI - DOCKERFILE FOR RENDER HOSTING
# Build:   docker build -t omnimart-ai .
# Run:     docker run -p 8080:8080 -e NVIDIA_API_KEYS=... -e BREVO_API_KEY=... omnimart-ai
# Render:  Web Service -> Dockerfile -> deploy; health check path /health
# ======================================================

# -------- Stage 1: Build with Maven + JDK 21 --------
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Cache dependency resolution separately from source (layer caching)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Copy sources and produce the runnable jar
COPY src ./src
RUN mvn -B clean package -DskipTests

# -------- Stage 2: Slim runtime image --------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as non-root user (Render security baseline)
RUN useradd -m appuser

# Non-volatile, well-known jar name
COPY --from=builder /app/target/aistore-1.0.0.jar app.jar

# India timezone so logs/delivery dates match the target market
ENV TZ=Asia/Kolkata

USER appuser

# Render injects PORT (free tier 10000); application.yml prefers it,
# falling back to 8080 for local runs
ENV SERVER_PORT=8080
EXPOSE 8080

# MaxRAMPercentage keeps the JVM inside Render's 512MB container budget
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseSerialGC", "-jar", "app.jar"]
