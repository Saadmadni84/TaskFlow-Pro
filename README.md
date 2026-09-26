# TaskFlow Pro

A dependency-aware workflow and DAG scheduling platform engineered around a deterministic graph engine.

## Overview & Business Value

### The Problem: Why Traditional Kanban Fails at Scale
Traditional task management platforms (Jira, Trello, Linear) manage tasks as flat cards within independent status columns. In real-world software and hardware engineering, work forms an interconnected **Directed Acyclic Graph (DAG)** of hard prerequisites:
- **Manual Dependency Churn**: When Task A slips by 4 days, team leads must manually identify all downstream tasks across multiple boards, calculate new dates, and notify assignees. In large backlogs, prerequisite slips are routinely missed.
- **Premature Execution of Blocked Work**: Developers pick up tickets marked `IN_PROGRESS` or `READY` only to discover hours later that required APIs or database schemas were never completed, resulting in context-switching and wasted engineering hours.
- **Compounding Deadline Inaccuracies**: Naive scheduling tools sum up delays across converging parallel paths ($+3$ days on Path B and $+3$ days on Path C incorrectly yields $+6$ days on Task D), generating false panic and corrupted delivery commitments.

### The Solution: TaskFlow Pro
TaskFlow Pro replaces manual tracking with an **authoritative, mathematical graph engine**:
1. **Continuous Automated Readiness**: When prerequisite tasks finish or reopen, downstream tasks automatically transition between `READY` and `BLOCKED` in topological order.
2. **Deterministic Topological Scheduling**: Schedule adjustments automatically propagate through downstream subgraphs, preserving task durations and calculating valid start dates based on the latest predecessor completion ($\max(\text{predDue} + 1)$).
3. **Non-Compounding Converging Paths**: Parallel paths converging on a shared milestone do not compound delays.
4. **Side-Effect-Free Impact Simulation**: Engineers can simulate proposed schedule mutations in-memory before committing changes to PostgreSQL.
5. **Execution Guardrails**: Tasks in a `BLOCKED` dependency state cannot be marked `DONE`, preserving execution integrity.

---

## The Golden Demo Scenario: The Converging Diamond

The canonical demonstration of TaskFlow Pro's mathematical correctness is the **Converging Diamond DAG**:

```text
         Task B (Duration: 2d)
        ↗                      ↘
Task A (Duration: 3d)            Task D (Duration: 2d)
        ↘                      ↗
         Task C (Duration: 2d)
```

### Deterministic Walkthrough
1. **Initial Baseline**:
   - $A$: June 1 $\rightarrow$ June 3
   - $B$: June 4 $\rightarrow$ June 5 (constrained by $A$)
   - $C$: June 4 $\rightarrow$ June 5 (constrained by $A$)
   - $D$: June 6 $\rightarrow$ June 7 (constrained by $\max(B.\text{due}, C.\text{due}) + 1$)
2. **Move Task A by $+3$ Days (June 1 $\rightarrow$ June 4)**:
   - Click **Impact Preview** to simulate without mutating the database.
   - $B$ shifts $+3$ days $\rightarrow$ June 7 $\rightarrow$ June 8.
   - $C$ shifts $+3$ days $\rightarrow$ June 7 $\rightarrow$ June 8.
   - **Converging Node $D$ shifts by $+3$ days $\rightarrow$ June 9 $\rightarrow$ June 10, NOT $+6$ days.**
3. **Commit & Reload**:
   - Click **Commit Proposed Schedule**. Refresh the page. Every date is persisted identically in PostgreSQL.
4. **Readiness Unlocking & Rollback**:
   - Mark $A$ as `DONE` $\rightarrow B$ and $C$ automatically transition from `BLOCKED` to `READY`.
   - Move $A$ back to `IN_PROGRESS` $\rightarrow B$, $C$, and $D$ immediately roll back to `BLOCKED`.
5. **Blocked $\rightarrow$ Done Protection**:
   - Attempt to mark $D$ as `DONE` while $B$ or $C$ are incomplete $\rightarrow$ The backend rejects the request with HTTP `409 Conflict` (`TASK_BLOCKED`). The Kanban UI disables the action.

---

## Architecture & System Design

```mermaid
flowchart TD
    subgraph Presentation ["Presentation Layer (Next.js 14 / React 18 / TypeScript)"]
        Kanban["Production Kanban Board"]
        DAGCanvas["Visual DAG Canvas (xyflow + dagre)"]
        ImpactUI["Impact Preview Simulator"]
        CPMUI["Critical Path Inspector"]
        AIUI["AI Suggestion Review Modal"]
    end

    subgraph Backend ["Authoritative Domain Layer (Spring Boot 3 Modular Monolith)"]
        REST["REST API Controllers & RFC-7807 Exception Handler"]
        subgraph DomainServices ["Domain Services (Java 21)"]
            TaskSvc["Task Service (Optimistic Locking)"]
            ReadinessSvc["Dependency Readiness Engine"]
            SchedSvc["Topological Scheduling Engine"]
            CPMSvc["Critical Path Method (CPM) Engine"]
            GraphSvc["Deterministic DAG Engine (Kahn's / DFS)"]
        end
        AISvc["AI Advisory Layer (Pluggable Provider)"]
    end

    subgraph Persistence ["Persistence Layer (PostgreSQL 16)"]
        DB[(PostgreSQL 16 + Flyway Migrations V1-V5)]
    end

    subgraph External ["Optional LLM Providers"]
        LLM["Google Gemini / Anthropic Claude / Mock"]
    end

    Presentation -->|REST / JSON| REST
    REST --> DomainServices
    TaskSvc --> ReadinessSvc
    TaskSvc --> SchedSvc
    DomainServices --> GraphSvc
    GraphSvc --> DB
    REST --> AISvc
    AISvc -.->|Read-Only Context| GraphSvc
    AISvc -->|Advisory Query| LLM
    AISvc -->|Human Acceptance Only| TaskSvc
```

