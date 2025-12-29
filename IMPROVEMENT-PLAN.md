# Umbrella Improvement Plan

Based on analysis of DataDome's client architecture, this plan outlines recommended improvements to Umbrella to enhance performance while maintaining architectural flexibility.

## Executive Summary

DataDome's implementation reveals several performance optimizations that Umbrella currently lacks. The most impactful improvements would be:

1. **URL Pattern Filtering** - Reduce API calls by 50-80%
2. **IP Whitelisting** - Skip internal/trusted traffic
3. **Field Truncation** - Prevent payload issues

These additions would make Umbrella competitive with DataDome on performance while maintaining superior flexibility through its two-tier architecture and multi-mode operation.

---

## High Priority Improvements

### 1. URL Pattern Filtering

**Problem:** Umbrella currently validates every request, including static assets (CSS, JS, images, fonts), which don't need bot protection and waste API calls.

**Solution:** Add configurable regex-based inclusion/exclusion patterns.

**Configuration:**
```java
// Filter init-params
<init-param>
    <param-name>umbrella.inclusion.regex</param-name>
    <param-value>^/api/.*</param-value> <!-- Only check /api/* paths -->
</init-param>

<init-param>
    <param-name>umbrella.exclusion.regex</param-name>
    <param-value>(?i)\.(css|js|png|jpg|gif|svg|ico|woff|woff2|ttf|eot|mp4|webm)$</param-value>
</init-param>
```

**Implementation Steps:**
1. Add `inclusionRegex` and `exclusionRegex` fields to `UmbrellaService`
2. Parse regex patterns from configuration in `UmbrellaFilter.init()`
3. Check patterns in `UmbrellaFilter.doFilter()` before calling `umbrellaService.httpEvent()`
4. If exclusion matches, skip Umbrella entirely
5. If inclusion is set and doesn't match, skip Umbrella
6. Add tests for various URL patterns

**Default Exclusion Pattern:**
```regex
(?i)\.(avi|flv|mka|mkv|mov|mp4|mpeg|mpg|mp3|flac|ogg|ogm|opus|wav|webm|webp|bmp|gif|ico|jpeg|jpg|png|svg|svgz|swf|eot|otf|ttf|woff|woff2|css|less|js|map)$
```

**Expected Impact:**
- 50-80% reduction in API calls for typical web applications
- Significantly reduced latency for static asset requests
- Lower costs (fewer API calls)
- Reduced load on Umbrella API servers

**Files to Modify:**
- `umbrella-integration/umbrella-java/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaService.java`
- `umbrella-integration/umbrella-java/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaServiceImpl.java`
- `umbrella-integration/umbrella-tomcat/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaFilter.java`
- `umbrella-integration/umbrella-tomcat-javax/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaFilter.java`

**OpenAPI Changes:**
Consider adding exclusion patterns to `Config` object so they can be updated dynamically via ping response.

---

### 2. IP Whitelisting (CIDR Subnet Matching)

**Problem:** Internal traffic (health checks, monitoring, load balancers) unnecessarily hits the Umbrella API.

**Solution:** Add CIDR-based IP whitelisting to skip trusted IPs/subnets.

**Configuration:**
```java
<init-param>
    <param-name>umbrella.skip.ips</param-name>
    <param-value>10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.1/32</param-value>
</init-param>
```

**Implementation Steps:**
1. Create `IpAddressMatcher` utility class (similar to DataDome's)
   - Support IPv4 CIDR notation (e.g., `192.168.1.0/24`)
   - Support IPv6 CIDR notation (e.g., `2001:db8::/32`)
   - Support single IPs (e.g., `127.0.0.1`)
2. Parse comma-separated skip IPs in `UmbrellaFilter.init()`
3. Check client IP against matchers in `UmbrellaFilter.doFilter()` before Umbrella call
4. If matched, skip Umbrella entirely and continue filter chain
5. Add comprehensive tests for IPv4/IPv6 matching

**Use Cases:**
- Internal health check endpoints (`10.0.0.0/8`)
- Office networks (`192.168.0.0/16`)
- Load balancer health checks (specific IPs)
- Monitoring tools (`172.16.0.0/12`)
- localhost (`127.0.0.1`, `::1`)

**Expected Impact:**
- 10-30% reduction in API calls (depending on internal traffic volume)
- Faster health checks (no Umbrella latency)
- Reduced noise in Umbrella analytics

**Files to Create:**
- `umbrella-integration/umbrella-java/src/main/java/io/dataspray/umbrella/integration/tomcat/IpAddressMatcher.java`

**Files to Modify:**
- `umbrella-integration/umbrella-tomcat/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaFilter.java`
- `umbrella-integration/umbrella-tomcat-javax/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaFilter.java`

**Reference Implementation:**
See DataDome's `IpAddressMatcher.java` for CIDR matching logic (likely based on Spring Security's implementation).

