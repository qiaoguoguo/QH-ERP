#!/bin/sh
set -eu

required_secrets='
spring.datasource.password
qherp.account-permission.initial-admin-password
qherp.storage.s3.access-key
qherp.storage.s3.secret-key
'
wait_seconds="${QHERP_SECRET_WAIT_SECONDS:-60}"

case "$wait_seconds" in
  ''|*[!0-9]*)
    echo "QHERP_SECRET_WAIT_SECONDS 必须是正整数。" >&2
    exit 64
    ;;
esac
if [ "$wait_seconds" -le 0 ]; then
  echo "QHERP_SECRET_WAIT_SECONDS 必须大于 0。" >&2
  exit 64
fi

elapsed=0
while :; do
  missing=''
  for secret_name in $required_secrets; do
    if [ ! -s "/run/secrets/$secret_name" ]; then
      missing="$secret_name"
      break
    fi
  done
  if [ -z "$missing" ]; then
    break
  fi
  if [ "$elapsed" -ge "$wait_seconds" ]; then
    echo "API 等待运行时密钥超时：$missing" >&2
    exit 78
  fi
  sleep 1
  elapsed=$((elapsed + 1))
done

exec java -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -jar /app/app.jar