### Architectural Decision: Why a Modular Monolith Instead of Microservices
TaskFlow Pro deliberately uses a **modular monolith** architecture rather than microservices:
1. **Strong Consistency Requirements**: Graph cycle detection, readiness status recalculation, and topological schedule propagation require atomic transactions across tasks, dependencies, and projects. Decomposing these into microservices (e.g., a "Task Service", "DAG Service", and "Scheduling Service") would necessitate distributed two-phase commit ($2\text{PC}$) or eventual consistency sagas, introducing high network latency, graph synchronization race conditions, and catastrophic failure modes during concurrent edge creation.
2. **Domain Isolation Without Operational Overhead**: The codebase enforces strict separation of concerns via package-private boundaries (`common`, `task`, `dependency`, `scheduling`, `criticalpath`, `ai`). Services communicate through well-typed Java interfaces and DTOs within the same JVM memory space, delivering sub-millisecond graph traversals.
3. **Transactional Integrity**: All graph and schedule mutations execute within standard Spring `@Transactional` boundaries against PostgreSQL, guaranteeing ACID rollback if a cycle or constraint violation occurs.

- **Frontend (`/frontend`)**: Next.js 14 (App Router), React 18, TypeScript, Tailwind CSS, `@xyflow/react`, and `@dagrejs/dagre`. Responsible solely for visualization, user interaction, and command dispatching.
- **Backend (`/backend`)**: Java 21, Spring Boot 3.3, and Spring Data JPA / Hibernate. Houses all authoritative algorithms and business logic.
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

## Database & Migrations

PostgreSQL 16 is managed using Flyway migrations located in `backend/src/main/resources/db/migration/`:
- `V1__init.sql`: Sets up the initial database schema baseline.
- `V2__create_core_domain_tables.sql`: Core relational tables (`projects`, `tasks`, `task_dependencies`), primary/foreign keys with `ON DELETE CASCADE`, self-loop checks (`CHECK (predecessor_task_id <> successor_task_id)`), and composite indexes.
- `V3__add_task_schedule_baseline.sql`: Scheduling baseline columns (`planned_start_date`, `scheduled_start_date`, `scheduled_due_date`, `duration_days`).
- `V4__seed_demo_data.sql`: Seed dataset defining realistic multi-tier DAG workflows with converging paths.
- `V5__seed_realistic_showcase_demo.sql`: Complete production platform showcase project featuring parallel branches, critical bottlenecks, and independent tasks.

## Seed & Demo Instructions

TaskFlow Pro includes automated, deterministic seed data via Flyway migrations `V4` and `V5`:
1. When starting the database and backend with `docker compose up -d postgres` and `./mvnw spring-boot:run`, Flyway automatically applies migrations and seeds the database.
2. The primary demo project is **"TaskFlow Pro Core Platform"** (`id: a0000000-0000-0000-0000-000000000001`):
   - **Converging Dependencies**: Requirements Analysis (`June 1 → June 3`) $\rightarrow$ Backend Development (`June 4 → June 8`) and Frontend Dashboard (`June 4 → June 6`) $\rightarrow$ Converging Integration Task (`June 9 → June 12`).
   - **Critical Path Bottlenecks**: Long-running paths demonstrate zero float/slack.
   - **Independent Tasks**: Disaster Recovery Runbook operates without dependencies to verify disconnected component handling.
3. Access the demo in your browser at `http://localhost:3000`:
   - Open `/kanban` to view the 4-column production board and drag/update tasks.
   - Open `/graph` to explore the interactive visual DAG canvas with upstream/downstream ancestor tracing.
   - Open `/scheduling` to test topological date propagation.
   - Open `/impact-preview` to simulate schedule shifts side-effect-free.
   - Open `/critical-path` to inspect forward/backward pass CPM slack calculations.
   - Open `/ai-suggestions` to generate LLM dependency recommendations and explicitly accept them.

## Key Assumptions

1. **Deterministic Single Source of Truth**: The Spring Boot backend domain layer is the single authority for graph validation, cycle detection, readiness states, and scheduling. The React frontend is strictly a visualization and command-dispatching interface.
2. **Inclusive Calendar Day Semantics**: All date math operates on inclusive calendar day boundaries:
   $$\text{durationDays} = \text{scheduledDueDate} - \text{scheduledStartDate} + 1$$
   $$\text{scheduledDueDate} = \text{scheduledStartDate} + \text{durationDays} - 1$$
3. **Strict Project Isolation**: Tasks and dependencies exist solely within the boundary of a single `Project`. Cross-project dependencies are explicitly prohibited by relational constraints and domain validators.
4. **Separation of Workflow and Readiness**:
   - `workflowStatus` (`BACKLOG`, `IN_PROGRESS`, `REVIEW`, `DONE`) is controlled by the user.
   - `dependencyStatus` (`READY`, `BLOCKED`) is derived exclusively by the graph engine from prerequisite completion (`workflowStatus == DONE`).
5. **Non-Compounding Delay Invariant**: Delays across parallel branches do not sum up; downstream tasks wait only for the latest predecessor to finish ($\max(\text{predDue} + 1)$).
6. **Human-in-the-Loop AI**: AI suggestions are purely advisory. No graph edge is created without explicit human review and authoritative backend DAG validation.

