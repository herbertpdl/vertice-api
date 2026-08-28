#!/bin/sh
# Gradle's native --continuous file watching relies on inotify, which does not
# reliably see changes written through Docker Desktop's virtiofs bind mounts on
# macOS. This polls `src` for changes and triggers an incremental recompile;
# spring-boot-devtools (which polls the compiled classes directory itself,
# independent of any OS-level fs watcher) then restarts the running app.
set -e

MARKER=/tmp/.vertice-last-build
touch "$MARKER"

(
  while true; do
    if [ -n "$(find src -newer "$MARKER" -type f 2>/dev/null)" ]; then
      touch "$MARKER"
      echo "[dev-watch] change detected, recompiling..."
      ./gradlew classes || echo "[dev-watch] compile failed, will retry on next change"
    fi
    sleep 2
  done
) &
WATCH_PID=$!

cleanup() {
  kill "$WATCH_PID" 2>/dev/null
}
trap cleanup EXIT INT TERM

./gradlew bootRun
