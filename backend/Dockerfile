# Multi-stage build: `docker compose up --build` works from a clean clone, no JDK on the host.

# --- Build stage: compile the Spring Boot jar with the Gradle wrapper ---------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Wrapper and build script first, so the Gradle distribution and dependencies are cached in their
# own layer and only re-downloaded when build.gradle.kts changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew \
 && ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx1g dependencies > /dev/null

COPY src src
# Tests run on the host (`./gradlew build`, Testcontainers); the image build skips them.
# The Kotlin compiler runs in-process so the build fits in a small Docker VM.
RUN ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx1g \
      -Pkotlin.compiler.execution.strategy=in-process bootJar -x test \
 && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /src/app.jar \;

# --- Runtime stage: JRE only --------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Runs as an unprivileged system user (uid/gid 10001, no login shell), not root: a hole in the
# app or a library then gets a user that owns nothing but the uploads folder.
RUN groupadd --system --gid 10001 recipe \
 && useradd --system --uid 10001 --gid 10001 --no-create-home --home-dir /nonexistent \
      --shell /usr/sbin/nologin recipe

# Uploaded photos (photo/PhotoController writes them, WebConfig serves /uploads/** read-only).
# Mounted as a volume at /app/uploads; owned by the app user so a fresh named volume takes that
# ownership.
RUN mkdir -p /app/uploads && chown 10001:10001 /app/uploads
# The jar stays root-owned and read-only for the app user.
COPY --from=build /src/app.jar app.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
