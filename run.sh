#!/usr/bin/env sh
# Build if needed, then start. Usage: ANTHROPIC_API_KEY=sk-ant-... ./run.sh
set -e
cd "$(dirname "$0")"
[ -f .env ] && { set -a; . ./.env; set +a; }
[ -f target/furniture-detailing.jar ] || mvn -q -B package -DskipTests
[ -n "$ANTHROPIC_API_KEY" ] || echo "ANTHROPIC_API_KEY is not set: photo analysis will be off."
exec java -jar target/furniture-detailing.jar
