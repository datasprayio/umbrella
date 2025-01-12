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