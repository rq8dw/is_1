#!/bin/sh
# Запуск ИС «Организации» на helios: sh ~/is-lab1/start.sh [порт]
# Пароль к БД pg/studs берётся из ~/.pgpass и в командную строку процесса не попадает.

APP_DIR=$(cd "$(dirname "$0")" && pwd)
PORT=${1:-24672}
JAVA=/usr/local/openjdk17/bin/java
JAR="$APP_DIR/is-lab1.jar"

# Перезапуск: останавливаем предыдущий экземпляр этого приложения
if pkill -U "$(id -u)" -f "$JAR" 2>/dev/null; then
    sleep 3
fi

DB_USER=$(id -un)
DB_PASSWORD=$(grep -m 1 ":$DB_USER:" ~/.pgpass | cut -d: -f5-)
if [ -z "$DB_PASSWORD" ]; then
    echo "В ~/.pgpass нет пароля для пользователя $DB_USER" >&2
    exit 1
fi
export PORT DB_USER DB_PASSWORD
unset _JAVA_OPTIONS JAVA_TOOL_OPTIONS

# Без явного -Xmx JVM на helios пытается зарезервировать ~30 ГБ и не стартует.
# Подоболочка отвязывает процесс от SSH-сессии (в /bin/sh FreeBSD нет disown).
cd "$APP_DIR" || exit 1
( nohup "$JAVA" -Xms64m -Xmx384m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC \
    -jar "$JAR" --spring.profiles.active=helios > "$APP_DIR/app.log" 2>&1 < /dev/null & )

echo "Запущено на порту $PORT, лог: $APP_DIR/app.log"