---

### 3. Field Truncation

**Problem:** Malicious or misconfigured clients could send enormous headers, causing:
- Oversized API payloads
- Potential denial of service
- Unnecessary bandwidth usage
- API request failures

**Solution:** Implement maximum field lengths with smart truncation.

**Implementation Steps:**
1. Define maximum lengths for each field in `HttpMetadata`
2. Add truncation utility methods:
   - `truncateString(String value, int maxBytes)` - simple truncation
   - `truncateFromEnd(String value, int maxBytes)` - for X-Forwarded-For
   - `truncateUrlEncoded(String value, int maxBytes)` - URL-encoding aware
3. Apply truncation when building `HttpMetadata` in `UmbrellaFilter`
4. Document field limits in CLIENT-ARCHITECTURE.md
5. Add tests for edge cases (multibyte chars, exact limits, etc.)

**Proposed Field Limits:**
```java
// Basic fields
uri: 2048 bytes
method: 10 bytes
proto: 8 bytes
ip: 45 bytes (IPv6 max)

// Headers - standard
hUserAgent: 768 bytes
hHost: 512 bytes
hReferer: 1024 bytes
hOrigin: 512 bytes
hContentType: 128 bytes
hAccept: 512 bytes
hAcceptLanguage: 256 bytes
hAcceptEncoding: 128 bytes
hAcceptCharset: 128 bytes

// Headers - proxy/forwarding
hXFwdFor: 512 bytes (truncate from END to preserve client IP)
hXFwdProto: 16 bytes
hXFwdHost: 512 bytes
hXFwdPort: 8 bytes
hFwd: 512 bytes
hVia: 256 bytes

// Headers - special
hAuthSize: already a size, no truncation needed
headerNames: 512 bytes (comma-separated list)
cookieNames: 512 bytes (comma-separated list)
additionalHeaders: 256 bytes per header value
```

**Truncation Strategy:**
- Most fields: Truncate from beginning (keep end)
- `hXFwdFor`: Truncate from END (keep most recent/client IP)
- UTF-8 aware: Don't split multibyte characters
- Log warning when truncation occurs

**Expected Impact:**
- Prevents API failures from oversized payloads
- Protects against accidental/malicious DoS
- Consistent payload sizes for better API performance
- Better error messages (know which field is too large)

**Files to Modify:**
- `umbrella-integration/umbrella-tomcat/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaFilter.java`
- `umbrella-integration/umbrella-tomcat-javax/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaFilter.java`

**Files to Create:**
- `umbrella-integration/umbrella-java/src/main/java/io/dataspray/umbrella/integration/tomcat/StringTruncator.java` (utility class)

---

## Medium Priority Improvements

### 4. HTTP Proxy Support

**Problem:** Enterprise environments often require all outbound HTTP to go through a corporate proxy.

**Solution:** Add configurable HTTP/HTTPS proxy support.

**Configuration:**
```java
<init-param>
    <param-name>umbrella.proxy.host</param-name>
    <param-value>proxy.example.com</param-value>
</init-param>

<init-param>
    <param-name>umbrella.proxy.port</param-name>
    <param-value>8080</param-value>
</init-param>

<init-param>
    <param-name>umbrella.proxy.ssl</param-name>
    <param-value>false</param-value>
</init-param>

<!-- Optional: Proxy authentication -->
<init-param>
    <param-name>umbrella.proxy.username</param-name>
    <param-value>proxyuser</param-value>
</init-param>

<init-param>
    <param-name>umbrella.proxy.password</param-name>
    <param-value>${PROXY_PASSWORD}</param-value>
</init-param>
```

