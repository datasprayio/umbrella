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
    ├── umbrella-tomcat-javax/     #   Legacy javax.servlet twin — thin adapter only, logic is shared
    └── umbrella-express/          #   npm `umbrella-express`; Express middleware over the TS client
```

Key architectural docs: [CLIENT-ARCHITECTURE.md](CLIENT-ARCHITECTURE.md) (two-tier client design, implementation contract) and [IMPROVEMENT-PLAN.md](IMPROVEMENT-PLAN.md) (feature roadmap, with a status table — Phase 1 is implemented).

## Architecture Notes (important when editing)

- **Two-tier design**: base client (`umbrella-base/*`) owns HTTP communication, modes, ping loop; integrations (`umbrella-integration/*`) own request interception and action enforcement.
- **Deliberate package sharing**: `umbrella-base/umbrella-java` uses package `io.dataspray.umbrella.integration.tomcat` so the Tomcat filters use it without imports. Renaming this package is a breaking change.
- **Shared logic, thin adapters**: request collection and action enforcement live once in `umbrella-base/umbrella-java` (`UmbrellaHttpExchange`, behind the `HttpExchangeAdapter` seam). Each servlet module supplies only `ServletExchange` plus a slim `UmbrellaFilter`. Fix behaviour in the shared class; the two `ServletExchange`/`UmbrellaFilter` pairs are still parallel files, so genuinely servlet-specific changes and their tests do need applying twice.
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

Publishing: Maven artifacts are GPG-signed and deployed via `central-publishing-maven-plugin` (Central Portal); npm packages publish during the Maven `deploy` phase (`can-npm-publish` guard). `exists-maven-plugin` skips already-published versions — **version bumps are required for any republish**.

## Tool Requirements

- **Java**: 21 to build (maven-enforcer-plugin); integration/base jars target Java 11 for consumer compatibility
- **Node.js**: v22.17.0 (`.nvmrc`; frontend-maven-plugin installs its own copy — a system pnpm newer than 8 will fail on this repo's lockfile, so build through Maven)
- **pnpm**: 8.6.10
- **Maven**: 3.x

## Testing

JUnit 5 + Mockito; `umbrella-java` uses OkHttp MockWebServer.

- `umbrella-base/umbrella-java`: `UmbrellaServiceTest` (init, block, timeout, monitor, disabled, 403 retry, 429) and `RequestFilteringTest` (regex, CIDR, truncation)
- `umbrella-integration/umbrella-tomcat` and `-javax`: `UmbrellaFilterTest`, 18 tests each (kept in sync manually — update both)
- `umbrella-base/umbrella-typescript`: `node --test`, run by the Maven `test` phase via `pnpm run test`
- `umbrella-integration/umbrella-express`: `node --test`, same wiring
- `umbrella-api`: no tests; the spec is validated by the generators in the consumer modules

## Request Filtering (opt-in)

Both servlet integrations skip requests that don't need checking, configured like every other property (init-param, then system property, then environment variable):

| init-param | Property | Purpose |
| --- | --- | --- |
| `exclusion-regex` | `umbrella.exclusion.regex` | Paths matching are skipped — typically static assets |
| `inclusion-regex` | `umbrella.inclusion.regex` | When set, only matching paths are checked |
| `skip-ips` | `umbrella.skip.ips` | Comma-separated IPs/CIDR blocks to skip (health checks, internal traffic) |

A malformed pattern or address fails at `init()` rather than silently disabling protection. Express takes `excludePath` / `includePath` regexes instead.

Collected header values are truncated (`StringTruncator`) so an oversized request cannot produce an oversized payload; `X-Forwarded-For` is truncated from the front to keep the entries nearest this server.

## Known Issues / Gotchas

- **Releases need new secrets**: publishing moved from the decommissioned OSSRH to the Central Publisher Portal, so the release workflow expects `CENTRAL_USERNAME` / `CENTRAL_TOKEN` instead of `OSSRH_USERNAME` / `OSSRH_TOKEN`. The workflow is manual (`workflow_dispatch`) and has a `dryRun` input. **These secrets are not set up yet** — the first release will fail until they are.
- **Nothing has been published from the new coordinates yet**: `umbrella-java` is at 0.0.5 under `io.dataspray.umbrella.base` (0.0.4 exists only under the old `…integration` groupId), and the tomcat modules are at 0.0.8 (0.0.7 is published with the old dependency coordinates). `exists-maven-plugin` skips already-published versions, so any republish needs a version bump.
- **Tomcat poms are unparented**: `umbrella-tomcat`/`-javax` declare no `<parent>` and duplicate ~100 lines of publishing config each. Version and plugin changes must be made in both.
- The enterprise repo consumes `umbrella-api`'s `json-schema` artifact from `~/.m2`; it has never been published. Build this repo before building that one.
- The vendor extension `x-existingJavaType` in the spec is rewritten to `existingJavaType` by `generateJsonSchema.js` for the enterprise repo's jsonschema2pojo. Keep that rewrite if you touch the generator.

## Git Configuration

Commits must be GPG-signed. Configure git before committing:
```bash
git config user.name 'Matus Faro'
git config user.email 'matus@matus.io'
git config commit.gpgsign true
git config user.signingkey matus@matus.io
```
