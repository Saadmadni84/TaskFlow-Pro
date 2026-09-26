# TaskFlow Pro

A dependency-aware workflow and DAG scheduling platform engineered around a deterministic graph engine.

## Overview

TaskFlow Pro is designed to manage complex task workflows where task execution readiness and schedules depend strictly on directed acyclic graph (DAG) relationships. 

The core architectural invariant of the system is:

> **The deterministic DAG/dependency engine is the source of truth.**  
> The frontend never decides whether a dependency is valid, whether a task is Ready or Blocked, or how dates propagate downstream. The backend domain layer owns and validates all dependency rules.

## Architecture

TaskFlow Pro is structured as a modular monolith:

```text
                    TaskFlow Pro
                         |
              ┌──────────┴──────────┐
              |                     |
          Kanban UI            DAG Engine
                                    |
                         ┌──────────┼──────────┐
                         |          |          |
                    Dependency   Readiness  Scheduling
                    Validation   Engine      Engine
```

- **Frontend (`/frontend`)**: Next.js 14, React, TypeScript, and Tailwind CSS. Focuses purely on visual state representation, user interaction, and dispatching commands to the backend.
- **Backend (`/backend`)**: Java 21, Spring Boot 3, and Spring Data JPA. Organized in domain-oriented packages (`common`, `task`, `dependency`, `scheduling`, `criticalpath`, `ai`).
- **Database (`docker-compose.yml`)**: PostgreSQL 16 containerized with Flyway schema migration management.

## Technology Stack

### Backend
- **Language**: Java 21
- **Framework**: Spring Boot 3.3.4 (Spring Web, Spring Data JPA, Bean Validation)
- **Database Migrations**: Flyway
- **Driver**: PostgreSQL JDBC
- **Testing**: JUnit 5, Mockito, AssertJ, H2 (test profile)

### Frontend
- **Framework**: Next.js 14 (App Router)
- **Language**: TypeScript (strict mode)
- **Styling**: Tailwind CSS (custom semantic state system)
- **Icons / UI**: Custom minimal atoms (`Badge`, `Button`, `Card`)

### Infrastructure
- **Containerization**: Docker & Docker Compose
- **Database**: PostgreSQL 16 Alpine

## Repository Structure

```text
taskflow-pro/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/taskflow/taskflow/
│   │   │   │       ├── TaskFlowApplication.java
│   │   │   │       ├── common/                 # Configs, exception handling, error response contracts
│   │   │   │       ├── task/                   # Task module foundation
│   │   │   │       ├── dependency/             # Dependency module foundation
│   │   │   │       ├── scheduling/             # Scheduling module foundation
│   │   │   │       ├── criticalpath/           # Critical path module foundation
│   │   │   │       └── ai/                     # AI suggestions module foundation
│   │   │   └── resources/
│   │   │       ├── db/migration/
│   │   │       │   └── V1__init.sql            # Flyway baseline migration
│   │   │       └── application.yml             # Spring configuration with env substitution
│   │   └── test/
│   │       ├── java/                           # Unit and integration test suites
│   │       └── resources/
│   │           └── application-test.yml        # Test configuration
│   ├── pom.xml
│   └── README.md
│
├── frontend/
│   ├── app/                                    # Next.js App Router layout and pages
│   ├── components/
│   │   ├── ui/                                 # Reusable UI atoms (Badge, Button, Card)
│   │   └── layout/                             # Header and Sidebar shell
│   ├── hooks/                                  # React hooks
│   ├── lib/
│   │   ├── api/                                # Typed API client and task API service
│   │   └── utils/                              # Utility helpers (cn)
│   ├── types/                                  # TypeScript domain interfaces
│   ├── public/                                 # Static assets
│   ├── package.json
│   ├── tsconfig.json
│   └── README.md
│
├── docker-compose.yml                          # PostgreSQL container setup
├── .env.example                                # Template for local environment variables
├── .gitignore                                  # Git exclusion rules
└── README.md                                   # Root documentation
```

## Local Development

