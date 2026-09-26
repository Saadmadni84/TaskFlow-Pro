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
- **Backend (`/backend`)**: Java 21, Spring Boot 3, and Spring Data JPA. Organized in domain-oriented packages (`common`, `project`, `task`, `dependency`, `scheduling`, `criticalpath`, `ai`).
- **Database (`docker-compose.yml`)**: PostgreSQL 16 containerized with Flyway schema migration management.

## Domain Model

TaskFlow Pro organizes work in projects, tasks, and directed dependency edges:

```text
Project
   |
   └── Tasks
          |
          └── Task Dependencies
                  |
                  └── predecessor → successor
```

### Invariant & Status Separation

A fundamental rule of TaskFlow Pro is separating user workflow progression from topological readiness:

```text
Workflow Status (User-controlled column placement)
--------------------------------------------------
BACKLOG
IN_PROGRESS
REVIEW
DONE

Dependency Status (System-derived readiness state)
--------------------------------------------------
READY
BLOCKED
```

> **Important**: Dependency status (`READY` / `BLOCKED`) is strictly **derived from prerequisite task completion in the DAG**. It is not a user-controlled Kanban status. A task can be `IN_PROGRESS + BLOCKED` or `BACKLOG + READY`.

### Entity-Relationship Diagram

```mermaid
erDiagram
    PROJECT ||--o{ TASK : contains
    TASK ||--o{ TASK_DEPENDENCY : predecessor
    TASK ||--o{ TASK_DEPENDENCY : successor

    PROJECT {
        uuid id PK
        string name
        string description
        timestamp created_at
        timestamp updated_at
    }

    TASK {
        uuid id PK
        uuid project_id FK
        string title
        text description
        string workflow_status
        string dependency_status
        date start_date
        date due_date
        integer duration_days
        bigint version
        timestamp created_at
        timestamp updated_at
    }

    TASK_DEPENDENCY {
        uuid id PK
        uuid predecessor_task_id FK
        uuid successor_task_id FK
        timestamp created_at
    }
```

## Dependency Graph Engine (DAG)

The DAG engine is the deterministic source of truth for graph topology. It is decoupled from Spring Data, web controllers, React, and scheduling math.

### In-Memory Graph Representation
- **Isolation**: When evaluating or traversing dependencies, the relevant project subgraph is loaded into memory in a single query (`DependencyGraphBuilder`), preventing N+1 database queries.
- **Node & Edge Indexes**: Maintained as `Map<UUID, Set<UUID>>` for both outgoing (`successors`) and incoming (`predecessors`) edges, enabling $O(1)$ adjacency lookups.
- **Immutability & Safety**: Collections returned from the graph are defensive, unmodifiable copies.

### Algorithms & Complexity

#### 1. Cycle Detection ($O(V + E)$)
- **Full Graph Inspection**: Implemented via depth-first search (DFS) with a three-color state machine (`UNVISITED`, `VISITING`, `VISITED`). Encountering a node currently in the `VISITING` state identifies a directed back-edge, confirming a cycle.
- **Targeted Edge Pre-Validation**: Before creating edge $A \rightarrow B$, the engine evaluates if $B$ can already reach $A$ via graph traversal. If reachable, adding $A \rightarrow B$ is rejected with `CycleDetectedException` before database writes occur, ensuring transactional integrity.

#### 2. Topological Sorting ($O(V + E)$)
- **Kahn's Algorithm**: Nodes with zero in-degree are processed iteratively while decrementing successor in-degrees.
- **Deterministic Tie-Breaking**: When multiple nodes have zero in-degree simultaneously, a priority queue resolves ties via standard natural UUID lexical order. Identical graphs produce identical, deterministic execution orders every time.
- **Cycle Guard**: If the processed node count is less than the total node count, Kahn's algorithm confirms a cycle and aborts.

#### 3. Traversal ($O(V + E)$)
- **Descendant Traversal**: Traverses all downstream nodes using BFS, ensuring converging dependencies (e.g. $A \rightarrow B \rightarrow D$ and $A \rightarrow C \rightarrow D$) include shared successor $D$ exactly once without duplicate processing.
- **Ancestor Traversal**: Traverses upstream prerequisites via incoming edges.
- **Affected Subgraph**: Produces the set of all downstream tasks affected by a change, ordered in topological sequence for schedule recalculation.

