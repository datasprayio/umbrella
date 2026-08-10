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

import java.nio.charset.StandardCharsets;

/**
 * Caps the size of collected header values, so that an oversized or malicious request cannot produce an oversized
 * API payload.
 */
public final class StringTruncator {

    /**
     * Truncates to at most {@code maxBytes} UTF-8 bytes, never splitting a multi-byte character.
     */
    public static String truncate(String value, int maxBytes) {
        if (value == null) {
            return null;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return value;
        }
        int end = maxBytes;
        // Back off the start of a truncated multi-byte sequence
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }

    /**
     * Truncates by dropping leading content, keeping the tail. Used for X-Forwarded-For, where the entries closest to
     * this server are the trustworthy ones.
     */
    public static String truncateKeepingEnd(String value, int maxBytes) {
        if (value == null) {
            return null;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return value;
        }
        int start = bytes.length - maxBytes;
        // Move forward to the start of a whole character
        while (start < bytes.length && (bytes[start] & 0xC0) == 0x80) {
            start++;
        }
        return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
    }

    private StringTruncator() {
        // Disable ctor
    }
}
