---
applyTo: "src/main/resources/application.properties,src/main/resources/application*.yml,src/main/resources/application*.yaml,docker-compose*.yml,Dockerfile,.github/workflows/*.yml,.env*"
---

Этот файл влияет на прод-окружение. Правила ревью:

- Сравни head и base версии `application.properties`: перечисли добавленные, удалённые и изменённые property-ключи и env-переменные `${...}`. Для каждой добавленной без дефолта явно напиши: «нужно проставить на проде до мержа».
- В summary ревью выведи полный список env-переменных из head-версии файла, разбитый на «без дефолта» и «с дефолтом».
- Новый `${VAR:default}` для URL, id, токенов, ключей интеграций или флагов — severity High: проектное правило запрещает дефолты в конфиге, значение должно приходить из окружения.
- Захардкоженный секрет или токен в любом из этих файлов — High.
- Изменение `server.port`, `management.*`, путей actuator, `spring.flyway.*`, `spring.jpa.hibernate.ddl-auto` — проверь, что фронт, reverse proxy и healthcheck контейнера это переживут.
- В `docker-compose.yml` сервис зависит от `postgres`, который в файле не описан; если PR трогает compose, проверь, что зависимости и `env_file` согласованы.
- В `deploy.yml` любые изменения тегов образа или реестра ghcr — напомни, что прод тянет тег `latest`.