## Dependency Readiness

TaskFlow Pro strictly separates user workflow progression from topological execution readiness:

- **Workflow status is user-controlled** (`BACKLOG`, `IN_PROGRESS`, `REVIEW`, `DONE`): Users freely move tasks across Kanban columns.
- **Dependency status is server-derived** (`READY`, `BLOCKED`): Derived purely by the backend engine from the workflow completion of prerequisite tasks. Clients cannot manually set dependency status.

### Authoritative Readiness Rules

For any task $T$, let $P(T)$ be its set of direct prerequisites/predecessors:

1. **`READY`**:
   - $P(T)$ is empty (the task has no prerequisites), **OR**
   - Every predecessor in $P(T)$ has `workflowStatus == DONE` and is itself satisfied (`READY`).
2. **`BLOCKED`**:
   - At least one predecessor in $P(T)$ has `workflowStatus != DONE` or is itself `BLOCKED`.

> **Invariant**: Readiness is never derived from predecessor `dependencyStatus` in isolation. It authoritative requires that all prerequisites have achieved `workflowStatus == DONE`.

### Flow of Readiness Through the Graph

```mermaid
flowchart TD
    A["Database Schema"] --> B["Backend API"]
    B --> C["Integration Tests"]

    A -->|DONE| B
    B -->|DONE| C
```

1. **Initial State**:
   - $A$: `workflowStatus = IN_PROGRESS`, `dependencyStatus = READY` (no prerequisites)
   - $B$: `workflowStatus = BACKLOG`, `dependencyStatus = BLOCKED` (waiting on $A$)
   - $C$: `workflowStatus = BACKLOG`, `dependencyStatus = BLOCKED` (waiting on $B$)
2. **Upstream Completion**:
   - When $A$ transitions to `DONE`, the readiness engine locates $A$'s affected descendants ($[B, C]$) and evaluates them in strict **topological order**.
   - $B$'s prerequisites are now satisfied $\rightarrow B$ transitions to `READY`.
   - $C$'s prerequisite $B$ is not yet `DONE` $\rightarrow C$ remains `BLOCKED`.
3. **Multi-Level Unlock**:
   - When $B$ transitions to `DONE`, $C$'s prerequisites are satisfied $\rightarrow C$ transitions to `READY`.
4. **Downstream Rollback**:
   - If $A$ is reopened (`DONE -> IN_PROGRESS`), the engine traverses the affected subgraph in topological order.
   - $B$ is recalculated to `BLOCKED`.
   - Because $B$'s prerequisite chain is broken, $C$ is immediately recalculated to `BLOCKED`.
   - Rollback propagates throughout the entire downstream graph—preventing stale `READY` states.
5. **Converging Dependencies & Idempotency**:
   - Converging successors (e.g. $B \rightarrow D, C \rightarrow D$) are evaluated exactly once in topological order.
   - Only tasks whose `dependencyStatus` actually changes are updated and persisted, preventing database write churn and unnecessary optimistic-lock version increments.

## Scheduling Engine

TaskFlow Pro implements a deterministic, constraint-based scheduling engine that automatically propagates upstream date changes downstream through the DAG without compounding delays.

### Date Semantics & Model

1. **`plannedStartDate`**: The user's independent planned schedule baseline.
2. **`durationDays`**: Number of inclusive calendar days occupied by the task ($durationDays \ge 1$). Duration is strictly preserved across all shifts.
3. **`scheduledStartDate`**: The effective start date after applying all prerequisite dependency constraints.
4. **`scheduledDueDate`**: The effective completion date calculated as:
   $$\text{scheduledDueDate} = \text{scheduledStartDate} + \text{durationDays} - 1\text{ day}$$

### The Scheduling Invariant

For every directed dependency $P \rightarrow S$ ($P$ is predecessor, $S$ is successor):

$$\text{successor.scheduledStartDate} \ge \max_{P \in \text{predecessors}} (P.\text{scheduledDueDate} + 1\text{ day})$$

$$\text{successor.scheduledStartDate} \ge \text{successor.plannedStartDate}$$

$$\text{successor.scheduledDueDate} = \text{successor.scheduledStartDate} + \text{successor.durationDays} - 1\text{ day}$$

### Why Delays Do Not Compound (Converging Paths)

In a diamond / converging dependency graph:

```mermaid
flowchart TD
    A["Task A"] --> B["Task B"]
    A --> C["Task C"]
    B --> D["Task D"]
    C --> D
```

Suppose task $A$ is delayed by $+3$ days:
- Both $B$ and $C$ depend directly on $A$, so their scheduled dates shift by $+3$ days.
- Successor $D$ depends on both $B$ and $C$.
- Rather than summing delays ($+3$ from $B$ and $+3$ from $C = +6$), the engine computes the constraint:
  $$D.\text{scheduledStartDate} = \max(D.\text{plannedStartDate}, B.\text{scheduledDueDate} + 1, C.\text{scheduledDueDate} + 1)$$
- Since both $B$ and $C$ finish on the same shifted date, $\max(B.\text{due} + 1, C.\text{due} + 1)$ shifts $D$ by **exactly $+3$ days**, completely preventing compound delay accumulation.

### Baseline Recomputability

Because schedules are derived from:
$$\text{plannedStartDate} + \text{current dependency constraints}$$
if an upstream task $A$ is moved earlier, downstream successors can return to their independent baseline planned schedule without relying on historical delta records or accumulated state drift.

## Dependency Impact Preview

TaskFlow Pro provides a high-fidelity **Dependency Impact Preview** capability. Before committing a task schedule mutation, clients can query the system to evaluate the exact downstream schedule consequences:

> *"If I make this proposed schedule change, which downstream tasks will be affected, what will their new scheduled dates be, and why?"*

### Conceptual Architecture & Execution Flow

```text
               Proposed date
                     ↓
            Affected descendants (DAG)
                     ↓
             Topological ordering
                     ↓
         In-memory schedule simulation
       (Shared ScheduleCalculationService)
                     ↓
          Compare current vs proposed
                     ↓
     Explain constraints & binding predecessors
                     ↓
               Return preview
```

### Core Architectural Guarantees

1. **Side-Effect Free Simulation**:
   - The preview endpoint (`POST /api/tasks/{taskId}/schedule/preview`) executes with `@Transactional(readOnly = true)`.
   - Never writes to the database, never modifies JPA entities, never mutates `updatedAt`, never increments `@Version`, and publishes zero domain events.
   - All evaluations occur in memory on lightweight simulation projections. The preview can be called repeatedly and safely in real-time as a user drags dates or types input.

2. **Single Source of Truth (Zero Calculation Drift)**:
   - Preview and actual mutation (`PUT /api/tasks/{id}`) invoke the **exact same deterministic engine**: `ScheduleCalculationService`.
   - The preview exactly predicts the committed database state, eliminating discrepancies where a preview promises one date but commit produces another.

3. **Descendant Discovery & Project Graph Scope**:
   - Uses the DAG traversal engine to extract the `AffectedSubgraph` for the source task.
   - Unrelated components (e.g. $X \rightarrow Y \rightarrow Z$ when modifying $A \rightarrow B \rightarrow C$) are excluded from recalculation and response payloads.
   - Single-batch loading (`findByProjectId`) fetches tasks efficiently in $O(V + E)$ without $N+1$ database roundtrips.

4. **Converging Path Non-Compounding Evaluation**:
   - In diamond graphs ($A \rightarrow B \rightarrow D$, $A \rightarrow C \rightarrow D$), shifting $A$ by $+3$ days shifts $B$ by $+3$ days, $C$ by $+3$ days, and $D$ by $+3$ days.
   - The engine computes $\max(B.\text{due} + 1, C.\text{due} + 1)$ rather than adding delays together ($+3 + 3 = +6$).

5. **Binding Predecessor Identification**:
   - Successors determine their earliest allowed start from the maximum required start across all direct predecessors:
     $$\text{latestConstraint} = \max_{P \in \text{predecessors}} (P.\text{scheduledDueDate} + 1\text{ day})$$
   - Every predecessor $P$ where $P.\text{scheduledDueDate} + 1 == \text{latestConstraint}$ is identified as a **binding predecessor** (`constraintSourceTaskIds`).
   - If multiple predecessors finish on the same maximum date, all binding predecessors are reported.

