# One image with the whole app: the React frontend is built and served by the Spring Boot backend.
# The database runs as its own container; see docker-compose.yml.

# ---- frontend ----
FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# the generated API types are checked in; gen:api only needs the contract when it changes
COPY specs/004-meeting-tools/contracts /build/specs/004-meeting-tools/contracts
RUN npm run build

# ---- backend ----
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build/backend
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /build/frontend/dist ./src/main/resources/static
RUN mvn -q -B package -DskipTests -Dspotless.check.skip=true \
    && cp target/visualizer-*.jar /build/app.jar

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=backend /build/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
