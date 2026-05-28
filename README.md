# Bakeaura

Monorepo structure:

- `backend/` - Spring Boot backend
- `frontend/` - Frontend application
- `docker-compose.yml` - local multi-service orchestration
- `.env` - compose-level environment variables

## Run with Docker Compose

1. Update values in root `.env` if needed.
2. Build and start:

```bash
docker compose up --build
```

3. Services:

- Backend: `http://localhost:8080`
- Postgres: `localhost:5433`
- Redis: `localhost:6379`