**Implementation Steps:**
1. Add proxy configuration fields to `UmbrellaServiceImpl`
2. Parse proxy config in `UmbrellaFilter.init()`
3. Pass proxy config to `UmbrellaServiceImpl.init()`
4. Configure OkHttpClient with proxy settings
5. Support proxy authentication (Basic auth)
6. Add tests with mock proxy server

**Expected Impact:**
- Enables deployment in strict enterprise environments
- Supports environments with no direct internet access
- Allows proxy-based filtering/logging

**Files to Modify:**
- `umbrella-integration/umbrella-java/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaService.java`
- `umbrella-integration/umbrella-java/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaServiceImpl.java`
- `umbrella-integration/umbrella-tomcat/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaFilter.java`
- `umbrella-integration/umbrella-tomcat-javax/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaFilter.java`

---

### 5. DNS Caching with TTL

**Problem:** DNS lookups add 5-20ms of latency to each API call.

**Solution:** Implement DNS result caching with configurable TTL.

**Implementation Steps:**
1. Create custom `DnsResolver` class
2. Cache DNS results with 5-minute TTL (configurable)
3. Use cached results for subsequent requests
4. Refresh cache on expiry or failure
5. Log DNS resolution times for monitoring

**Configuration:**
```java
<init-param>
    <param-name>umbrella.dns.cache.ttl.seconds</param-name>
    <param-value>300</param-value> <!-- 5 minutes -->
</init-param>
```

**Expected Impact:**
- 5-20ms latency reduction per request
- More consistent response times
- Reduced DNS server load

**Files to Create:**
- `umbrella-integration/umbrella-java/src/main/java/io/dataspray/umbrella/integration/tomcat/CachingDnsResolver.java`

**Files to Modify:**
- `umbrella-integration/umbrella-java/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaServiceImpl.java`

---

### 6. Performance Metrics (Request Attributes)

**Problem:** Applications can't measure Umbrella's impact on latency.

**Solution:** Add request attribute with time spent in Umbrella.

**Implementation:**
```java
// In UmbrellaFilter
long startTime = System.currentTimeMillis();
HttpAction action = umbrellaService.httpEvent(data);
long elapsedTime = System.currentTimeMillis() - startTime;
httpServletRequest.setAttribute("umbrella.spent_time_ms", elapsedTime);
```

**Application Usage:**
```java
Long umbrellaTime = (Long) request.getAttribute("umbrella.spent_time_ms");
if (umbrellaTime != null && umbrellaTime > 100) {
    logger.warn("Umbrella took {}ms", umbrellaTime);
}
```

**Expected Impact:**
- Applications can monitor Umbrella overhead
- Alert on high latency
- Track SLA compliance
- Identify performance issues

**Files to Modify:**
- `umbrella-integration/umbrella-tomcat/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaFilter.java`
- `umbrella-integration/umbrella-tomcat-javax/src/main/java/io/dataspray/umbrella/integration/tomcat/UmbrellaFilter.java`

---

## Low Priority Improvements

### 7. Environment Variable Substitution

**Problem:** Sensitive values (API keys) in `web.xml` are visible in version control.

**Solution:** Support `${VARIABLE}` syntax for environment variable substitution.

**Example:**
```xml
<init-param>
    <param-name>api-key</param-name>
    <param-value>${UMBRELLA_API_KEY}</param-value>
</init-param>
```

**Implementation:**
```java
private String resolveValue(String rawValue) {
    Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");
    Matcher matcher = pattern.matcher(rawValue);

    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
        String varName = matcher.group(1);
        String varValue = System.getenv(varName);
        if (varValue == null) {
            throw new ServletException("Undefined variable: " + varName);
        }
        matcher.appendReplacement(result, varValue);
    }
    matcher.appendTail(result);
    return result.toString();
}
```

**Expected Impact:**
- Cleaner configuration management
- Better security (no secrets in version control)
- Easier deployment across environments

---

### 8. Header-Based Session Tracking

**Problem:** Mobile apps and some APIs don't support cookies.

**Solution:** Allow session tracking via custom header instead of cookie.

**Configuration:**
```java
<init-param>
    <param-name>umbrella.session.header</param-name>
    <param-value>X-Umbrella-Session</param-value>
</init-param>
```

**Implementation:**
Include header in node ID or send as separate field to API.

