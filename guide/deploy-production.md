# Деплой Corpsman на публичный хост (HTTPS, вариант Caddy)

Применимо к ветке `feature/https` и последующим релизам (2.0.0+).
Предполагается VPS с Linux, установленными Docker и docker compose plugin,
и домен, A-запись которого указывает на IP сервера.

---

## 1. Подготовка сервера

```bash
# firewall: открыть только нужное
sudo ufw allow 22/tcp     # ssh
sudo ufw allow 80/tcp     # http (редирект + выпуск сертификата Let's Encrypt)
sudo ufw allow 443/tcp    # https
sudo ufw enable
```

Порты 5432 (postgres) и 8080 (app) наружу не открывать — в compose они
внутри docker-сети; если в вашем файле остался проброс `5432:5432`,
уберите его для продакшена.

## 2. Получение кода

```bash
git clone <repo-url> corpsman && cd corpsman
git checkout develop            # или feature/https / тег релиза
```

## 3. Конфигурация окружения

Рядом с `docker-compose.yml` создайте файл `.env`:

```dotenv
# Домен сайта — Caddy сам получит сертификат Let's Encrypt
SITE_ADDRESS=club.example.com

# Пароль БД (рекомендуется сменить и указать тот же в SPRING_DATASOURCE_PASSWORD)
POSTGRES_PASSWORD=secret

# Cookie Secure включён в compose по умолчанию (за Caddy всегда HTTPS)
```

Переменные читаются из `.env` автоматически:
`SITE_ADDRESS`, `COOKIE_SECURE=true` (уже в compose), `SPRING_DATASOURCE_*`.

## 4. Первый запуск

Продакшен использует отдельный compose-файл с HTTPS-профилем (Caddy):

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f caddy app   # certificate obtained / Started CorpsmanApplication
```

Все последующие команды управления прод-стеком тоже выполняются с
`-f docker-compose.prod.yml`.

Caddy при первом запросе сам выпустит сертификат Let's Encrypt —
порты 80 и 443 должны быть открыты до этого момента.

Проверка:

```bash
curl -sI http://club.example.com/login        # 308 -> https
curl -sI https://club.example.com/login       # 200
curl -sI https://club.example.com/login | grep -i strict-transport  # после включения HSTS (см. п.7)
```

## 5. Создание первого администратора

Регистрация возможна только по инвайт-ключу, а ключи создаёт админ,
поэтому первую учётную запись вносим в БД напрямую:

```bash
# хэш пароля (BCrypt, strength 12) — выполнить локально, где есть репозиторий:
jshell --class-path "$(find ~/.m2 -name 'spring-security-crypto-*.jar' | head -1)" <<'EOF' 2>/dev/null
System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode("СЛОЖНЫЙ_ПАРОЛЬ"));
/exit
EOF

docker exec -it corpsman-postgres psql -U app_user -d appdb -c \
  "INSERT INTO users (username, password_hash, role, active, created_at, updated_at, version)
   VALUES ('admin', '<ВСТАВИТЬ_ХЭШ>', 'ADMIN', true, NOW(), NOW(), 0);"
```

Дальнейшие пользователи — через «Ключи» в интерфейсе под админом.

## 6. Обновление версии

```bash
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

Миграции Liquibase применятся автоматически при старте приложения.
Данные живут в томах `postgres_data`, `uploads`, `logs`, `caddy_data`
— при `up -d --build` они сохраняются.

## 7. Включение HSTS (после проверки, что всё работает по HTTPS)

В `deploy/caddy/Caddyfile` внутрь блока сайта добавить:

```text
header Strict-Transport-Security "max-age=31536000; includeSubDomains"
```

и `docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile`.
Внимание: после HSTS браузеры будут ходить только по HTTPS минимум год.

## 8. Резервные копии

```bash
# БД
docker exec corpsman-postgres pg_dump -U app_user appdb | gzip > backup-$(date +%F).sql.gz
# Загруженные файлы
tar czf uploads-$(date +%F).tgz uploads/

# Восстановление
gunzip -c backup-2026-08-22.sql.gz | docker exec -i corpsman-postgres psql -U app_user -d appdb
```

## 9. Откат на HTTP (аварийно)

```bash
docker compose -f docker-compose.prod.yml stop caddy
# в compose вернуть маппинг "8080:8080" сервису app и COOKIE_SECURE=false
docker compose up -d app
```

Приложение полностью работоспособно по HTTP без изменений кода.

---

## Локальная сборка и тестирование (HTTP)

Локальный профиль — обычный `docker-compose.yml` (без Caddy, HTTP:8080,
cookie без Secure):

```bash
docker compose up -d --build
# приложение: http://localhost:8080
docker compose logs -f app
```

Домен и TLS для локали не требуются. Интеграционные тесты — `mvn test`
(используют MockMvc поверх HTTP, от профиля не зависят).

## Краткий чеклист продакшена

- [ ] DNS A-запись → IP сервера, порты 80/443 открыты
- [ ] `.env`: SITE_ADDRESS=<домен>, POSTGRES_PASSWORD изменён
- [ ] Убран проброс порта 5432 наружу
- [ ] Первый админ создан, пароль ≥12 символов
- [ ] `https://домен` открывается с валидным замком
- [ ] Логин работает, cookie имеет Secure/HttpOnly/SameSite=Lax
- [ ] Настроен cron бэкапа БД
