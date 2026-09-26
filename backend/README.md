# TaskFlow Pro - Backend

The backend of TaskFlow Pro is a modular monolith built with Java 21, Spring Boot 3, and PostgreSQL, designed to serve as the deterministic source of truth for workflow DAG execution, readiness calculation, and schedule propagation.

## Architecture & Modules

```text
com.taskflow.taskflow/
├── TaskFlowApplication.java
│
├── common/             # Cross-cutting infrastructure: error responses, global exception handling, configs
├── task/               # Task lifecycle, metadata, and state (Phase 2)
├── dependency/         # Directed acyclic graph relationships, cycle detection, readiness calculation (Phase 2+)
├── scheduling/         # Schedule propagation and topological date calculation (Phase 3)
├── criticalpath/       # Critical path analysis and float calculation (Phase 4)
└── ai/                 # LLM-based dependency suggestions and review flow (Phase 5)
```

## Prerequisites

- Java 21+
- Apache Maven 3.9+ (or use `./mvnw`)
- Docker & Docker Compose (for PostgreSQL)

## Configuration

Configuration is managed via `src/main/resources/application.yml` and environment variables:

| Variable | Description | Default |
|---|---|---|
| `POSTGRES_HOST` | Database host | `localhost` |
| `POSTGRES_PORT` | Database port | `5432` |
| `POSTGRES_DB` | Database name | `taskflow` |
| `POSTGRES_USER` | Database user | `taskflow` |
| `POSTGRES_PASSWORD` | Database password | `change_me` |
| `SERVER_PORT` | HTTP Server port | `8080` |

## Building & Testing

```bash
# Run unit and integration tests
./mvnw test

# Package application jar
./mvnw clean package
```

## Running

```bash
# Start PostgreSQL via root docker-compose
docker compose up -d postgres

# Start Spring Boot application
./mvnw spring-boot:run
```