### 1. Prerequisites
- Java 21+
- Node.js 20+ and npm 10+
- Docker & Docker Compose

### 2. Configure Environment
Copy the sample environment variables:
```bash
cp .env.example .env
```

### 3. Start Database
```bash
docker compose up -d postgres
```
Verify the container status:
```bash
docker compose ps
```

### 4. Run Backend
```bash
cd backend
./mvnw spring-boot:run
```
The backend server runs on `http://localhost:8080`.

### 5. Run Frontend
```bash
cd frontend
npm install
npm run dev
```
The frontend application runs on `http://localhost:3000`.

## Backend

The backend enforces single-responsibility architecture across domain boundaries:
- **`common`**: Global exception hierarchy (`ApiException`, `ResourceNotFoundException`, `DomainException`, `ValidationException`), structured JSON response format (`ErrorResponse`), and CORS configuration.
- **`task`**: Task entities, lifecycle state transitions, and task CRUD (Phase 2).
- **`dependency`**: Graph validation, cycle detection, edge management, and Ready/Blocked readiness calculation (Phase 2+).
- **`scheduling`**: Downstream date propagation and non-compounding schedule shift calculation (Phase 3).
- **`criticalpath`**: Longest dependency path and float/slack calculation (Phase 4).
- **`ai`**: Dependency suggestion generation and human approval flow (Phase 5).

## Frontend

The frontend adopts a dark, minimal, professional aesthetic with zero uncontrolled `any` types. All API requests are encapsulated in dedicated service modules (`lib/api/*`) rather than making ad-hoc fetch calls inside React components.

Semantic state styling conveys workflow states:
- **Ready**: Emerald green badge / border indicating predecessor tasks are satisfied.
- **Blocked**: Rose red badge / border indicating predecessor tasks are pending.
- **Warning**: Amber badge for scheduling conflicts or impending deadline issues.
- **Neutral**: Zinc badge for backlog or unscheduled items.

## Database

PostgreSQL 16 is managed using Flyway migrations located in `backend/src/main/resources/db/migration/`.
- `V1__init.sql`: Sets up the initial database schema baseline.
- Future schema migrations (`V2__...`, `V3__...`) will introduce task and dependency tables in upcoming phases.

## Environment Variables

Defined in `.env.example`:

| Variable | Description | Default |
|---|---|---|
| `POSTGRES_DB` | PostgreSQL database name | `taskflow` |
| `POSTGRES_USER` | PostgreSQL user | `taskflow` |
| `POSTGRES_PASSWORD` | PostgreSQL password | `change_me` |
| `POSTGRES_PORT` | PostgreSQL host port | `5432` |
| `POSTGRES_HOST` | PostgreSQL host | `localhost` |
| `SERVER_PORT` | Backend application port | `8080` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `dev` |
| `NEXT_PUBLIC_API_URL` | Frontend API target | `http://localhost:8080/api` |

## Testing

### Backend Tests
Runs both context initialization, structured exception handler verification, and database integration:
```bash
cd backend
./mvnw test
```

### Frontend Build & Lint Verification
```bash
cd frontend
npm run lint
npm run build
```

## Future Modules

### Implemented (Phase 1)
- [x] Monorepo structure and directory conventions
- [x] Spring Boot 3 modular monolith foundation with Java 21
- [x] Global structured JSON error handling and HTTP status mapping
- [x] PostgreSQL 16 containerization with Docker Compose and healthcheck
- [x] Flyway migration infrastructure with initial baseline
- [x] Real database connection integration testing
- [x] Next.js 14 TypeScript application shell with dark theme and semantic design system
- [x] Typed API client isolation layer

### Planned (Upcoming Phases)
- [ ] **Phase 2**: Task & Dependency domain models, PostgreSQL relational schema, cycle detection, and readiness state calculation
- [ ] **Phase 3**: Downstream schedule propagation engine and interactive four-column Kanban board with `dnd-kit`
- [ ] **Phase 4**: Critical path analysis and schedule float calculation
- [ ] **Phase 5**: AI-assisted dependency suggestion engine with human approval workflow
