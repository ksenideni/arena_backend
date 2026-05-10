# syntax=docker/dockerfile:1.7

# ---------- Build stage ----------
FROM gradle:9.2.1-jdk21 AS build
WORKDIR /src

# Корпоративные CA (если присутствуют в build-контексте) импортируем и в OS-trust,
# и в JVM cacerts — иначе Gradle не может скачать плагины через MITM-прокси.
# Если файла нет, шаг no-op.
COPY docker/extra-ca.crt /tmp/extra-ca.crt
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY protocol/build.gradle.kts protocol/
COPY server/build.gradle.kts server/
COPY web/build.gradle.kts web/
COPY bots/build.gradle.kts bots/

RUN gradle --no-daemon :web:dependencies --quiet || true

COPY protocol ./protocol
COPY server ./server
COPY web ./web
COPY bots ./bots

RUN gradle --no-daemon :web:installDist -x test

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --system arena && useradd --system --gid arena arena

COPY --from=build /src/web/build/install/web /app

ENV HTTP_PORT=8080 \
    TCP_PORT=9000

EXPOSE 8080 9000

USER arena
ENTRYPOINT ["/app/bin/web"]
