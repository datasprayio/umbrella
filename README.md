# Umbrella

Umbrella is a bot-protection and request-firewall service. Your web server sends
request metadata to the Umbrella API, which evaluates your rules and returns an
allow or block decision.

This repository holds the open-source client side: the API specification, the
base clients, and the web server integrations. The backend is proprietary and
lives separately.

## Modules

| Module | Artifact | What it is |
| --- | --- | --- |
| `umbrella-api` | `io.dataspray.umbrella:umbrella-api` | The OpenAPI specification, the source of truth for every client |
| `umbrella-base/umbrella-java` | `io.dataspray.umbrella.base:umbrella-java` | Java client: HTTP communication, operation modes, background ping |
| `umbrella-base/umbrella-typescript` | npm `umbrella-client` | TypeScript client, same semantics as the Java client |
| `umbrella-integration/umbrella-tomcat` | `io.dataspray.umbrella.integration:umbrella-tomcat` | Servlet filter for Jakarta (Tomcat 10.1+) |
| `umbrella-integration/umbrella-tomcat-javax` | `io.dataspray.umbrella.integration:umbrella-tomcat-javax` | Servlet filter for legacy `javax.servlet` |
| `umbrella-integration/umbrella-express` | npm `umbrella-express` | Express middleware |

## Getting started

### Tomcat (Jakarta)

Add the dependency, then register the filter in `web.xml`:

```xml
<filter>
    <filter-name>UmbrellaFilter</filter-name>
    <filter-class>io.dataspray.umbrella.integration.tomcat.UmbrellaFilter</filter-class>
    <init-param>
        <param-name>org</param-name>
        <param-value>my-org</param-value>
    </init-param>
    <init-param>
        <param-name>api-key</param-name>
        <param-value>my-api-key</param-value>
    </init-param>
</filter>
<filter-mapping>
    <filter-name>UmbrellaFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
```

Every setting can also come from a system property (`umbrella.org`,
`umbrella.api.key`, `umbrella.endpoint.url`, `umbrella.enabled`) or an
environment variable (`UMBRELLA_ORG`, `UMBRELLA_API_KEY`, …). See the module
READMEs for details.

### Express

```js
const {umbrella} = require('umbrella-express');

const protect = umbrella({
    org: 'my-org',
    apiKey: process.env.UMBRELLA_API_KEY,
    excludePath: /\.(css|js|png|jpg|svg|woff2?)$/,
});

await protect.start();
app.use(protect);
```

## How it works

Umbrella uses a two-tier client design. The base client owns HTTP
communication, the operation mode and the background ping; the integration owns
request interception and enforcement. See
[CLIENT-ARCHITECTURE.md](CLIENT-ARCHITECTURE.md).

The server drives the operation mode:

- **BLOCKING** — the integration waits for a decision on every request
- **MONITOR** — events are reported asynchronously and requests always continue
- **DISABLED** — no events are sent

**Umbrella fails open.** A timeout, an error, an unreachable API or an
unrecognised response all allow the request through. Only an explicit block
decision stops one.

## Building

```bash
mvn clean install
```

Requires Java 21 and Maven 3. Node and pnpm are installed automatically by the
build. See [CLAUDE.md](CLAUDE.md) for the development workflow.

## License

MIT. See [LICENSE](LICENSE).
