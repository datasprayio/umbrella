# Umbrella Client Architecture

This document describes the architecture and features of Umbrella client libraries to guide implementation for additional languages and web server frameworks.

## Overview

Umbrella provides a two-tier client architecture:

1. **Base Client** - Language-specific HTTP client library that communicates with the Umbrella API
2. **Web Server Integration** - Framework-specific middleware/filter that intercepts requests and applies Umbrella rules

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Web Application                           │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│              Web Server Integration Layer                    │
│  (Tomcat Filter, Express Middleware, etc.)                   │
│                                                               │
│  - Intercepts HTTP requests                                  │
│  - Extracts HTTP metadata                                    │
│  - Applies actions (block, headers, cookies)                 │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                    Base Client Library                       │
│  (umbrella-java, umbrella-typescript)                        │
│                                                               │
│  - API communication                                         │
│  - Background pinging                                        │
│  - Mode management (BLOCKING/MONITOR/DISABLED)               │
│  - Configuration synchronization                             │
│  - Async event handling                                      │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            │ HTTPS
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                     Umbrella API                             │
│               https://api.umbrella.dataspray.io              │
└─────────────────────────────────────────────────────────────┘
```

---

## Base Client Features

The base client is responsible for all API communication and state management. It should be framework-agnostic and reusable across different web server integrations.

### Required API Operations

All base clients must implement these API operations (defined in OpenAPI spec at `umbrella-api/src/main/openapi/umbrella-api.yaml`):

#### 1. Health API - Node Ping
**Endpoint:** `POST /org/{org}/node/ping`

**Purpose:** Register the node and receive configuration updates

**Request:**
```json
{
  "nodeId": "hostname; app-name; server-info; sid=<uuid>"
}
```

**Response:**
```json
{
  "config": {
    "mode": "BLOCKING|MONITOR|DISABLED",
    "timeoutMs": 5000,
    "collectAdditionalHeaders": ["X-Custom-Header"]
  }
}
```

**Implementation Requirements:**
- Execute on initialization to validate API key
- Schedule background pings every 10 minutes
- Update local configuration on each response
- Handle errors gracefully (403 = invalid API key, continue disabled)

#### 2. Ingest API - HTTP Event
**Endpoint:** `POST /org/{org}/event/http`

**Purpose:** Submit HTTP request metadata for validation/monitoring

**Request:**
```json
{
  "nodeId": "...",
  "currentMode": "BLOCKING|MONITOR",
  "httpMetadata": { /* extensive metadata - see below */ }
}
```

**Response:**
```json
{
  "action": {
    "requestProcess": "ALLOW|BLOCK",
    "responseStatus": 403,
    "requestMetadata": {"key": "value"},
    "responseHeaders": {"X-Custom": "value"},
    "responseCookies": [{"name": "...", "value": "..."}]
  },
  "configRefresh": { /* optional config update */ }
}
```

**Implementation Requirements:**
- **BLOCKING mode:** Synchronous call, wait for response, apply action
- **MONITOR mode:** Async call in background, always allow request
- **DISABLED mode:** Skip API call entirely
- Handle 429 (rate limit) by temporarily disabling until next ping
- On error in BLOCKING mode, default to ALLOW

#### 3. Ingest API - Custom Event (Optional)
**Endpoint:** `POST /org/{org}/event/{eventType}`

**Purpose:** Submit custom application events

**Request:**
```json
{
  "nodeId": "...",
  "currentMode": "MONITOR",
  "key": "user-signup",
  "metadata": {"userId": "123", "plan": "pro"}
}
```

**Response:**
```json
{
  "action": {"custom": "data"},
  "configRefresh": { /* optional */ }
}
```

### Core Client Responsibilities

#### 1. Initialization
```java
void init(
    String orgName,           // Organization identifier
    String apiKey,            // API authentication key
    List<String> nodeIdParts, // Parts to construct unique node ID
    Optional<String> endpoint // Optional: custom API endpoint URL
)
```

**Tasks:**
- Construct unique node identifier from parts + random session ID
- Initialize API client with authentication
- Perform initial ping to validate credentials
- Start background ping scheduler (10-minute interval)
- Handle initialization failures gracefully

**Node ID Construction:**
The node ID should uniquely identify this instance. Include:
- Hostname
- Application name
- Server/framework info
- Random session ID: `sid=<uuid>`

Format: `hostname; app-name; server-version; sid=<uuid>`

#### 2. Configuration Management
```java
class Config {
    OperationMode mode;              // BLOCKING, MONITOR, DISABLED
    Long timeoutMs;                  // Max latency for blocking calls
    List<String> collectAdditionalHeaders; // Extra headers to collect
}
```

**Behaviors:**
- Store config as volatile/atomic variable (thread-safe)
- Update on ping response or httpEvent response
- Apply timeout to HTTP client when in BLOCKING mode
- Return additional headers list to web server integration

#### 3. HTTP Event Processing
```java
HttpAction httpEvent(HttpMetadata data)
```

**Mode-Specific Logic:**

**BLOCKING Mode:**
- Make synchronous API call
- Wait for response (with timeout)
- Return action to caller
- On error: log warning, return DEFAULT_ALLOW_ACTION

**MONITOR Mode:**
- Submit async API call in background thread pool
- Return DEFAULT_ALLOW_ACTION immediately
- Log errors silently

**DISABLED Mode:**
- Skip API call entirely
- Return DEFAULT_ALLOW_ACTION

**Rate Limiting (429 Response):**
- Temporarily set mode to DISABLED
- Will re-enable on next successful ping

#### 4. Background Task Management

**Ping Scheduler:**
- Single-threaded executor for periodic tasks
- Daemon thread (shouldn't prevent JVM shutdown)
- Execute ping every 10 minutes
- Log ping failures at WARNING level

**Async Event Queue (MONITOR mode):**
- Thread pool executor for background HTTP events
- Share with ping scheduler or use separate pool
- Queue size should prevent memory exhaustion

#### 5. Shutdown
```java
void shutdown()
```

- Gracefully shutdown thread pools
- Don't wait for background tasks (daemon threads)
- Allow in-flight requests to complete or timeout

### HTTP Metadata Collection

The `HttpMetadata` object captures comprehensive request information. All fields are optional but collect as many as available:

#### Basic Request Info
- `ts`: Request timestamp (ISO-8601)
- `uri`: Request URI path
- `method`: HTTP method (GET, POST, etc.)
- `proto`: Protocol scheme (http, https)
- `contentLength`: Request body size

#### IP and Proxy Headers
- `ip`: Direct client IP
- `port`: Client port
- `hXFwdFor`: X-Forwarded-For header
- `hXFwdProto`: X-Forwarded-Proto
- `hXFwdHost`: X-Forwarded-Host
- `hXFwdPort`: X-Forwarded-Port
- `hCfConnIp`: CF-Connecting-IP (Cloudflare)
- `hTrueClientIp`: True-Client-IP
- `hXRealIp`: X-Real-IP
- `hFwd`: Forwarded header (RFC 7239)
- `hVia`: Via header

#### Standard Headers
- `hUserAgent`: User-Agent
- `hAccept`: Accept
- `hAcceptLanguage`: Accept-Language
- `hAcceptCharset`: Accept-Charset
- `hAcceptEncoding`: Accept-Encoding
- `hContentType`: Content-Type
- `hConnection`: Connection
- `hHost`: Host
- `hOrigin`: Origin
- `hReferer`: Referer
- `hFrom`: From
- `hPragma`: Pragma
- `hXReqWith`: X-Requested-With
- `hXReqId`: X-Request-ID

#### Authentication Headers
- `hAuthPrefix`: Authorization scheme (e.g., "Bearer", "Basic")
- `hAuthSize`: Length of Authorization header value

#### Security Headers (Client Hints)
- `hSecChUa`: Sec-CH-UA
- `hSecChUaMobile`: Sec-CH-UA-Mobile
- `hSecChUaPlatform`: Sec-CH-UA-Platform
- `hSecChUaModel`: Sec-CH-UA-Model
- `hSecChUaFull`: Sec-CH-UA-Full-Version
- `hSecChUaArch`: Sec-CH-UA-Arch
- `hSecChDevMem`: Sec-CH-Device-Memory
- `hSecFetchDest`: Sec-Fetch-Dest
- `hSecFetchMode`: Sec-Fetch-Mode
- `hSecFetchSite`: Sec-Fetch-Site
- `hSecFetchUser`: Sec-Fetch-User

#### TLS Information
- `tlsCipher`: TLS cipher suite
- `tlsProto`: TLS protocol version

#### Metadata Lists
- `headerNames`: List of all request header names
- `cookieNames`: List of all cookie names (not values!)
- `additionalHeaders`: Map of header name -> value for configured additional headers

---

## Web Server Integration Features

The web server integration layer intercepts HTTP requests and applies Umbrella's security rules. It's framework-specific.

### Required Integration Points

#### 1. Filter/Middleware Registration

The integration must hook into the web server's request processing pipeline as early as possible.

**Examples:**
- **Java Servlet:** Implement `javax.servlet.Filter` or `jakarta.servlet.Filter`
- **Express.js:** Middleware function `(req, res, next) => {}`
- **Django:** Middleware class with `__call__` method
- **Flask:** `@app.before_request` decorator
- **ASP.NET:** `IHttpModule` or middleware in pipeline

#### 2. Configuration Loading

Support multiple configuration sources in priority order:

1. Framework-specific config (e.g., filter init-params, middleware options)
2. System properties (e.g., `-Dumbrella.api.key=...`)
3. Environment variables (e.g., `UMBRELLA_API_KEY`)

**Required Configuration:**
- `org` / `UMBRELLA_ORG`: Organization name (required)
- `api-key` / `UMBRELLA_API_KEY`: API key (required)
- `enabled` / `UMBRELLA_ENABLED`: Enable/disable flag (optional, default: true)
- `endpoint-url` / `UMBRELLA_ENDPOINT_URL`: Custom endpoint (optional)

#### 3. Request Metadata Extraction

Extract all available HTTP metadata from the request object. See "HTTP Metadata Collection" section above for complete list.

**Key Implementation Notes:**
- Parse `Authorization` header to extract scheme prefix and total size (not the token itself)
- Collect cookie names but NOT cookie values
- Extract TLS/SSL session information if available
- Handle missing headers gracefully (all fields optional)

#### 4. Action Application

Apply the `HttpAction` response from the API to modify the request/response:

**Request Modifications:**
```java
if (action.requestMetadata != null) {
    // Add custom attributes to request context
    // e.g., request.setAttribute(key, value)
}
```

**Response Modifications:**
```java
if (action.responseStatus != null) {
    response.setStatus(action.responseStatus);
}

