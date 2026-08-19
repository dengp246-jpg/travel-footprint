#!/usr/bin/env bash
set -Eeuo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="$project_root/deploy/oracle/docker-compose.yml"
env_file="$project_root/deploy/oracle/.env"
runtime_dir="$project_root/deploy/oracle/runtime"
backup_dir="$project_root/deploy/oracle/backups"
timestamp="$(date -u +%Y%m%d-%H%M%S)"
archive="$backup_dir/travel-footprint-$timestamp.tar.gz"

if [[ ! -f "$env_file" ]]; then
  echo "Missing $env_file" >&2
  exit 1
fi

install -d -m 700 "$backup_dir"
docker compose --env-file "$env_file" -f "$compose_file" stop app
restart_required=true
cleanup() {
  if [[ "${restart_required:-false}" == true ]]; then
    docker compose --env-file "$env_file" -f "$compose_file" start app >/dev/null
  fi
}
trap cleanup EXIT

tar -C "$runtime_dir" -czf "$archive" data uploads
chmod 600 "$archive"
docker compose --env-file "$env_file" -f "$compose_file" start app >/dev/null
restart_required=false

echo "Backup created: $archive"
