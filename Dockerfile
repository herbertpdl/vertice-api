FROM eclipse-temurin:25-jdk AS dev

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN ./gradlew --version

COPY . .
RUN chmod +x docker/dev-entrypoint.sh

EXPOSE 8080 9090

CMD ["docker/dev-entrypoint.sh"]
