# Contributing

NeonVibe is a personal, self-hosted music server. Contributions are welcome, but
the repository is maintained as a single mainline by its owner. This document
defines the workflow and conventions to keep the history clean.

## Development Environment

See [README.md](README.md) for the full setup. In short:

```bash
docker compose -f docker-compose.dev.yml up -d   # PostgreSQL dev (puerto 5433)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && pnpm dev                          # localhost:5173
```

## Branching and Pull Requests

- Work on a short-lived branch named `fix/<slug>` or `feat/<slug>`.
- Keep `main` green: it must always build and pass tests.
- Open a pull request for review. Each PR should be focused on a single change.
- The owner merges into `main`; the merge history stays linear (rebase/squash).

## Commit Conventions

Commits follow [Conventional Commits](https://www.conventionalcommits.org/),
written in English:

- `feat:` — new functionality
- `fix:` — bug fix
- `refactor:` — code change that does not alter behavior
- `docs:` — documentation only
- `chore:` — build, tooling, maintenance
- `perf:`, `test:`, `build:`, `ci:` — as appropriate

Example:

```
fix(web): stop React #310 crash on detail pages
```

## Code Conventions

Backend (Java 21 / Spring Boot):

- Package base: `com.neonvibe`.
- Never expose JPA entities directly; use DTOs.
- Prefer constructor injection; no field `@Autowired`.
- Handle exceptions through the global `@ControllerAdvice`.
- Keep the scanner and heavy jobs async (`@Async` / `ExecutorService`).

Frontend (React 18 / TypeScript):

- Functional components and hooks; no class components.
- TypeScript strict; no untyped `any`.
- Call hooks unconditionally and before any conditional `return`.
- Mobile-first; the player bar stays fixed at the bottom (min 64px).

General:

- Documentation for humans is written in Spanish.
- Commit messages are written in English (conventional commits).
- No emojis in documentation.
- Diagrams, when needed, use Mermaid (must render correctly on GitHub).

## Testing

- Backend: `cd backend && ./mvnw test` (Spring Boot + JUnit 5, 123 tests).
- Frontend: `cd frontend && pnpm test` (Vitest + Testing Library on jsdom).
- Frontend typecheck and bundle: `cd frontend && pnpm build` (`tsc -b` + Vite).
- CI (`.github/workflows/ci.yml`) runs the backend tests, the frontend tests and
  build, and packages the deployable JAR on every push and pull request to `main`.
- Add or update tests with the change; a PR that breaks the build is not merged.

## Definition of Done

- Code builds locally (`deploy/build.sh` or the equivalent).
- Backend tests pass.
- Frontend typecheck and build pass.
- Documentation touched by the change is updated.
- Commit message follows the conventions above.
