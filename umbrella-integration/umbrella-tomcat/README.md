# Umbrella Tomcat Integration

## Features

- Auto-syncing configuration from the Umbrella API:
    - Mode of operation: Disabled, Block, Monitor
    - Configure maximum latency overhead of blocking before blocking is skipped.
- Modifies `HttpServletRequest`:
    - Adds custom attributes
- Modifies `HttpServletResponse`:
    - Overrides status code
    - Adds custom headers
    - Adds custom cookies

## Installation

Make sure to add the uber-jar to the `lib` directory of your Tomcat installation.

Then adjust your `web.xml` to include the filter:

```xml

<filter>
    <description>
        This filter intercepts HTTP requests .
    </description>
    <filter-name>UmbrellaFilter</filter-name>
    <filter-class>io.dataspray.umbrella.integration.tomcat.UmbrellaFilter</filter-class>

    <init-param>
        <description>
            The Organization name to use for
            authenticating with the Umbrella API.
        </description>
        <param-name>org</param-name>
        <param-value></param-value>
    </init-param>
    <init-param>
        <description>
            The API Key to use for authenticating
            with the Umbrella API.
        </description>
        <param-name>api-key</param-name>
        <!-- You can also pass in via env UMBRELLA_API_KEY or property umbrella.api.key -->
        <param-value></param-value>
    </init-param>
    <!--
    <init-param>
        <description>
            Explicitly enable or disable the filter.
            If unspecified, default is enabled.
        </description>
        <param-name>enabled</param-name>
        <param-value>true</param-value>
    </init-param>
    <init-param>
        <description>
            Override the endpoint URL to connect
            to a self-hosted instance.
        </description>
        <param-name>endpoint-url</param-name>
        <param-value>https://api.umbrella.dataspray.io</param-value>
    </init-param>
    -->
</filter>
<filter-mapping>
<filter-name>UmbrellaFilter</filter-name>
<url-pattern>/*</url-pattern>
</filter-mapping>
```

## Reducing API calls

Static assets and trusted internal traffic do not need bot protection. All three
options below are optional; without them every request is checked.

```xml
<init-param>
    <description>Paths matching this pattern skip Umbrella entirely.</description>
    <param-name>exclusion-regex</param-name>
    <param-value>(?i)\.(css|js|png|jpg|gif|svg|ico|woff|woff2|ttf|mp4|webm)$</param-value>
</init-param>
<init-param>
    <description>When set, only paths matching this pattern are checked.</description>
    <param-name>inclusion-regex</param-name>
    <param-value>^/api/</param-value>
</init-param>
<init-param>
    <description>Requests from these addresses or CIDR blocks skip Umbrella.</description>
    <param-name>skip-ips</param-name>
    <param-value>10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.1,::1</param-value>
</init-param>
```

The exclusion pattern is checked first and wins over the inclusion pattern. Both
IPv4 and IPv6 addresses and CIDR blocks are supported. A malformed pattern or
address fails at startup rather than silently disabling protection.

## Configuration sources

Every setting can come from, in order of precedence:

1. A filter `init-param`, as above
2. A system property: `umbrella.org`, `umbrella.api.key`, `umbrella.enabled`,
   `umbrella.endpoint.url`, `umbrella.inclusion.regex`, `umbrella.exclusion.regex`,
   `umbrella.skip.ips`
3. An environment variable: `UMBRELLA_ORG`, `UMBRELLA_API_KEY`, `UMBRELLA_ENABLED`,
   `UMBRELLA_ENDPOINT_URL`, `UMBRELLA_INCLUSION_REGEX`, `UMBRELLA_EXCLUSION_REGEX`,
   `UMBRELLA_SKIP_IPS`

## Behaviour

Umbrella fails open. A timeout, an API error, an unreachable endpoint or an
unrecognised response all allow the request through — only an explicit block
decision stops one, answering with the status the server names or `403` if it
names none.

Every checked request gets an `umbrella.spent_time_ms` attribute recording the
milliseconds spent in Umbrella:

```java
Long spent = (Long) request.getAttribute("umbrella.spent_time_ms");
```

Collected header values are truncated to bounded sizes so that an oversized
request cannot produce an oversized API payload.

Requires Servlet 6 (Tomcat 10.1 or newer). For `javax.servlet` applications use
`umbrella-tomcat-javax` instead.
