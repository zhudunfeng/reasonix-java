# Repository Guidelines

## Project Structure

This repository (`reansonix-java`) is a Java 17 + Spring Boot 3 reimplementation of the Reasonix AI coding agent, built on the AgentScope-Java framework. The codebase follows a standard Maven layout and is intentionally scoped so every package owns one concern.

```
reansonix-java/
├── src/main/java/com/reansonix/   ← production source code
├── src/main/resources/            ← application.yml, workspace templates
├── src/test/java/com/reansonix/   ← unit & integration tests
├── docs/                          ← design documents, PRD, API specs
└── pom.xml                        ← Maven build descriptor
```

Internal packages mirror the Reasonix Go modules:
- `agent/` — unified `AgentController` and `ReActLoop`
- `provider/` — LLM & embedding adapters
- `skill/` — Skill discovery, parsing, and indexing
- `tool/` — Tool abstraction, registry, and built-in tools
- `memory/` — Document, auto, and vector memory layers
- `subagent/` — Sub-agent lifecycle management
- `config/` — `@ConfigurationProperties` bindings for YAML config

## Build, Test, and Development Commands

| Command | Purpose |
|---|---|
| `mvn clean compile` | Compile all modules without running tests |
| `mvn clean test` | Compile and run the full test suite |
| `mvn spring-boot:run` | Start the application in development mode |
| `mvn clean verify` | Run tests and integration checks (CI-equivalent) |
| `mvn spotless:apply` | Apply automatic code formatting (Java) |

## Coding Style & Naming Conventions

- Language: **Java 17** (minimum), **Java 21** preferred for Virtual Thread support.
- Build tool: **Maven 3.9+** (aligns with the AgentScope-Java reference project).
- Formatting: **Spotless** with the `google-java-format` preset; run `mvn spotless:apply` before committing.
- Packages: use lowercase dot-separated names (e.g. `com.reasonix.agent`).
- Classes: `PascalCase`; interfaces begin with a capital `I` only when a concrete implementation suffix already exists (e.g. `ChatModel` not `IChatModel`).
- Methods & variables: `camelCase`; constants `UPPER_SNAKE_CASE`.
- Keep classes focused: one responsibility per class, following the Reasonix Go package conventions.

## Testing Guidelines

- Framework: **JUnit 5** with **Spring Test** for integration tests.
- Location: test classes sit in the same package as the class under test, under `src/test/java/…`.
- Naming: `{ClassName}Test.java` for unit tests, `{Feature}IntegrationTest.java` for integration tests.
- Run a single test: `mvn test -Dtest=ReActLoopTest`.
- Add regression tests when fixing bugs; avoid changing existing passing tests without discussion.

## Commit & Pull Request Guidelines

- Commit messages follow the [Conventional Commits](https://www.conventionalcommits.org/) style (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).
- Body lines should wrap at 72 characters; reference related issues with `Closes #NNN`.
- Open PRs against the `main` branch and include:
  1. A clear description of the change and motivation.
  2. Confirmation that `mvn clean verify` passes.
  3. Screenshots or log snippets for any REST endpoint changes.
- Keep PRs scoped: one logical change per PR; use draft PRs for work-in-progress.

## Security & Configuration Tips

- API keys are injected via environment variables in `application.yml` (e.g. `${DEEPSEEK_API_KEY:}`); never hardcode secrets.
- The `reasonix.toml → application.yml` config migration is tracked in the PRD; contributor tooling for that migration is a Phase 4 item.
- MCP tool bridges in `McpController` must validate and sanitize all inbound JSON-RPC payloads.



## User

- 使用wsl ubuntu为运行编译环境与运行环境
- 代码方法必须有中式注释，使用utf8字符编码