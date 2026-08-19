#!/usr/bin/env bash
set -Eeuo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="$project_root/deploy/oracle/docker-compose.yml"
env_file="$project_root/deploy/oracle/.env"

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "Docker Engine and the Docker Compose plugin are required." >&2
  exit 1
fi
if [[ ! -f "$env_file" ]]; then
  echo "Missing $env_file" >&2
  echo "Copy deploy/oracle/.env.example to deploy/oracle/.env and fill in the real values." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$env_file"
set +a

if [[ -z "${SITE_ADDRESS:-}" || "$SITE_ADDRESS" == "travel.example.com" ]]; then
  echo "Set SITE_ADDRESS in deploy/oracle/.env before deploying." >&2
  exit 1
fi
if [[ -z "${APP_ADMIN_BOOTSTRAP_PASSWORD:-}" || "$APP_ADMIN_BOOTSTRAP_PASSWORD" == "replace-with-a-strong-unique-password" || ${#APP_ADMIN_BOOTSTRAP_PASSWORD} -lt 12 ]]; then
  echo "APP_ADMIN_BOOTSTRAP_PASSWORD must be a unique value of at least 12 characters." >&2
  exit 1
fi

install -d -m 700 \
  "$project_root/deploy/oracle/runtime/data" \
  "$project_root/deploy/oracle/runtime/uploads" \
  "$project_root/deploy/oracle/backups"
chmod 600 "$env_file"

docker compose --env-file "$env_file" -f "$compose_file" build --pull app
docker compose --env-file "$env_file" -f "$compose_file" up -d --remove-orphans
docker compose --env-file "$env_file" -f "$compose_file" ps

if [[ "$SITE_ADDRESS" == http://* || "$SITE_ADDRESS" == https://* ]]; then
  public_url="${SITE_ADDRESS%/}"
else
  public_url="https://${SITE_ADDRESS%/}"
fi

echo "Waiting for $public_url/health"
for attempt in {1..30}; do
  if curl --fail --silent --show-error --max-time 15 "$public_url/health" >/dev/null; then
    echo "Deployment is healthy: $public_url"
    exit 0
  fi
  sleep 4
done

echo "The containers started, but the public health check did not pass." >&2
echo "Check DNS, Oracle ingress rules, and logs with:" >&2
echo "docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.yml logs --tail=100" >&2
exit 1
