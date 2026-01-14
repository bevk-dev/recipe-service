# recipe-service

Kratek opis
- Upravlja katalog receptov, integracijo OpenAI za avtomatski uvoz receptov z URL naslova. Shrani podatke v Postgres in pošilja dogodke v Kafka. Storitev privzeto posluša na portu `8084`.

Gradnja

```bash
# v mapi recipe-service
./mvnw clean package -DskipTests
docker build -t <your-registry>/recipe-service:latest .
```

Zagon
- Lokalno z Docker Compose: `shopsync-infra/docker-compose.yml` (izpostavljen kot `8084:8084`).
- Kubernetes manifests: `shopsync-infra/k8s/recipe-service`.

env spremenljivke
- `SPRING_DATASOURCE_URL` — JDBC povezava na `recipe_db`.
- `SPRING_KAFKA_BOOTSTRAP_SERVERS` — Kafka bootstrap server.
- `OPENAI_API_KEY` — če uporabljate OpenAI integracijo, nastavite kot tajno spremenljivko.

Kje iskati konfiguracijo
- `src/main/resources/application.properties`

Centralna dokumentacija sistema
- Za arhitekturo celotnega sistema in pregled vseh storitev glej `shopsync-infra/DOCUMENTATION.md`.