6. **Baseline vs. Constraint Disambiguation**:
   - If a successor's independent `plannedStartDate` is later than or equal to the dependency constraint date, the task is classified as `PLANNED_DATE_DOMINANT`.
   - The preview clearly explains that the task's schedule is unchanged because its planned schedule is already later than the dependency requirement, rather than falsely claiming a dependency delay.

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
| `AI_ENABLED` | Enable AI-assisted dependency suggestion engine | `false` |
| `AI_PROVIDER` | AI provider strategy (`gemini`, `noop`) | `gemini` |
| `AI_MODEL` | LLM model identifier | `gemini-1.5-flash` |
| `AI_API_KEY` | Provider API key | (empty) |
| `AI_TIMEOUT` | Provider HTTP timeout in seconds | `10` |
| `AI_MAX_SUGGESTIONS` | Maximum candidate suggestions returned | `10` |

## AI-Assisted Dependency Suggestions

TaskFlow Pro includes an **AI-Assisted Dependency Suggestion Engine** designed to assist engineering teams in discovering potential dependencies across complex project backlogs.

### 1. Architectural Principle: AI Proposes, Human Approves, Deterministic Engine Validates

The AI engine is an **advisory assistant**, never an autonomous decision maker:
- **No Direct Graph Mutation**: The AI cannot write to the PostgreSQL database, mutate task dependencies, or alter scheduling.
- **Human-in-the-Loop**: Suggestions are ephemeral candidate proposals presented to the user. A dependency is only persisted when a human user explicitly accepts it via `POST /api/dependency-suggestions/accept`.
- **Deterministic Engine Authority**: All accepted suggestions are routed through the authoritative `TaskDependencyService`, subjecting the proposed edge to cycle detection, self-dependency checks, project isolation, duplicate checks, readiness recalculation, and topological scheduling.

```text
Task Context
     ↓
AI Suggestion Provider
     ↓
Candidate Dependencies
     ↓
Human Review
     ↓
Deterministic Dependency Service
     ↓
DAG Validation
     ↓
Persistence
     ↓
Readiness + Scheduling
```

### 2. Context Grounding & Privacy
To avoid hallucinated dependencies and protect project data:
- **Project Scoped**: Only tasks belonging to the exact same project as the target task are included in the prompt context. Cross-project tasks are never sent to the model.
- **Minimal Surface**: The LLM receives only task titles, bounded descriptions (max 500 characters), workflow status, and existing dependency edges. No credentials, user identities, database connection details, or unrelated project data are ever transmitted.
- **Untrusted Input Defense**: All task titles and descriptions are treated as untrusted data. The prompt explicitly instructs the model to ignore prompt injection attempts or system command overrides embedded inside task text.

### 3. Structured Output & Validation
- Suggestions are returned as structured JSON containing `predecessorTaskId`, `successorTaskId`, normalized `confidence` (`0.0 <= confidence <= 1.0`), and a concise explanatory `reason`.
- The server independently validates all model output:
  1. Both predecessor and successor UUIDs must exist in the database and belong to the same project.
  2. Self-dependencies (`A -> A`) are discarded.
  3. Existing project dependencies (`A -> B`) are discarded.
  4. Suggestions that would create a directed cycle (`C -> A` when `A -> B -> C` exists) are filtered before presentation and strictly rejected during acceptance.
  5. Invalid confidence values or blank reasons are rejected.

### 4. Resilience & Offline Mode
- Core TaskFlow Pro operations (task CRUD, dependency creation, cycle detection, readiness calculation, scheduling, impact preview) **operate completely independently of AI**.
- When `AI_ENABLED=false` or when the provider is unreachable (network timeout, rate limit 429, 5xx server error), the system degrades gracefully with standard error responses (`AI_DISABLED` or `AI_UNAVAILABLE`) without impacting application startup or core functionality.

## AI-Tool Declaration

In accordance with platform governance and transparency requirements:
- **Feature**: LLM-assisted task dependency suggestion (`POST /api/tasks/{taskId}/dependency-suggestions` and `POST /api/dependency-suggestions/accept`).
- **Data Transmitted**: Task identifiers, task titles, truncated descriptions, and existing dependency pairs within the same project.
- **Human Approval**: Mandatory. Suggestions are purely informational and are never persisted automatically.
- **Safety Authority**: Deterministic backend graph algorithms (`DependencyGraphBuilder`, `CycleDetectionService`, `GraphTraversalService`) have sole authority over graph validity and persistence.
- **Offline Guarantee**: The platform functions fully without an AI provider or API key configured.

