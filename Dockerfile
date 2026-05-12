# ---------- Build stage ----------
FROM gradle:9.2.1-jdk21 AS build
WORKDIR /src

# Корпоративные CA (если присутствуют в build-контексте) импортируем и в OS-trust,
# и в JVM cacerts — иначе Gradle не может скачать плагины через MITM-прокси.
# Если файла нет, шаг no-op.
COPY docker/extra-ca.crt /tmp/extra-ca.crt
#RUN set -eux; \
#    if [ -s /tmp/extra-ca.crt ]; then \
#      cp /tmp/extra-ca.crt /usr/local/share/ca-certificates/extra-ca.crt; \
#      update-ca-certificates; \
#      awk 'BEGIN{n=0} /BEGIN CERT/{n++; f=sprintf("/tmp/c%03d.pem", n)} {print > f}' /tmp/extra-ca.crt; \
#      for f in /tmp/c*.pem; do \
#        keytool -importcert -noprompt -trustcacerts -alias "extra-$(basename "$f" .pem)" -file "$f" -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit || true; \
#      done; \
#    fi; \
#    rm -f /tmp/extra-ca.crt /tmp/c*.pem

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
