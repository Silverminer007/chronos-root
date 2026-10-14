#!/usr/bin/env bash
# Regenerates schema/java_schema_expected.txt from the Java Flyway migrations.
# Needs Docker; no Java is run, only the SQL files are applied to a throw-away PostgreSQL.
set -euo pipefail

cd "$(dirname "$0")/.."
name="chronos-java-schema-$$"
trap 'docker rm -f "$name" >/dev/null 2>&1 || true' EXIT

docker run -d --name "$name" -e POSTGRES_PASSWORD=postgres postgres:16-alpine >/dev/null
until docker exec "$name" pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done
sleep 2
for f in $(ls ../backend/src/main/resources/db/migration/V*.sql | sort -V); do
  docker exec -i "$name" psql -U postgres -v ON_ERROR_STOP=1 -q < "$f" >/dev/null
done
docker exec -i "$name" psql -U postgres -At < schema/snapshot.sql > schema/java_schema_expected.txt
echo "wrote schema/java_schema_expected.txt ($(wc -l < schema/java_schema_expected.txt) lines)"
