# Builds and runs the lead server (server/), not the Android app.
#   docker build -t loan-finder-server .
#   docker run -p 8080:8080 -v leads-data:/data --env-file server.env loan-finder-server
# See server/README.md for the environment variables.

FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon :server:installDist

FROM eclipse-temurin:17-jre
# Runs as an unprivileged user; only it can read the database folder.
RUN useradd --system --uid 10001 --home-dir /app leads \
    && mkdir -p /data && chown leads /data && chmod 700 /data
COPY --from=build /src/server/build/install/server /app
USER leads
ENV PORT=8080 \
    DATABASE_PATH=/data/leads.db
EXPOSE 8080
# Mount persistent storage here, or leads are lost on every redeploy.
VOLUME ["/data"]
ENTRYPOINT ["/app/bin/server"]
