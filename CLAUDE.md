# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**umbrella** is the open-source (MIT) client side of Umbrella, a bot-protection / web-request-firewall product. Integrations intercept HTTP requests in a web server, send request metadata to the Umbrella API, and enforce the returned ALLOW/BLOCK decision.

This repo was split out of the original monorepo:

- **`umbrella`** (this repo): OpenAPI spec, base clients (Java/TypeScript), web-server integrations (Tomcat jakarta/javax, Express).
- **`umbrella-enterprise`** (sibling repo, proprietary): the `umbrella-stream` backend (DataSpray-based ingester/controller). It consumes this repo's `umbrella-api` `json-schema` artifact — build this repo first (`mvn clean install`) so it lands in `~/.m2`.

## Repository Structure

```
umbrella/
├── umbrella-api/                  # OpenAPI spec (source of truth) + JSON schema generation
│   ├── src/main/openapi/          #   umbrella-api.yaml
│   └── src/main/javascript/       #   generateJsonSchema.js (node)
│   # Publishes two classifier tar.gz artifacts: `openapi` (raw yaml) and `json-schema`
├── umbrella-base/                 # Framework-agnostic base clients
│   ├── umbrella-java/             #   UmbrellaService(-Impl) wrapping a build-time-generated
│   │                              #   okhttp/gson client (groupId io.dataspray.umbrella.base)
│   └── umbrella-typescript/       #   npm package `umbrella-client`; thin UmbrellaClient wrapper;
│                                  #   generated typescript-fetch client is CHECKED IN under
│                                  #   src/main/typescript/client/
└── umbrella-integration/          # Web-server integrations
    ├── umbrella-tomcat/           #   Jakarta servlet filter (Servlet 6 / Tomcat 10.1+, Java 11 target)
    ├── umbrella-tomcat-javax/     #   Legacy javax.servlet twin — a near-verbatim COPY of umbrella-tomcat
    └── umbrella-express/          #   Empty stub — not implemented yet (pom + package.json only)
```

Key architectural docs: [CLIENT-ARCHITECTURE.md](CLIENT-ARCHITECTURE.md) (two-tier client design, implementation contract) and [IMPROVEMENT-PLAN.md](IMPROVEMENT-PLAN.md) (planned features — note its `umbrella-integration/umbrella-java/...` file paths are stale; that module now lives at `umbrella-base/umbrella-java/`).

## Architecture Notes (important when editing)

- **Two-tier design**: base client (`umbrella-base/*`) owns HTTP communication, modes, ping loop; integrations (`umbrella-integration/*`) own request interception and action enforcement.
- **Deliberate package sharing**: `umbrella-base/umbrella-java` uses package `io.dataspray.umbrella.integration.tomcat` so the Tomcat filters use it without imports. Renaming this package is a breaking change.
- **Jakarta/javax duplication is copy-paste**: `umbrella-tomcat` and `umbrella-tomcat-javax` differ only in import prefixes and one SameSite-cookie line. Any fix to `UmbrellaFilter` (or its test) must be applied to BOTH modules.
- **Fail-open contract**: Umbrella must never break the host application. API errors/timeouts → ALLOW and continue the filter chain. Only an explicit BLOCK decision may stop a request. Preserve this in all changes.
- **Operation modes** (server-controlled via ping/config): `BLOCKING` (synchronous decision per request), `MONITOR` (async fire-and-forget reporting), `DISABLED`. A 429 from the API temporarily disables until the next ping.

## Build System

Hybrid Maven + pnpm. Maven drives everything; `frontend-maven-plugin` installs Node/pnpm into `./node` and runs the TypeScript builds. Java clients are generated at build time from the unpacked `umbrella-api` artifact (`openapi-generator-maven-plugin`); the TypeScript generated client is checked in.

### Key Commands

```bash
# Full build and test (from repo root)
mvn clean install

# Without tests
mvn clean install -DskipTests

# Single module (umbrella-api must be installed first for downstream modules)
cd umbrella-base/umbrella-java && mvn clean install

# Single test class
mvn test -Dtest=UmbrellaFilterTest

# Regenerate after editing the OpenAPI spec
# 1. Edit umbrella-api/src/main/openapi/umbrella-api.yaml
# 2. cd umbrella-api && mvn clean install          (regenerates JSON schemas)
# 3. Rebuild umbrella-base modules                 (regenerates clients)
```

Publishing: Maven artifacts are GPG-signed and deployed via nexus-staging; npm packages publish during the Maven `deploy` phase (`can-npm-publish` guard). `exists-maven-plugin` skips already-published versions — **version bumps are required for any republish**.

## Tool Requirements

- **Java**: 21 to build (maven-enforcer-plugin); integration/base jars target Java 11 for consumer compatibility
- **Node.js**: v22.17.0 (`.nvmrc`; frontend-maven-plugin installs its own copy)
- **pnpm**: 8.6.10
- **Maven**: 3.x

## Testing

JUnit 5 + Mockito; `umbrella-java` uses OkHttp MockWebServer.

- `umbrella-base/umbrella-java`: `UmbrellaServiceTest` (init, block, timeout, monitor, disabled)
- `umbrella-integration/umbrella-tomcat` and `-javax`: `UmbrellaFilterTest` (kept in sync manually — update both)
- `umbrella-typescript`, `umbrella-api`, `umbrella-express`: no tests currently

## Known Issues / Gotchas (as of 2026-08, post-split)

- **CI gating bug**: `.github/workflows/test.yml` only runs on pushes whose commit message contains `[skip deploy]` (monorepo leftover) — normal master pushes are untested. There is **no deploy workflow** in this repo; releases can't run from CI. The workflow also installs the `dst` CLI, which nothing here uses.
- **GroupId migration hazard**: `umbrella-java` moved to groupId `io.dataspray.umbrella.base` but kept version 0.0.4 (published 0.0.4 exists only under the old `io.dataspray.umbrella.integration`). The tomcat modules (0.0.7, already published with old coordinates) hardcode the new dependency. Everything needs version bumps before the next release.
- **Tomcat poms are unparented**: `umbrella-tomcat`/`-javax` declare no `<parent>` and duplicate ~100 lines of publishing config each.
- **Publishing endpoints are dead**: all poms point at `s01.oss.sonatype.org` (OSSRH, decommissioned mid-2025); deploys need migration to Central Publisher Portal.
- `.node-version` (18.16.1) contradicts `.nvmrc` (22.17.0) — trust `.nvmrc`.
- Spec validation is disabled (`skipValidateSpec=true`) in consumer poms because the spec embeds non-standard `existingJavaType` keys (used by the enterprise repo's codegen).
- `umbrella-express` is an empty stub that still produces an (empty) 0.0.2 jar.

## Git Configuration

Commits must be GPG-signed. Configure git before committing:
```bash
git config user.name 'Matus Faro'
git config user.email 'matus@matus.io'
git config commit.gpgsign true
git config user.signingkey matus@matus.io
```