## Limitations

1. **Discrete Day Granularity**: Scheduling calculations operate at the day granularity level. Sub-hour or minute-based scheduling (e.g. shift work or millisecond cron triggers) is out of scope.
2. **Project Boundary Scope**: Task DAGs cannot span multiple projects. Each project maintains its own isolated dependency graph.
3. **No Automatic AI Mutation**: The AI cannot autonomously modify existing tasks, alter schedules, or delete dependencies. All mutations require user confirmation.
4. **In-Memory Subgraph Evaluation**: Graph traversal and CPM calculations are performed in-memory on the loaded project subgraph ($O(V + E)$). For extreme graphs exceeding 50,000 nodes in a single project, distributed graph processing (e.g., GraphX) would be required.

## API Overview

All endpoints are prefixed with `/api` and return standardized JSON responses:

### Projects (`/api/projects`)
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/projects` | List all projects |
| `POST` | `/api/projects` | Create a new project |
| `GET` | `/api/projects/{id}` | Get project details |
| `DELETE` | `/api/projects/{id}` | Delete a project and its tasks/dependencies |

### Tasks (`/api/tasks`)
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/tasks/project/{projectId}` | List all tasks in a project with derived readiness and schedules |
| `POST` | `/api/tasks` | Create a new task (auto-calculates readiness and schedule) |
| `GET` | `/api/tasks/{id}` | Get task details |
| `PUT` | `/api/tasks/{id}` | Update task details, workflow status, or planned dates |
| `DELETE` | `/api/tasks/{id}` | Delete task and cascade delete associated dependencies |

### Dependencies (`/api/dependencies`)
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/dependencies/project/{projectId}` | List all directed dependency edges in a project |
| `POST` | `/api/dependencies` | Create directed dependency (enforces cycle detection, project match, non-self) |
| `DELETE` | `/api/dependencies/{predecessorId}/{successorId}` | Remove dependency edge and recalculate downstream readiness/schedules |
| `GET` | `/api/dependencies/affected-subgraph/{taskId}` | Get topologically sorted list of downstream affected tasks |

### Scheduling & Impact Simulation
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/tasks/{taskId}/schedule/preview` | Deterministic, side-effect-free schedule impact simulation |
| `PUT` | `/api/tasks/{taskId}` | Authoritative schedule update with topological downstream propagation |

### AI Dependency Suggestions (`/api`)
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/tasks/{taskId}/dependency-suggestions` | Query configured LLM/provider for candidate dependencies |
| `POST` | `/api/dependency-suggestions/accept` | Explicitly accept AI suggestion, validating and persisting via DAG engine |

### Critical Path Analysis & Graph Inspection
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/projects/{projectId}/critical-path` | Authoritative CPM analysis (early/late dates, slack, critical paths) |
| `GET` | `/api/projects/{projectId}/dependency-graph` | Full graph topology with adjacency lists, roots, and leaves |