**Expected Impact:**
- Better support for mobile apps
- More flexible session management
- Works in cookie-less environments

---

### 9. Response Validation Header

**Problem:** No protection against response spoofing attacks.

**Solution:** Add `X-Umbrella-Response` header that must match status code.

**API Response:**
```
HTTP/1.1 403 Forbidden
X-Umbrella-Response: 403
Content-Type: application/json

{"action": {"requestProcess": "BLOCK", "responseStatus": 403}}
```

**Validation:**
```java
if (response.getHeader("X-Umbrella-Response") == null ||
    !response.getHeader("X-Umbrella-Response").equals(String.valueOf(statusCode))) {
    logger.warning("Invalid Umbrella response - possible spoofing");
    return DEFAULT_ALLOW_ACTION;
}
```

**Expected Impact:**
- Protection against MITM attacks
- Validates response authenticity
- Additional security layer

---

## Implementation Priorities

### Phase 1: Core Performance (Q1 2025)
1. URL Pattern Filtering
2. IP Whitelisting
3. Field Truncation

**Goal:** 50-80% reduction in API calls, prevent payload issues

### Phase 2: Enterprise Features (Q2 2025)
4. HTTP Proxy Support
5. DNS Caching
6. Performance Metrics

**Goal:** Enable enterprise deployment, reduce latency

### Phase 3: Developer Experience (Q3 2025)
7. Environment Variable Substitution
8. Header-Based Sessions
9. Response Validation

**Goal:** Improve security and flexibility

---

## Testing Requirements

### Unit Tests
- Regex pattern matching (inclusion/exclusion)
- CIDR IP matching (IPv4, IPv6, edge cases)
- String truncation (multibyte chars, exact limits)
- Environment variable substitution

### Integration Tests
- End-to-end with URL filtering
- End-to-end with IP whitelisting
- Proxy configuration
- DNS caching behavior

### Performance Tests
- Benchmark API call reduction with filtering
- Measure latency with/without DNS caching
- Stress test with oversized headers

---

## Documentation Updates

### CLIENT-ARCHITECTURE.md
- Add URL filtering section
- Add IP whitelisting section
- Add field truncation limits
- Update implementation checklist

### README Files
- Update umbrella-tomcat/README.md with new config options
- Update umbrella-tomcat-javax/README.md
- Add configuration examples

### CLAUDE.md
- Note new configuration options
- Update development workflow

---

## Backward Compatibility

All improvements should be **backward compatible**:
- New features optional (disabled by default)
- No breaking changes to existing API
- Existing deployments continue to work

**Default Behavior:**
- No URL filtering (check all requests)
- No IP whitelisting (check all IPs)
- No field truncation (unlimited)
- No proxy (direct connection)
- DNS caching disabled by default

Users opt-in to new features via configuration.

---

## Success Metrics

### Performance
- **API Call Reduction:** 50-80% fewer calls (URL filtering)
- **Latency Reduction:** 5-20ms faster (DNS caching)
- **Payload Safety:** Zero oversized payload failures

### Adoption
- **Enterprise Deployments:** 10+ enterprises using proxy support
- **Configuration Usage:** 70%+ deployments enable URL filtering

### Reliability
- **Error Rate:** <0.1% errors from new features
- **Backward Compatibility:** 100% existing deployments continue working

---

## Open Questions

1. **Should URL filtering be enabled by default?**
   - Pros: Immediate performance benefit
   - Cons: Might break existing assumptions
   - Recommendation: Make it opt-in initially, enable by default in v1.0

2. **Should we add dynamic filtering via API?**
   - Allow Umbrella API to update exclusion patterns via ping response
   - Would enable real-time adjustments without redeployment

3. **Should truncation limits be configurable?**
   - Allow overriding default limits via config
   - Trade-off: More complexity vs flexibility

4. **Should we expose metrics via JMX?**
   - Publish Umbrella metrics (API calls, latency, cache hits) via JMX
   - Enables monitoring integration

---

## References

- [DATADOME-ANALYSIS.md](DATADOME-ANALYSIS.md) - Detailed DataDome comparison
- [CLIENT-ARCHITECTURE.md](CLIENT-ARCHITECTURE.md) - Current Umbrella architecture
- DataDome Source: `/Users/matus/dev/datadome/DataDome-JavaModuleDome-*/src/`
