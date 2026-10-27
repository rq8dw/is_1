# ИС «Организации» — лабораторная работа №1

Информационная система для управления объектами `Organization` (вместе с `Coordinates` и `Address`).

**Стек:** Java 17, Spring Boot 3.5 (Spring MVC, WebSocket), Hibernate 6 (JPA), PostgreSQL, Flyway;
клиент — HTML/CSS/JavaScript без сборки (ES-модули), раздаётся самим приложением.

## Структура

```
src/main/java/ru/itmo/is/lab1
├── domain/        сущности JPA: Organization, Coordinates, Address, OrganizationType
├── repository/    уровень хранения: Spring Data JPA + вызовы функций БД (SpecialOperationsRepository)
├── service/       бизнес-логика: CRUD, каскадное удаление, спецоперации, проверки ввода
├── dto/           объекты запросов и ответов REST API
├── web/           Spring MVC контроллеры (REST) и обработка ошибок
└── realtime/      рассылка изменений клиентам по WebSocket после коммита транзакции
src/main/resources
├── db/migration/  V1 — таблицы и ограничения, V2 — функции БД для спецопераций
└── static/        интерфейс: index (таблица), create, view, special, related
```

## Как выполнены требования

| Требование | Реализация |
|---|---|
| Ограничения полей на уровне ORM | Bean Validation и `@Column(nullable = false)` в `domain/*` |
| Ограничения полей на уровне БД | `NOT NULL` / `CHECK` в `V1__schema.sql` (x ≤ 549, > 0, непустое name, допустимые type) |
| id генерирует БД | `GENERATED ALWAYS AS IDENTITY` + `@GeneratedValue(strategy = IDENTITY)` |
| creationDate генерируется автоматически | `@PrePersist` в `Organization` (+ `DEFAULT now()` в БД) |
| Создание / просмотр по ID / изменение / удаление в отдельных окнах | страницы `create.html`, `view.html`, модальные окна изменения и подтверждения удаления |
| Связывание с уже существующими объектами | в форме для координат и адресов можно выбрать «Новые» или «Существующие» |
| Таблица с пагинацией, сортировкой и фильтрацией | `index.html`; фильтры по строковым колонкам — только полное совпадение, сортировка по клику на заголовок |
| Изменение из таблицы и из визуализации объекта | кнопка «Изменить» в строке таблицы и на странице `view.html` |
| Автообновление у всех клиентов | WebSocket `/ws/updates`: событие рассылается после коммита (`ChangeBroadcaster`) |
| Удаление связанных объектов | при удалении организации её координаты и адреса удаляются, если ими больше никто не пользуется; при удалении координат или адреса (`related.html`) удаляются ссылающиеся на них организации |
| Спецоперации как функции БД | `V2__special_operations.sql`, вызываются из `SpecialOperationsService` через `SpecialOperationsRepository` |
| Информирование о некорректном вводе | проверки в браузере (в числовые поля нельзя ввести буквы) + серверная валидация; ошибки показываются у конкретных полей |
| Навигация через меню | верхнее меню на всех страницах |

Порядок типов для операции «type меньше заданного» — порядок объявления enum:
`GOVERNMENT < TRUST < PRIVATE_LIMITED_COMPANY`.
При поглощении сотрудники поглощаемой организации добавляются к поглощающей, годовые обороты суммируются,
поглощаемая организация удаляется.

## Запуск локально

Нужны JDK 17+ и PostgreSQL. Схему БД при старте создаёт Flyway.

```bash
# база данных
psql -U postgres -c "CREATE DATABASE is_lab1"

# сборка (или gradle bootJar, если Gradle установлен)
./gradlew bootJar

# запуск: параметры подключения берутся из переменных окружения
DB_URL=jdbc:postgresql://localhost:5432/is_lab1 DB_USER=postgres DB_PASSWORD=123 PORT=8080 \
  java -jar build/libs/is-lab1.jar
```