### Diagnostics & Health
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/actuator/health` | Spring Boot health probe |
| `GET` | `/actuator/metrics` | Micrometer metrics for requests, DAG operations, and timings |

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

## AI-Tool Declaration & Governance

In strict accordance with platform transparency, academic integrity, and AI safety criteria:

### 1. AI Tools Used During Development
- **Assistant & IDE**: Google DeepMind Antigravity IDE (powered by Gemini models) was utilized as an agentic pair-programming assistant for boilerplate scaffolding, test generation, and documentation drafting.
- **Authoritative Review**: Every algorithm, SQL schema constraint, graph cycle check, topological sorting implementation, and React component was reviewed, validated, and verified by human engineering against project specifications.

### 2. Runtime AI Architecture & Provider Strategy
TaskFlow Pro features a pluggable runtime AI architecture for dependency discovery:
- **Configured Providers**:
  - `mock` (Default / Development): A deterministic heuristic provider using software engineering lifecycle patterns (e.g. database schema $\rightarrow$ backend API $\rightarrow$ automated testing) to generate realistic candidate suggestions without external API keys or network latency.
  - `gemini`: Integration with Google Gemini (`gemini-1.5-flash` or configured model) via REST API.
  - `claude`: Integration with Anthropic Claude via REST API.
- **Grounded Context**: The prompt receives strictly scoped project tasks (titles and descriptions truncated to 500 characters). Cross-project tasks, environment variables, system prompts, and database credentials are never sent to external models.

### 3. What AI Does vs. What AI Does NOT Do
| Capability | Permitted | Architectural Enforcement |
|---|---|---|
| Propose candidate dependency edges | **YES** | Returns ephemeral JSON suggestions with confidence score ($0.0 - 1.0$) and semantic reasoning. |
| Directly mutate PostgreSQL database | **NO** | Controller and service layers forbid write operations during suggestion generation. |
| Modify task workflow or dependency status | **NO** | Readiness engine derives status strictly from prerequisite completion. |
| Automatically recalculate schedules | **NO** | Topological scheduling triggers only on human-approved entity updates. |
| Bypass cycle detection or graph rules | **NO** | All human-accepted suggestions pass through authoritative `TaskDependencyService`. |

### 4. Human-in-the-Loop & Hallucination Mitigation
1. **Human Acceptance**: AI suggestions are presented in the UI as advisory candidates with trust indicators (`AI Candidate · Requires Human Review`). Users must explicitly click "Accept" or dismiss.
2. **Server-Side Deterministic Validation**: Even when a human accepts a suggestion, the backend validates the edge before writing to PostgreSQL:
   - **Unknown Task Filter**: Rejects IDs not present in the current project database.
   - **Self-Dependency Filter**: Rejects $A \rightarrow A$.
   - **Duplicate Edge Filter**: Rejects edges already existing in the project.
   - **Cycle Pre-Validation**: Runs DFS traversal. If adding the edge introduces a directed cycle, the transaction aborts with HTTP `409 Conflict` (`CYCLE_DETECTED`), protecting database integrity.
3. **Resilience & Offline Guarantee**: If an external LLM times out, returns HTTP 429/500, or if `AI_ENABLED=false`, the core platform functions completely without degradation. No core workflow (Kanban, DAG, scheduling, readiness, CPM) relies on AI availability.

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
   - **No Compounding Delays on Converging Paths**: Notice that the CPM engine evaluates the earliest constraint of D as `max(EF(B) + 1, EF(C) + 1)`. It does *not* sum the durations of B and C into D merely because both converge into D. Only the longest predecessor constraint determines D's timing, preventing artificial double-counting.

### 5. Multiple Critical Paths & Disjoint Subgraphs
- If parallel branches share the same schedule-determining duration (e.g. `duration(B) == duration(C)`), **all critical sequences are preserved and returned** (e.g., `[[A, B, D], [A, C, D]]`).
- Projects with multiple independent roots or disconnected subgraphs are fully evaluated. All project tasks are included in the `tasks` metrics list.
- Reconstructed paths are bounded by a configurable maximum (`MAX_CRITICAL_PATHS = 100`) to prevent exponential path explosion on dense graphs.

### 6. Complexity & Performance
- **Time Complexity**: `O(V + E)` for topological sorting, forward pass, backward pass, and slack calculation. Path reconstruction is bounded by `O(K * V)` where `K` is the number of critical paths.
- **Space Complexity**: `O(V + E)` in memory.
- **Side-Effect Free**: Analytical only. Calling `GET /api/projects/{projectId}/critical-path` performs zero database writes, does not increment entity versions, and leaves task schedules intact.

### 7. Interactive CPM Workspace & Visual DAG Integration
The web application provides a comprehensive **Critical Path Analysis Dashboard** at `/critical-path`:
- **Project Metrics**: Real-time display of Project Duration, Project Finish Date, Critical Task Count, and Parallel Critical Paths Count.
- **Critical Chains Sequence**: Visual representation of each critical path sequence ($Task_1 \rightarrow Task_2 \rightarrow \dots \rightarrow Task_n$) with clickable interactive task pills.
- **Task Timing & Slack Breakdown Table**: Horizontally scrollable table displaying Earliest Start (ES), Earliest Finish (EF), Latest Start (LS), Latest Finish (LF), Duration, Total Float (Slack), and `CRITICAL` vs `FLOAT` badges.
- **Selected Task Detail Callout**: Displays mathematically accurate scheduling flexibility statements:
  - Critical tasks: *"This task has 0 days of total float. Any delay directly delays project delivery."*
  - Float tasks: *"This task can move by up to X days without changing project completion date, assuming other constraints remain unchanged."*
- **Visual DAG Graph Highlight Mode**: Toggle `[ Highlight Critical Path ]` on `/graph` to emphasize critical nodes with amber borders and glow, animate critical connecting edges, and dim non-critical tasks to 40% secondary opacity.

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
│   │   ├── client.ts, projects.ts, tasks.ts, dependencies.ts, scheduling.ts, ai.ts, criticalPath.ts, dependencyGraph.ts
│   └── utils/
│       └── dates.ts                 # Timezone-safe calendar date arithmetic and formatting
├── components/
│   ├── graph/
│   │   ├── DependencyGraphView.tsx  # Interactive ReactFlow canvas with Dagre auto-layout
│   │   ├── GraphTaskNode.tsx        # Compact status & readiness node card
│   │   ├── GraphDetailDrawer.tsx    # Slide-out inspector with upstream/downstream lists
│   │   └── GraphEmptyState.tsx      # Guidance for zero tasks or zero dependencies
├── hooks/
│   ├── useDependencyGraph.ts        # Dagre layout, highlight calculation, mutation refresh
│   ├── useProjects.ts               # Project listing and selection
│   └── useProjectBoard.ts           # State orchestration, optimistic rollback, server reconciliation
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
8. **Interactive Visual DAG Graph**: Dedicated visual representation of project tasks and directed dependency edges powered by `@xyflow/react` and `@dagrejs/dagre`:
   - Directed edges with arrows enforcing `predecessor ───────▶ successor` semantics.
   - Dual layout modes: Left-to-Right (LR) workflow and Top-to-Bottom (TB) hierarchy.
   - Node selection with selective highlight modes: direct prerequisites/dependents or complete ancestor/descendant chains.
   - Interactive slide-out task inspector with direct dependency removal and quick Kanban navigation.
   - Graph controls: Zoom In/Out, Fit-to-Screen, Reset View, Layout Orientation, and Highlight Chain mode.
   - Accessible structured textual alternative view for screen readers and compact tabular review.
   - Live cycle rejection feedback without orphaned edges.
   - Dual-view workspace toggle (`Kanban Board` ⇄ `Visual DAG Graph`) and dedicated `/graph` route.

---

## User Workflow

The end-to-end user journey follows a deterministic, human-in-the-loop progression:

```text
Create Task
    ↓
Add Dependencies (Prerequisites / Dependents)
    ↓ (Server validates DAG, rejects cycles, calculates initial schedule)
Visual DAG Inspection (Inspect topological structure, converging paths, and dependency chains)
    ↓
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

