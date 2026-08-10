/*
 * Copyright 2025 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.umbrella.integration.tomcat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RequestFilteringTest {

    @Test
    void testExclusionSkipsStaticAssets() {
        RequestFilterConfig config = new RequestFilterConfig(
                Optional.empty(),
                Optional.of("(?i)\\.(css|js|png|woff2)$"),
                Optional.empty());

        assertFalse(config.shouldCheck("/static/app.css", "203.0.113.1"));
        assertFalse(config.shouldCheck("/static/APP.PNG", "203.0.113.1"));
        assertTrue(config.shouldCheck("/api/login", "203.0.113.1"));
    }

    @Test
    void testInclusionLimitsToMatchingPaths() {
        RequestFilterConfig config = new RequestFilterConfig(
                Optional.of("^/api/"),
                Optional.empty(),
                Optional.empty());

        assertTrue(config.shouldCheck("/api/login", "203.0.113.1"));
        assertFalse(config.shouldCheck("/health", "203.0.113.1"));
    }

    @Test
    void testExclusionWinsOverInclusion() {
        RequestFilterConfig config = new RequestFilterConfig(
                Optional.of("^/api/"),
                Optional.of("\\.js$"),
                Optional.empty());

        assertFalse(config.shouldCheck("/api/widget.js", "203.0.113.1"));
    }

    @Test
    void testUnconfiguredChecksEverything() {
        RequestFilterConfig config = RequestFilterConfig.none();

        assertTrue(config.shouldCheck("/anything.css", "10.0.0.1"));
        assertTrue(config.shouldCheck(null, null));
    }

    @Test
    void testSkipIps() {
        RequestFilterConfig config = new RequestFilterConfig(
                Optional.empty(),
                Optional.empty(),
                Optional.of("10.0.0.0/8, 192.168.1.5, ::1"));

        assertFalse(config.shouldCheck("/api", "10.4.3.2"));
        assertFalse(config.shouldCheck("/api", "192.168.1.5"));
        assertFalse(config.shouldCheck("/api", "0:0:0:0:0:0:0:1"));
        assertTrue(config.shouldCheck("/api", "192.168.1.6"));
        assertTrue(config.shouldCheck("/api", "203.0.113.1"));
    }

    @Test
    void testInvalidConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RequestFilterConfig(
                Optional.of("([unclosed"), Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new RequestFilterConfig(
                Optional.empty(), Optional.empty(), Optional.of("not-an-ip")));
    }

    @Test
    void testIpv4CidrBoundaries() {
        IpAddressMatcher matcher = new IpAddressMatcher("192.168.1.0/24");

        assertTrue(matcher.matches("192.168.1.0"));
        assertTrue(matcher.matches("192.168.1.255"));
        assertFalse(matcher.matches("192.168.2.0"));
        assertFalse(matcher.matches("192.168.0.255"));
    }

    @Test
    void testNonByteAlignedPrefix() {
        IpAddressMatcher matcher = new IpAddressMatcher("10.0.0.0/12");

        assertTrue(matcher.matches("10.0.0.1"));
        assertTrue(matcher.matches("10.15.255.255"));
        assertFalse(matcher.matches("10.16.0.0"));
    }

    @Test
    void testSingleAddressAndMixedFamilies() {
        IpAddressMatcher exact = new IpAddressMatcher("127.0.0.1");
        assertTrue(exact.matches("127.0.0.1"));
        assertFalse(exact.matches("127.0.0.2"));

        IpAddressMatcher v6 = new IpAddressMatcher("2001:db8::/32");
        assertTrue(v6.matches("2001:db8::1"));
        assertFalse(v6.matches("2001:db9::1"));
        // An IPv4 address never falls inside an IPv6 block
        assertFalse(v6.matches("10.0.0.1"));
        assertFalse(exact.matches("::1"));

        assertFalse(exact.matches(null));
        assertFalse(exact.matches("garbage"));
    }

    @Test
    void testTruncateRespectsMultibyteBoundaries() {
        // Each emoji is 4 UTF-8 bytes
        String value = "😀😀😀";
        assertEquals("😀😀", StringTruncator.truncate(value, 8));
        assertEquals("😀😀", StringTruncator.truncate(value, 11));
        assertEquals(value, StringTruncator.truncate(value, 12));
        assertEquals("", StringTruncator.truncate(value, 2));
        assertNull(StringTruncator.truncate(null, 10));
    }

    @Test
    void testTruncateKeepingEndPreservesClosestEntries() {
        String forwarded = "1.1.1.1, 2.2.2.2, 3.3.3.3";
        String truncated = StringTruncator.truncateKeepingEnd(forwarded, 9);

        assertTrue(forwarded.endsWith(truncated), truncated);
        assertTrue(truncated.getBytes(StandardCharsets.UTF_8).length <= 9);
        assertEquals(forwarded, StringTruncator.truncateKeepingEnd(forwarded, 1000));
    }

    @Test
    void testCollectedValuesAreCapped() {
        StringBuilder longValue = new StringBuilder();
        for (int i = 0; i < 5_000; i++) {
            longValue.append('a');
        }
        String value = longValue.toString();

        assertEquals(UmbrellaHttpExchange.MAX_USER_AGENT_BYTES,
                StringTruncator.truncate(value, UmbrellaHttpExchange.MAX_USER_AGENT_BYTES).length());
        assertEquals(UmbrellaHttpExchange.MAX_URI_BYTES,
                StringTruncator.truncate(value, UmbrellaHttpExchange.MAX_URI_BYTES).length());
    }
}
