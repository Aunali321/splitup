################################################################################
# Multi-stage build for the SplitUp! Ktor server.
# Build context: repo root. CI invokes:
#   docker build -f deploy/server.Dockerfile -t splitup-server .
################################################################################

FROM eclipse-temurin:17-jdk AS build
WORKDIR /src

# Layer 1: Gradle wrapper + version catalog. Cached across builds when versions
# don't change.
COPY gradle gradle
COPY gradlew gradlew
COPY gradle.properties settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew

# Layer 2: per-module build scripts. Touching any of these invalidates layer 3.
COPY shared/build.gradle.kts shared/build.gradle.kts
COPY server/build.gradle.kts server/build.gradle.kts
COPY composeApp/build.gradle.kts composeApp/build.gradle.kts

# Layer 3: warm Gradle's dependency cache. Without this every code change
# re-downloads gigabytes of KMP artefacts.
RUN ./gradlew :server:dependencies --no-daemon || true

# Layer 4: sources. Code changes invalidate from here.
COPY shared shared
COPY server server

RUN ./gradlew :server:installDist --no-daemon

################################################################################
# Runtime stage. Slim JRE; no Gradle, no source.
################################################################################
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /src/server/build/install/server /app

EXPOSE 8080

ENV PORT=8080 \
    DATABASE_URL=jdbc:postgresql://postgres:5432/splitup \
    DATABASE_USER=splitup \
    DATABASE_PASSWORD=splitup

ENTRYPOINT ["/app/bin/server"]