## Testing & Verification Summary

### Comprehensive Test Suite Execution
TaskFlow Pro is validated by an extensive automated test suite covering unit, domain, graph, scheduling, readiness, integration, concurrency, and end-to-end scenarios:

#### Backend Test Suite (`./mvnw test`)
- **Total Tests**: **188 Tests Run**
- **Failures**: **0**
- **Errors**: **0**
- **Skipped**: **0**
- **Execution Time**: **~22 seconds**
- **Scope**:
  - `DependencyGraphTest`, `CycleDetectionTest`, `TopologicalSortTest` (Kahn's deterministic tie-breaking, back-edge detection)
  - `DependencyReadinessServiceTest`, `DependencyReadinessIntegrationTest` (unlocking, cascading rollback, converging paths)
  - `SchedulingServiceTest`, `SchedulingEngineIntegrationTest` (non-compounding propagation, duration preservation, earlier movement)
  - `ScheduleImpactPreviewTest` (side-effect-free in-memory simulation, preview/commit mathematical equivalence)
  - `CriticalPathCalculatorTest`, `CriticalPathControllerIntegrationTest` (inclusive forward/backward pass, total slack, multi-path)
  - `DependencySuggestionServiceTest`, `DependencySuggestionControllerIntegrationTest` (grounded context, cycle pre-validation, offline fallback)
  - `TaskConcurrencyIntegrationTest` (JPA `@Version` optimistic locking conflict rejection)
  - `TransactionalRollbackIntegrationTest` (ACID rollback on constraint violation)
  - `GoldenScenarioE2EIntegrationTest` (complete end-to-end Diamond DAG lifecycle)
  - `LargeDagSanityTest` (1,000 tasks topological sort benchmark < 100ms)

```bash
cd backend
./mvnw test
```

#### Frontend Test Suite (`npm test`)
- **Total Tests**: **60 Tests Run** across **17 Test Suites**
- **Failures**: **0**
- **Linter**: `npm run lint` $\rightarrow$ **0 ESLint warnings or errors**
- **Scope**:
  - `Header.test.tsx`: Clickable brand home link, breadcrumbs, keyboard accessibility, responsive truncate
  - `KanbanBoard.test.tsx`, `TaskCard.test.tsx`, `TaskDialogs.test.tsx`: Column workflows, card rendering, dialog forms
  - `ReadinessPage.test.tsx`: Backend integration, disabled `Mark Done` on `BLOCKED` tasks with contextual guidance
  - `SchedulingPages.test.tsx`: Live project task loading, date propagation triggers, side-effect-free impact previews
  - `DependencyGraphView.test.tsx`, `GraphTaskNode.test.tsx`: Visual DAG rendering, ancestor tracing, zoom/fit
  - `CriticalPathWorkspace.test.tsx`, `CriticalPathModal.test.tsx`: Multi-path visualization, slack inspector
  - `AiSuggestionWorkspace.test.tsx`, `AiSuggestionsModal.test.tsx`: Candidate review, explicit acceptance

```bash
cd frontend
npm run lint
npm test
```

---

## Security Architecture & Defenses

TaskFlow Pro implements defense-in-depth principles across all application boundaries:

| Security Dimension | Implementation & Architectural Enforcement |
|---|---|
| **Zero Secrets in Source** | Database credentials, API keys, and environment-specific settings are injected solely through environment variables. `.env` is ignored by Git; `.env.example` contains safe placeholder values. |
| **DTO-Based Request Validation** | All client payloads are bound to strongly-typed DTOs and validated with Jakarta Bean Validation (`@Valid`, `@NotNull`, `@Size`, `@Min`). Unrecognized fields are rejected. |
| **Backend Authoritative Control** | The client has zero authority over derived readiness (`READY`/`BLOCKED`) or scheduled dates. Client attempts to forge dependency states are completely ignored; domain rules are enforced exclusively by the backend. |
| **Project Boundary Isolation** | Relational foreign keys (`ON DELETE CASCADE`) and service-level checks guarantee tasks from different projects cannot be linked in a dependency edge. Cross-project contamination is impossible. |
| **Pre-Transaction Cycle Validation** | Cycle detection is performed in-memory via DFS prior to executing database writes, preventing graph corruption and eliminating unnecessary deadlocks. |
| **Optimistic Concurrency Control** | Tasks utilize a JPA `@Version` column (`BIGINT`). Concurrent modifications to the same task trigger HTTP `409 Conflict` (`CONCURRENCY_CONFLICT`), preventing lost updates in multi-user environments. |
| **Untrusted AI Output Handling** | All suggestions from external LLMs are treated as untrusted user input and subjected to the same strict deterministic DAG validation before persistence. |
| **Prompt Injection Defense** | Prompts are constructed using strictly sanitized JSON structure with truncated task titles and descriptions (max 500 chars). System prompts explicitly instruct the model to ignore override commands embedded in task descriptions. |
| **RFC-7807 Error Sanitization** | Exceptions are intercepted by `GlobalExceptionHandler`. Sensitive stack traces, SQL syntax, and internal server paths are never exposed to clients; clients receive structured error codes and correlation IDs. |
| **HTTP Edge Security Headers** | `SecurityHeadersFilter` automatically injects `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, and `Permissions-Policy`. |

---

## Production Readiness & Operational Robustness

TaskFlow Pro is designed for production deployability without operational complexity:

1. **Automated Schema Evolution**: PostgreSQL 16 migrations (`V1` through `V5`) are automatically executed on startup by Flyway, ensuring reproducible schema deployments and automated demo seeding.
2. **ACID Transaction Boundaries**: All domain mutations (`createDependency`, `updateTask`, `recalculateReadiness`, `propagateSchedule`) run inside atomic `@Transactional` boundaries. If any invariant fails, all intermediate mutations roll back cleanly.
3. **Execution Guardrails**: Tasks in a `BLOCKED` dependency state cannot be completed (`workflowStatus == DONE`). Backend rejects invalid transitions with HTTP `409 Conflict` (`TASK_BLOCKED`); frontend disables the `Mark Done` action with explanatory tooltips.
4. **Health & Liveness Probes**: Integrated Spring Boot Actuator endpoints (`/actuator/health` and `/actuator/metrics`) provide container orchestrators (Kubernetes / Docker Compose) with instant liveness and readiness status without exposing sensitive environment dumps.
5. **Request Correlation & MDC Tracing**: Every request is tagged with an `X-Request-Id` (UUID) propagated through SLF4J MDC, enabling instant log correlation across high-throughput server logs.
6. **Graceful Degradation**: Core workflow capabilities (Kanban, DAG, scheduling, readiness, CPM) operate with zero reliance on external AI services. If AI is disabled or fails, the core system continues operating with zero disruption.

---

## Scalability Evolution: From Prototype to Enterprise

TaskFlow Pro's architecture is engineered with a clear, defensible path for scaling as workload demands expand:

```text
Current Architecture (Prototype / Mid-Scale)
   O(V + E) In-Memory DAG Engine · Spring Boot Modular Monolith · PostgreSQL 16
                              │
                              ▼
Scale Phase 1: 10,000 Tasks
   HikariCP Read/Write Connection Pools · Read Replicas for CPM Queries · Composite Indexes
                              │
                              ▼
Scale Phase 2: 100,000 Tasks
   Redis Caching for Project Adjacency Lists · Asynchronous Background Workers for Large Shifts
                              │
                              ▼
Scale Phase 3: Millions of Tasks
   Horizontal Partitioning by project_id · Transactional Outbox Pattern · Event-Driven Propagation
```

### Detailed Scaling Roadmap
1. **Current Scale (Up to 5,000 Tasks per Project)**:
   - All graph traversals (DFS cycle detection, Kahn's topological sort, CPM forward/backward pass) execute in $O(V + E)$ time using in-memory adjacency sets (`Map<UUID, Set<UUID>>`).
   - The entire project subgraph is loaded in a single indexed query via `DependencyGraphBuilder`, completely avoiding N+1 database queries.
   - Benchmark: 1,000 tasks traverse and schedule in **< 100ms** on standard commodity hardware.
2. **Mid Scale (10,000 – 50,000 Tasks)**:
   - Configure PostgreSQL read replicas to offload read-only CPM critical path calculations (`GET /api/projects/{id}/critical-path`) and visual DAG queries (`GET /api/projects/{id}/dependency-graph`).
   - Leverage HikariCP connection pool tuning and batch insert/update capabilities for large cascade writes.
3. **High Scale (100,000 Tasks)**:
   - Introduce Redis caching for project adjacency lists and cached topological orderings. Cache invalidation occurs deterministically only when a dependency edge is created or deleted.
   - Offload large project-wide schedule shifts to asynchronous Spring background worker threads (`@Async`), returning an immediate correlation ID to the client while broadcasting updates via WebSockets or Server-Sent Events (SSE).
4. **Enterprise Scale (Millions of Tasks Across Thousands of Projects)**:
   - **Horizontal Sharding / Table Partitioning**: Because TaskFlow Pro enforces strict project isolation (no cross-project dependencies), data partitions naturally and cleanly by `project_id`. Sharding PostgreSQL by `project_id` requires zero distributed cross-partition graph queries.
   - **Event-Driven Outbox**: Edge additions publish domain events via a Transactional Outbox pattern into an event bus (e.g. Apache Kafka), enabling horizontally scaled worker nodes to process schedule cascades per project partition.

> **Architectural Defense**: We intentionally avoid premature introduction of Kafka, Redis, or microservices today. Introducing distributed infrastructure before reaching thousands of concurrent projects would compromise the determinism, atomic consistency, and maintainability of the graph engine without providing performance benefits.

---

## Production Deployment (`docker-compose.yml`)

TaskFlow Pro provides a multi-container Docker Compose configuration for production evaluation:

```bash
# Build and run the entire stack (PostgreSQL + Spring Boot Backend + Next.js Frontend)
docker compose up --build
```
- **Postgres**: `postgres:16-alpine` on port 5432 with `pg_isready` health check.
- **Backend**: Multi-stage `eclipse-temurin:21-jre-alpine` container running as non-root user `taskflow` with Actuator liveness/readiness probe on `/actuator/health`.
- **Frontend**: Multi-stage `node:20-alpine` standalone runner on port 3000 running as non-root user `nextjs`.

## Observability & Operations (Phase 11)

TaskFlow Pro features a lightweight, production-grade observability and diagnostics architecture built entirely on Spring Boot Actuator, Micrometer, SLF4J/Logback, and normalized frontend error reporting without unnecessary distributed infrastructure (no Kafka, Redis, Elasticsearch, Prometheus server, or OpenTelemetry collectors).

### 1. Structured Logging & Operation Tracking
Backend services log operation boundaries using structured key-value pairs designed for machine parsing and rapid operator diagnostics:

```text
2026-09-27T00:04:24.089+05:30  INFO 91592 --- [taskflow-pro] [main] c.t.t.s.service.SchedulingService : operation=SCHEDULE_PROPAGATION rootTaskId=e053a2e6-c4f8-4a8b-a0d0-8a4812dbdfd3 projectId=c87eabf6-fcc1-4ddc-83b7-91ae17898437 affectedTaskCount=4 changedTaskCount=4 maxScheduleShiftDays=3 durationMs=3 result=SUCCESS
```

Key monitored domain operations:
- `TASK_CREATE`, `TASK_UPDATE`, `TASK_DELETE`: Entity mutations, status transitions, and duration.
- `DEPENDENCY_CREATE`: Predecessor/successor IDs, project scope, and explicit results (`SUCCESS`, `CYCLE_REJECTED`, `DUPLICATE_REJECTED`, `SELF_DEPENDENCY_REJECTED`, `NOT_FOUND`).
- `DEPENDENCY_REMOVE`: Edge removal and cascade timings.
- `DEPENDENCY_READINESS_RECALCULATION`: Topological propagation, `affectedTaskCount`, `changedTaskCount`, `readyCount`, `blockedCount`, and `durationMs`.
- `SCHEDULE_PROPAGATION` & `SCHEDULE_PREVIEW`: Root task ID, affected count, non-compounding `maxScheduleShiftDays`, and calculation duration.
- `CRITICAL_PATH_CALCULATION`: Project ID, task count, critical task count, multi-path count, project duration in days, and computation duration.
- `AI_DEPENDENCY_SUGGESTION`: Provider, target task, candidate count, result (`SUCCESS`, `DISABLED`, `RATE_LIMITED`, `PROVIDER_ERROR`), suggestion count, and duration.

> **Security Guarantee**: Logging never includes passwords, database credentials, API keys, JWTs, cookies, request bodies, complete AI prompts, or full model responses.

### 2. Request Correlation & Tracing
- **`RequestIdFilter`**: Automatically captures incoming `X-Request-Id` (sanitizing to `^[a-zA-Z0-9_-]{1,64}$`) or generates a standard UUID.
- **MDC Context**: Injects `requestId` into SLF4J MDC for automatic inclusion in every log line and clears context in a `finally` block to prevent thread leaks.
- **Header & Payload Echo**: Returns `X-Request-Id` in all HTTP response headers and embeds `requestId` in JSON error responses (`ErrorResponse`).

### 3. HTTP Request Observability & Error Classification
- **`HttpRequestLoggingFilter`**: Captures method, URI path, HTTP status, duration in ms, and `requestId`.
- **Level Differentiation**:
  - `2xx / 3xx`: Logged at `INFO`
  - `4xx` (Client & business validation errors, cycle rejections, not found): Logged at `WARN` without noisy stack traces.
  - `5xx` (Unexpected server/database faults): Logged at `ERROR` with full stack traces.
  - Internal actuator health checks (`/actuator/health`): Filtered to `DEBUG` to prevent high-frequency probe log spam.

### 4. Health & Readiness Probes
- **Endpoint**: `GET /actuator/health`
- **Checks**:
  - Application liveness and readiness state.
  - Database connectivity probe verifying PostgreSQL socket reachability and responsiveness.
- Exposes no environment variables, heap dumps, or sensitive configuration internals.

### 5. Application Metrics (Micrometer / Actuator)
Lightweight application metrics registered in Spring Boot's `MeterRegistry` using strictly low-cardinality tags (`operation`, `result`, `status`):
- `task_create_total`, `task_update_total`, `task_delete_total`
- `dependency_create_total` (tag: `result` -> `success`, `cycle_rejected`, etc.)
- `dependency_remove_total`
- `dependency_cycle_rejection_total`
- `schedule_propagation_total` & `schedule_propagation_duration` (Timer)
- `readiness_recalculation_total` & `readiness_recalculation_duration` (Timer)
- `ai_suggestion_request_total` & `ai_suggestion_duration` (Timer)
- `ai_suggestion_failure_total`

### 6. Frontend Diagnostics & Normalized Error Handling
- **API Error Normalization (`formatApiError`)**: Normalizes `ApiClientError`, HTTP network faults, and client validation errors into user-friendly messages with error codes and correlation IDs:
  - `CYCLE_DETECTED`: "This dependency would create a cycle, so it was not added."
  - `CONCURRENCY_CONFLICT`: "This task was changed elsewhere. Refresh and try again."
  - `VALIDATION_ERROR`: "Please check the task details and try again."
  - `AI_DISABLED`: "AI suggestions are temporarily unavailable. You can add the dependency manually."
- **React Error Boundary (`error.tsx`)**: Dark-themed, responsive error boundary preventing blank screens, offering a retry action (`reset()`), page refresh, and displaying reference IDs (`error.digest` / `requestId`) without exposing stack traces.

### 7. Step-by-Step Operator Debugging Workflow
```text
1. Capture Request ID: Obtain the correlation ID from the UI alert or the X-Request-Id HTTP response header.
2. Search Backend Logs: grep "requestId=<UUID>" /path/to/backend.log
3. Identify Operation & Code: Check operation=<NAME> result=<RESULT> and status=<CODE>.
4. Analyze Root Cause: Inspect affectedTaskId, reason, or constraint details.
5. Verify State: Check project and task status in database or via GET /api/projects/{projectId}/dependency-graph.
```

### 8. Operational Troubleshooting Table

| Symptom | Likely Area | What to Inspect |
|---|---|---|
| **Dependency not added** | DAG validation | Search logs for `operation=DEPENDENCY_CREATE` and inspect `result` (`CYCLE_REJECTED`, `SELF_DEPENDENCY_REJECTED`, or `DUPLICATE_REJECTED`). |
| **Task unexpectedly BLOCKED** | Readiness engine | Search logs for `operation=DEPENDENCY_READINESS_RECALCULATION` and inspect predecessor workflow statuses (`BACKLOG`, `IN_PROGRESS` vs `DONE`). |
| **Schedule changed unexpectedly** | Scheduling engine | Search logs for `operation=SCHEDULE_PROPAGATION` and check `maxScheduleShiftDays` and root task planned start date change. |
| **Preview differs from commit** | Scheduling simulation | Compare `operation=SCHEDULE_PREVIEW` logs with `operation=SCHEDULE_PROPAGATION` logs for the same root task ID. |
| **AI suggestions unavailable** | AI provider / Rate limiter | Search logs for `operation=AI_DEPENDENCY_SUGGESTION` and check `result` (`DISABLED`, `RATE_LIMITED`, or `PROVIDER_ERROR`). Verify deterministic workflow continues unaffected. |
| **Update rejected (409)** | Optimistic locking | Search logs for `Optimistic locking conflict` with `code=CONCURRENCY_CONFLICT` and `requestId`. Concurrently modified entity version requires reload. |
| **Slow graph propagation** | Graph engine | Search logs for `durationMs` in `SCHEDULE_PROPAGATION` or `DEPENDENCY_READINESS_RECALCULATION`. Verify `LargeDagSanityTest` baseline (< 500ms for 1,000 tasks). |

---

## Implemented Phases

- [x] **Phase 1**: Monorepo foundation, Spring Boot 3 modular monolith (Java 21), Next.js 14 frontend, containerized PostgreSQL 16 via Docker Compose, Flyway migration baseline, strict separation of derived dependency states (`READY`, `BLOCKED`) from workflow states (`BACKLOG`, `IN_PROGRESS`, `REVIEW`, `DONE`), and centralized RFC-7807 error handling.
- [x] **Phase 2**: Core domain model (`Project`, `Task`, `TaskDependency`), PostgreSQL relational schema via Flyway (`V2__create_core_domain_tables.sql`), foreign keys with `ON DELETE CASCADE`, self-loop checks, JPA/Hibernate optimistic locking via `@Version` on a `BIGINT` column, project isolation validation, and persistence test suite.
- [x] **Phase 3**: Deterministic DAG Engine (`DependencyGraph`, DFS cycle detection, Kahn's topological sort with deterministic tie-breaking, reachability, descendant/ancestor traversal, affected subgraph calculation, and transactional cycle prevention).
- [x] **Phase 4**: Dependency Readiness Engine (evaluating prerequisite completion, derived `READY`/`BLOCKED` status propagation in topological order, multi-level unlock, downstream rollback on task reopen, converging graph handling, and edge addition/removal recalculation).
- [x] **Phase 5**: Dependency-Aware Scheduling Engine (constraint-based schedule calculation, non-compounding downstream date propagation, topological schedule recalculation, duration preservation, recomputable baseline schedules, and transactional persistence).
- [x] **Phase 6**: Dependency Impact Preview (side-effect-free in-memory schedule simulation, identical calculation engine reuse, binding predecessor identification, converging path non-compounding explanation, preview/commit consistency verification, and REST preview API).
- [x] **Phase 7**: AI-Assisted Dependency Suggestion Engine (pluggable provider abstraction, Google Gemini REST adapter with JSON schema enforcement, grounded project task context, prompt injection defense, server-side validation against hallucination/cycles/cross-project/self-dependency, human-in-the-loop explicit acceptance flow delegating to deterministic graph engine, offline degradation, and comprehensive unit/integration test suite).
- [x] **Phase 8**: Critical Path Analysis Engine (pure deterministic CPM calculation, forward/backward pass, total slack calculation, critical task identification, bounded multi-path reconstruction, read-only REST API `GET /api/projects/{projectId}/critical-path`, inclusive calendar date arithmetic, and comprehensive test suite).
- [x] **Phase 9**: Production Kanban Frontend and Workflow UI (responsive 4-column Kanban board, authoritative readiness and schedule display, accessible card actions, optimistic UI with server rollback, schedule impact preview intercept modal, dependency management with cycle error reporting, AI suggestion review with explicit acceptance, and multi-path critical path analysis modal).
- [x] **Phase 10**: Production Hardening, Security, E2E Validation & Deployment Readiness (HTTP security headers, correlation ID request tracing, sliding-window AI rate limiting, input boundary constraints, optimistic locking concurrency protection, atomic rollback guarantees, preview/commit consistency verification, Golden Scenario E2E test, deterministic seed demo migration, multi-stage production Dockerfiles, Actuator health probes, and full Docker Compose orchestration).
- [x] **Functional Completion & Visual DAG**: Dedicated Visual DAG representation powered by `@xyflow/react` and `@dagrejs/dagre`, backend `GET /api/projects/{projectId}/dependency-graph` query endpoint, predecessor → successor arrow semantics, Dagre layout engine, node selection & chain highlighting, slide-out inspector, cycle rejection error messaging, accessible list alternative, responsive canvas controls, and dual-view workspace switching.
- [x] **Phase 11**: Observability, Diagnostics & Production Operations (Correlation `X-Request-Id` filter, sanitized MDC tracing, structured domain operation logging, 4xx/5xx HTTP request classification, Micrometer application metrics, actuator health validation, large DAG sanity stress test for 1,000 tasks, Golden Operation Trace integration test, centralized frontend error normalization, accessible error boundary with reference ID display, and operator troubleshooting guide).



