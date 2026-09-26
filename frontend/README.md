# TaskFlow Pro - Frontend

The frontend for TaskFlow Pro is a Next.js 14 application built with TypeScript and Tailwind CSS. It serves as the visualization and interaction layer for the workflow engine.

## Architectural Boundary

> **Core Invariant**: The deterministic backend DAG/dependency engine is the single source of truth. The frontend is strictly a presentation and interaction surface; it does not compute Ready/Blocked statuses or graph validity locally.

## Directory Structure

```text
frontend/
├── app/                  # Next.js App Router (layout, pages, globals.css)
├── components/
│   ├── ui/               # Reusable UI atoms (Badge, Button, Card)
│   └── layout/           # Application layout components (Header, Sidebar)
├── hooks/                # Custom React hooks
├── lib/
│   ├── api/              # Typed API communication clients (client.ts, taskApi.ts)
│   └── utils/            # Helper utilities (cn.ts)
├── types/                # Strict TypeScript domain interfaces
├── public/               # Static assets
├── package.json
└── tsconfig.json
```

## Setup & Running

```bash
# Install dependencies
npm install

# Run development server
npm run dev

# Build production bundle
npm run build

# Run linter
npm run lint
```

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | Backend REST API base URL | `http://localhost:8080/api` |