Затем откройте http://localhost:8080. Значения по умолчанию: `localhost:5432/is_lab1`,
пользователь `postgres`, пароль `123`, порт `8080`.

В PowerShell переменные задаются так: `$env:DB_PASSWORD='123'; $env:PORT='8080'; java -jar build/libs/is-lab1.jar`.

> Если Maven Central недоступен (ответ 403), зависимости берутся из зеркала
> `maven-central.storage-download.googleapis.com`, оно уже прописано в `settings.gradle`.

## Запуск на helios (кафедральный сервер)

Автоматически (вход по ключу `~/.ssh/helios_key`, пароль к БД берётся на сервере из `~/.pgpass`):

```powershell
.\deploy\deploy-helios.ps1            # собрать, залить, перезапустить и открыть туннель
.\deploy\deploy-helios.ps1 -Tunnel    # только туннель: http://localhost:24672/
.\deploy\deploy-helios.ps1 -Status    # состояние и хвост лога; -Stop — остановить
```

Вручную:

```bash
# 1. собрать jar локально и скопировать на сервер
scp -P 2222 build/libs/is-lab1.jar sXXXXXX@se.ifmo.ru:~/

# 2. на helios: профиль helios подключается к pg:5432/studs
ssh -p 2222 sXXXXXX@se.ifmo.ru
DB_USER=sXXXXXX DB_PASSWORD=<пароль> PORT=<свободный порт> \
  java -Xmx512m -jar is-lab1.jar --spring.profiles.active=helios

# 3. на своём компьютере: проброс порта и открыть http://localhost:8080
ssh -p 2222 -L 8080:localhost:<порт на helios> sXXXXXX@se.ifmo.ru
```

Если таблицы нужно создать в определённой схеме, укажите её в URL:
`DB_URL=jdbc:postgresql://pg:5432/studs?currentSchema=sXXXXXX`.

## Отчёт и UML-диаграммы

- `docs/uml/*.puml` — исходники диаграмм пакетов и классов (PlantUML), рядом лежат готовые SVG.
- `docs/report/report.pdf` — отчёт: задание, диаграммы, исходный код (ссылка и листинги), выводы.

Пересборка отчёта с данными титульного листа (нужен Chrome или Edge):

```powershell
powershell -ExecutionPolicy Bypass -File docs/report/build-report.ps1 `
    -Student "Фамилия Имя Отчество" -Group "P3XXX" -Teacher "Фамилия И. О." -Variant "N" `
    -RepoUrl "https://github.com/<user>/is-lab1"
```

Если передать `-PlantUmlJar путь\к\plantuml.jar`, диаграммы будут заново отрисованы из `.puml`.

## REST API

| Метод | Путь | Назначение |
|---|---|---|
| GET | `/api/organizations?page&size&sort&direction&name&fullName&type&officialZipCode&postalZipCode` | страница таблицы |
| GET / PUT / DELETE | `/api/organizations/{id}` | получить / изменить / удалить |
| POST | `/api/organizations` | создать |
| GET, DELETE | `/api/coordinates`, `/api/coordinates/{id}` | координаты (с числом использований) |
| GET, DELETE | `/api/addresses`, `/api/addresses/{id}` | адреса (с числом использований) |
| GET | `/api/special/count-by-type?type=` | количество с type = заданному |
| GET | `/api/special/count-full-name-greater?fullName=` | количество с fullName > заданного |
| GET | `/api/special/type-less-than?type=` | организации с type < заданного |
| POST | `/api/special/organizations/{id}/employees` | добавить сотрудника |
| POST | `/api/special/absorb` `{"absorberId":1,"absorbedId":2}` | поглощение |

Ссылка на связанный объект в теле запроса: `{"id": 5}` — существующий объект, `{"x": 1, "y": 2}` или
`{"zipCode": "190000"}` — новый; `null` у адреса — адреса нет.
