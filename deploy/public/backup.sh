#!/usr/bin/env bash
# Nightly backup of the database and uploaded files, keeping the last 14 days.
# Install once (runs at 03:15 every night):
#   (crontab -l 2>/dev/null; echo "15 3 * * * $HOME/mentalist/deploy/public/backup.sh >> $HOME/mentalist-backups/backup.log 2>&1") | crontab -
# Restore a database backup:
#   gunzip -c ~/mentalist-backups/db-YYYY-MM-DD.sql.gz | docker compose -f docker-compose.public.yml exec -T db psql -U mentalist mentalist
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="${BACKUP_DIR:-$HOME/mentalist-backups}"
DAY=$(date +%F)
mkdir -p "$OUT"
chmod 700 "$OUT"
umask 077

docker compose -f docker-compose.public.yml exec -T db pg_dump -U mentalist mentalist | gzip > "$OUT/db-$DAY.sql.gz.part"
mv "$OUT/db-$DAY.sql.gz.part" "$OUT/db-$DAY.sql.gz"
docker compose -f docker-compose.public.yml exec -T backend tar czf - -C /data uploads > "$OUT/uploads-$DAY.tgz.part"
mv "$OUT/uploads-$DAY.tgz.part" "$OUT/uploads-$DAY.tgz"

find "$OUT" -name 'db-*.sql.gz' -mtime +14 -delete
find "$OUT" -name 'uploads-*.tgz' -mtime +14 -delete
echo "$(date -Is) backup ok: $(du -sh "$OUT" | cut -f1) in $OUT"