if (action.responseHeaders != null) {
    // Add custom headers to response
    response.setHeader(name, value);
}

if (action.responseCookies != null) {
    // Add cookies with full configuration
    // (domain, path, maxAge, secure, httpOnly, sameSite)
}
```

**Request Processing:**
```java
if (action.requestProcess == RequestProcess.BLOCK) {
    // Stop processing, return response immediately
    return;
} else {
    // Continue to next filter/handler
    filterChain.doFilter(request, response);
}
```

#### 5. Error Handling

**Graceful Degradation:**
- If base client initialization fails, log error and disable filter
- If httpEvent call throws exception, log and allow request
- Never block requests due to Umbrella errors (fail-open)

**Logging Levels:**
- `INFO`: Initialization success, mode changes
- `WARNING`: API failures, ping failures
- `SEVERE`: Invalid credentials, initialization errors
- `FINEST/DEBUG`: Request processing, skipped requests

#### 6. Server Identification

Build a unique node identifier from server context:

**Recommended Components:**
- Hostname (from OS)
- Application name (from deployment descriptor, config)
- Server name and version (e.g., "Apache Tomcat/10.1.5")
- Virtual server name (if available)
- Random session ID

**Example Construction:**
```java
List<String> parts = Arrays.asList(
    InetAddress.getLocalHost().getHostName(),  // "web-server-01"
    servletContext.getServletContextName(),    // "MyApp"
    servletContext.getServerInfo(),            // "Apache Tomcat/10.1.5"
    servletContext.getVirtualServerName(),     // "example.com"
    "sid=" + UUID.randomUUID()                 // "sid=123e4567-..."
);
String nodeId = String.join("; ", parts);
```

---

## Implementation Checklist

### Base Client

- [ ] Generate client from OpenAPI spec (or implement manually)
- [ ] Implement authentication (API key in `Authorization: apikey <key>` header)
- [ ] Implement `init()` with node ID construction
- [ ] Implement initial ping with credential validation
- [ ] Implement background ping scheduler (10-minute interval)
- [ ] Implement mode-aware `httpEvent()` method
  - [ ] BLOCKING: synchronous with timeout
  - [ ] MONITOR: async/background
  - [ ] DISABLED: no-op
- [ ] Implement config management (thread-safe)
- [ ] Implement dynamic timeout configuration
- [ ] Handle 429 rate limiting (temporarily disable)
- [ ] Implement graceful error handling (fail-open)
- [ ] Implement `shutdown()` for clean resource cleanup
- [ ] Add comprehensive logging

### Web Server Integration

- [ ] Hook into request processing pipeline
- [ ] Load configuration from multiple sources
- [ ] Initialize base client on startup
- [ ] Extract comprehensive HTTP metadata
  - [ ] Basic request info (URI, method, protocol)
  - [ ] IP and proxy headers
  - [ ] Standard HTTP headers
  - [ ] Authentication info (prefix + size only)
  - [ ] Security headers
  - [ ] TLS info
  - [ ] Header/cookie name lists
  - [ ] Additional configured headers
- [ ] Call base client `httpEvent()` method
- [ ] Apply response actions
  - [ ] Set response status
  - [ ] Add response headers
  - [ ] Set cookies (with all attributes)
  - [ ] Add request attributes/metadata
- [ ] Implement request blocking logic
- [ ] Handle errors gracefully (fail-open)
- [ ] Generate unique server identifier
- [ ] Add comprehensive logging
- [ ] Write README with installation instructions
- [ ] Add tests

---

## Example Implementations

### Current Implementations

**Java (Base Client):**
- Module: `umbrella-base/umbrella-java`
- GroupId: `io.dataspray.umbrella.base`
- Files:
  - `UmbrellaService.java` - Interface
  - `UmbrellaServiceImpl.java` - Implementation with OkHttp client

**Java Servlet (Tomcat Jakarta):**
- Module: `umbrella-integration/umbrella-tomcat`
- Files:
  - `UmbrellaFilter.java` - Jakarta Servlet Filter
- Servlet API: `jakarta.servlet.*`
- Java Version: 11+

**Java Servlet (Tomcat Javax - Legacy):**
- Module: `umbrella-integration/umbrella-tomcat-javax`
- Files:
  - `UmbrellaFilter.java` - Javax Servlet Filter
- Servlet API: `javax.servlet.*`
- Java Version: 11+

**TypeScript (Base Client):**
- Module: `umbrella-base/umbrella-typescript`
- GroupId: `io.dataspray.umbrella.base`
- Files:
  - `umbrellaClient.ts` - Wrapper around generated client
  - `client/*` - Generated from OpenAPI spec
- Uses fetch API for HTTP requests

**Express.js:**
- Module: `umbrella-integration/umbrella-express`
- Status: Not yet implemented

---

## Testing Guidelines

### Base Client Tests

1. **Initialization:**
   - Valid credentials → successful ping
   - Invalid credentials (403) → disabled mode
   - Network error → disabled mode, retry on ping

2. **Mode Behavior:**
   - BLOCKING mode → synchronous API call
   - MONITOR mode → async API call, immediate return
   - DISABLED mode → no API call

3. **Configuration Updates:**
   - Config from ping response
   - Config from httpEvent response
   - Timeout applied in BLOCKING mode only

4. **Background Tasks:**
   - Ping executes every 10 minutes
   - Thread pool shutdown on destroy

5. **Error Handling:**
   - 429 response → temporarily disable
   - Network errors → log and continue
   - Timeout → default to allow

### Web Server Integration Tests

1. **Configuration Loading:**
   - Load from framework config
   - Load from system properties
   - Load from environment variables
   - Priority order correct

2. **Metadata Extraction:**
   - All standard headers captured
   - IP and proxy headers extracted
   - Cookie names collected (not values)
   - TLS info extracted when available

3. **Action Application:**
   - Block action stops request processing
   - Response status set correctly
   - Headers added to response
   - Cookies set with all attributes
   - Request attributes added

4. **Error Handling:**
   - API errors → allow request
   - Missing base client → allow request
   - Invalid config → filter disabled

---

## Migration Guide

When adding support for a new language or framework:

1. **Start with Base Client:**
   - Generate client from OpenAPI spec if possible
   - Implement core client wrapper (like `UmbrellaClient`)
   - Add initialization, ping, and httpEvent methods
   - Implement background scheduler
   - Add comprehensive tests

2. **Implement Web Server Integration:**
   - Study framework's middleware/filter architecture
   - Implement request interception
   - Extract all available HTTP metadata
   - Apply response actions appropriately
   - Handle framework-specific cookie/header APIs

3. **Documentation:**
   - Create README with installation steps
   - Document configuration options
   - Provide code examples
   - Add to main CLAUDE.md

4. **Publishing:**
   - Add Maven module (if applicable)
   - Configure publishing to package registry
   - Update parent pom.xml
   - Add to CI/CD pipeline
