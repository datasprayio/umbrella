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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Decides which requests are worth sending to Umbrella. Static assets and trusted internal traffic do not need bot
 * protection, and skipping them removes most of the API calls a typical application would otherwise make.
 *
 * <p>Everything here is opt-in: an unconfigured instance checks every request.
 */
public final class RequestFilterConfig {

    private final Pattern inclusionPattern;
    private final Pattern exclusionPattern;
    private final List<IpAddressMatcher> skipIpMatchers;

    public static RequestFilterConfig none() {
        return new RequestFilterConfig(Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * @param inclusionRegex when present, only matching paths are checked
     * @param exclusionRegex when present, matching paths are skipped; takes precedence over the inclusion pattern
     * @param skipIps        comma separated addresses or CIDR blocks whose requests are skipped
     * @throws IllegalArgumentException if a pattern or address cannot be parsed
     */
    public RequestFilterConfig(
            Optional<String> inclusionRegex,
            Optional<String> exclusionRegex,
            Optional<String> skipIps) {
        this.inclusionPattern = inclusionRegex.map(RequestFilterConfig::compile).orElse(null);
        this.exclusionPattern = exclusionRegex.map(RequestFilterConfig::compile).orElse(null);

        List<IpAddressMatcher> matchers = new ArrayList<>();
        skipIps.ifPresent(value -> {
            for (String entry : value.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    matchers.add(new IpAddressMatcher(trimmed));
                }
            }
        });
        this.skipIpMatchers = Collections.unmodifiableList(matchers);
    }

    public boolean shouldCheck(String path, String remoteAddress) {
        if (path != null) {
            if (exclusionPattern != null && exclusionPattern.matcher(path).find()) {
                return false;
            }
            if (inclusionPattern != null && !inclusionPattern.matcher(path).find()) {
                return false;
            }
        }
        for (IpAddressMatcher matcher : skipIpMatchers) {
            if (matcher.matches(remoteAddress)) {
                return false;
            }
        }
        return true;
    }

    private static Pattern compile(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException ex) {
            throw new IllegalArgumentException("Not a valid regular expression: " + regex, ex);
        }
    }
}