## Critical Path Analysis

TaskFlow Pro provides a pure, deterministic **Critical Path Analysis Engine** implementing the classic Critical Path Method (CPM).

### 1. Conceptual Overview
The critical path is the schedule-determining sequence of dependent tasks that defines the minimum total project duration. Any delay to a critical task directly pushes back the overall project completion date.

The calculation is **100% deterministic** and operates independently of any AI/LLM components, database writes, or HTTP concerns.

### 2. Architecture Diagram

```text
Project
  │
  ├── Tasks
  │
  └── Dependencies
          │
          ↓
    DAG / Topological Sort (Kahn's Algorithm + Deterministic Tie-Breaking)
          │
          ↓
      Forward Pass (ES, EF = ES + duration - 1)
          │
          ↓
   Project Completion Date = max(EF of all terminal tasks)
          │
          ↓
      Backward Pass (LF = min(succ.LS - 1), LS = LF - duration + 1)
          │
          ↓
        Total Slack = ChronoUnit.DAYS.between(ES, LS)
          │
          ↓
   Critical Tasks (Slack == 0) & Critical Paths Extraction
```

### 3. Core Terminology & Inclusive Date Semantics
- **Earliest Start (ES)**: Earliest calendar date a task can begin, constrained by predecessor completion:
  `ES = max(predecessor.EF + 1 day)` (or task's base scheduled/planned start for root tasks).
- **Earliest Finish (EF)**: Earliest date the task can complete:
  `EF = ES + durationDays - 1 day` (inclusive date convention established in Phase 5).
- **Project Completion Date**: The latest earliest finish among all terminal tasks (out-degree 0):
  `projectCompletionDate = max(terminal.EF)`.
- **Latest Finish (LF)**: The latest date a task can finish without delaying project completion:
  `LF = projectCompletionDate` for terminal tasks; `LF = min(successor.LS - 1 day)` for non-terminal tasks.
- **Latest Start (LS)**: The latest date a task can start without delaying project completion:
  `LS = LF - durationDays + 1 day`.
- **Total Slack**: The number of calendar days a task can be delayed without extending project completion:
  `totalSlackDays = ChronoUnit.DAYS.between(ES, LS) == ChronoUnit.DAYS.between(EF, LF)`.
- **Critical Task**: A task whose total slack is exactly zero (`totalSlackDays == 0`).
- **Critical Path**: A directed, unbroken sequence of critical tasks from a critical root to a critical terminal task.

### 4. Example: Branching & Converging Paths
Consider a project with tasks A, B, C, D and start date June 1:
```text
      ┌──> B (5d) ──┐
A (3d)│             ├──> D (4d)
      └──> C (2d) ──┘
```

1. **Forward Pass**:
   - `A` (duration 3d): ES = June 1, EF = June 3
   - `B` (duration 5d, pred A): ES = June 4, EF = June 8
   - `C` (duration 2d, pred A): ES = June 4, EF = June 5
   - `D` (duration 4d, preds B & C): ES = max(June 8 + 1, June 5 + 1) = June 9, EF = June 12
2. **Project Completion Date**:
   - Terminal task `D`: Completion = June 12.
3. **Backward Pass**:
   - `D`: LF = June 12, LS = June 9 (Slack = 0 -> Critical)
   - `B`: LF = June 8, LS = June 4 (Slack = 0 -> Critical)
   - `C`: LF = June 8, LS = June 7 (Slack = 3 -> Non-critical)
   - `A`: LF = min(June 4 - 1, June 7 - 1) = June 3, LS = June 1 (Slack = 0 -> Critical)
4. **Result**:
   - Critical tasks: `[A, B, D]`
   - Critical path: `["A", "B", "D"]` (duration 3 + 5 + 4 = 12 days)
   - Sub-path `A -> C -> D` has 3 days of float/slack and is non-critical.

### 5. Multiple Critical Paths & Disjoint Subgraphs
- If parallel branches share the same schedule-determining duration (e.g. `duration(B) == duration(C)`), **all critical sequences are preserved and returned** (e.g., `[[A, B, D], [A, C, D]]`).
- Projects with multiple independent roots or disconnected subgraphs are fully evaluated. All project tasks are included in the `tasks` metrics list.
- Reconstructed paths are bounded by a configurable maximum (`MAX_CRITICAL_PATHS = 100`) to prevent exponential path explosion on dense graphs.

### 6. Complexity & Performance
- **Time Complexity**: `O(V + E)` for topological sorting, forward pass, backward pass, and slack calculation. Path reconstruction is bounded by `O(K * V)` where `K` is the number of critical paths.
- **Space Complexity**: `O(V + E)` in memory.
- **Side-Effect Free**: Analytical only. Calling `GET /api/projects/{projectId}/critical-path` performs zero database writes, does not increment entity versions, and leaves task schedules intact.

## Frontend

The TaskFlow Pro frontend is a fast, responsive, production Kanban workflow interface that exposes the deterministic DAG, readiness engine, scheduling engine, impact preview, AI suggestions, and critical path analysis.

### Installation & Development

```bash
cd frontend
npm install --legacy-peer-deps
npm run dev
```

The application runs locally on `http://localhost:3000`.

### Environment Configuration

Configure the backend API URL in `frontend/.env.local` or root `.env`:

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api
# Fallback support
NEXT_PUBLIC_API_URL=http://localhost:8080/api
```

### Frontend Architecture

```text
frontend/
├── app/
│   ├── page.tsx                     # Landing page with Phase 9 launchpad
│   ├── kanban/page.tsx              # Primary production Kanban board
│   ├── projects/[projectId]/page.tsx# Project-scoped Kanban route
│   ├── error.tsx                    # Error boundary for unexpected rendering faults
│   └── ...
├── components/
│   ├── board/
│   │   ├── KanbanWorkspace.tsx      # Master project workspace coordinator
│   │   ├── KanbanBoard.tsx          # 4-column responsive grid
│   │   ├── KanbanColumn.tsx         # Drag target & dynamic task counter
│   │   └── TaskCard.tsx             # Authoritative readiness, dates, accessible menu
│   ├── task/
│   │   ├── TaskCreateDialog.tsx     # Validated task creation dialog
│   │   ├── TaskEditDialog.tsx       # Details edit with impact preview intercept
│   │   └── TaskDeleteDialog.tsx     # Dependency-aware deletion confirmation
│   ├── dependency/
│   │   └── DependencyModal.tsx      # Prerequisites, dependents, cycle error feedback
│   ├── scheduling/
│   │   └── ScheduleImpactPreviewModal.tsx # Downstream simulation with non-compounding reason
│   ├── ai/
│   │   └── AiSuggestionsModal.tsx   # Grounded candidate review with confidence & accept
│   ├── criticalpath/
│   │   └── CriticalPathModal.tsx    # Completion date, zero-slack bottlenecks, multi-path
│   └── ui/
│       ├── Modal.tsx                # Accessible backdrop and keyboard focus management
│       ├── Skeleton.tsx             # Board skeleton loaders
│       ├── Badge.tsx, Button.tsx, Card.tsx
├── hooks/
│   ├── useProjects.ts               # Project listing and selection
│   └── useProjectBoard.ts           # State orchestration, optimistic rollback, server reconciliation
├── lib/
│   ├── api/                         # Typed domain API client modules
│   │   ├── client.ts, projects.ts, tasks.ts, dependencies.ts, scheduling.ts, ai.ts, criticalPath.ts
│   └── utils/
│       └── dates.ts                 # Timezone-safe calendar date arithmetic and formatting
└── types/
    └── index.ts                     # TypeScript domain contracts strictly matching Spring DTOs
```

### Major UI Features

1. **Authoritative 4-Column Workflow**: Columns `Backlog`, `In Progress`, `Review`, `Done`. The frontend never calculates `READY` or `BLOCKED`; it renders the server's `dependencyStatus` and dynamic task counts.
2. **Optimistic Movement with Server Rollback**: Fast drag-and-drop with instant visual feedback; reverts immediately with a clear alert if the server rejects the move or reports a concurrency conflict.
3. **Accessible "Move to..." Navigation**: Full keyboard accessibility via the compact card menu (`•••`), enabling status transitions without requiring drag interactions.
4. **Dependency Impact Preview Flow**: Editing a task's planned start date intercepts with a preview modal, presenting simulated downstream shifts (+X days), constraint sources, and non-compounding path explanations before committing.
5. **Interactive Dependency Manager**: Search and add project prerequisites, remove prerequisites, with real-time cycle detection feedback (`Dependency not added. This dependency would create a cycle in the workflow.`).
6. **AI Dependency Suggestions**: Grounded LLM proposals showing confidence percentage and domain reasoning with explicit human review (`[Accept]` / `[Dismiss]`) and graceful degradation if AI is unavailable.
7. **Critical Path Bottleneck Viewer**: Exposes project completion date, critical tasks (0 slack), float distribution, and renders all parallel critical paths.

---

## User Workflow

The end-to-end user journey follows a deterministic, human-in-the-loop progression:

```text
Create Task
    ↓
Add Dependencies (Prerequisites / Dependents)
    ↓ (Server validates DAG, rejects cycles, calculates initial schedule)
Move Through Kanban (Backlog → In Progress → Review → Done)
    ↓ (Optimistic move with server reconciliation & rollback on error)
Dependency State Updates (Upstream Done unlocks downstream Ready; Reopen blocks successors)
    ↓
Schedule Propagates (Topological non-compounding date calculation)
    ↓
Impact Preview (Planned date change triggers downstream simulation preview before commit)
    ↓
Critical Path Analysis (View zero-slack bottleneck chains and completion date)
    ↓
AI Dependency Suggestions (Semantic suggestions reviewed and explicitly accepted)
```

---

## Testing

### Backend Tests
Runs context initialization, structured exception handler verification, database integration, DAG engine tests, readiness engine tests, scheduling engine tests, impact preview tests, AI suggestion tests, and Critical Path Analysis tests (165 tests):
```bash
cd backend
./mvnw test
```

### Frontend Tests
Runs comprehensive component and hook tests with Vitest and React Testing Library (20 tests across 8 suites):
```bash
cd frontend
npm test
```

### Frontend Build & Lint Verification
```bash
cd frontend
npm run lint
npm run build
```

---

## Implemented Phases

- [x] **Phase 1**: Monorepo foundation, Spring Boot 3 modular monolith (Java 21), Next.js 14 shell, Docker Compose PostgreSQL 16, Flyway baseline, centralized error handling.
- [x] **Phase 2**: Core domain model (`Project`, `Task`, `TaskDependency`), PostgreSQL relational schema via Flyway (`V2__create_core_domain_tables.sql`), optimistic locking, project isolation validation, and persistence test suite.
- [x] **Phase 3**: Deterministic DAG Engine (`DependencyGraph`, DFS cycle detection, Kahn's topological sort with deterministic tie-breaking, reachability, descendant/ancestor traversal, affected subgraph calculation, and transactional cycle prevention).
- [x] **Phase 4**: Dependency Readiness Engine (evaluating prerequisite completion, derived `READY`/`BLOCKED` status propagation in topological order, multi-level unlock, downstream rollback on task reopen, converging graph handling, and edge addition/removal recalculation).
- [x] **Phase 5**: Dependency-Aware Scheduling Engine (constraint-based schedule calculation, non-compounding downstream date propagation, topological schedule recalculation, duration preservation, recomputable baseline schedules, and transactional persistence).
- [x] **Phase 6**: Dependency Impact Preview (side-effect-free in-memory schedule simulation, identical calculation engine reuse, binding predecessor identification, converging path non-compounding explanation, preview/commit consistency verification, and REST preview API).
- [x] **Phase 7**: AI-Assisted Dependency Suggestion Engine (pluggable provider abstraction, Google Gemini REST adapter with JSON schema enforcement, grounded project task context, prompt injection defense, server-side validation against hallucination/cycles/cross-project/self-dependency, human-in-the-loop explicit acceptance flow delegating to deterministic graph engine, offline degradation, and comprehensive unit/integration test suite).
- [x] **Phase 8**: Critical Path Analysis Engine (pure deterministic CPM calculation, forward/backward pass, total slack calculation, critical task identification, bounded multi-path reconstruction, read-only REST API `GET /api/projects/{projectId}/critical-path`, inclusive calendar date arithmetic, and comprehensive test suite).
- [x] **Phase 9**: Production Kanban Frontend and Workflow UI (responsive 4-column Kanban board, authoritative readiness and schedule display, accessible card actions, optimistic UI with server rollback, schedule impact preview intercept modal, dependency management with cycle error reporting, AI suggestion review with explicit acceptance, and multi-path critical path analysis modal).



