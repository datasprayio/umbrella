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

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Matches an IP address against a single address or a CIDR block, for both IPv4 and IPv6.
 */
public final class IpAddressMatcher {

    private final int maskBits;
    private final InetAddress requiredAddress;

    /**
     * @param cidr an address with an optional prefix length, such as {@code 10.0.0.0/8}, {@code 127.0.0.1} or
     *             {@code 2001:db8::/32}
     * @throws IllegalArgumentException if the address or prefix length cannot be parsed
     */
    public IpAddressMatcher(String cidr) {
        String address = cidr;
        int bits = -1;
        int slash = cidr.indexOf('/');
        if (slash > 0) {
            address = cidr.substring(0, slash);
            try {
                bits = Integer.parseInt(cidr.substring(slash + 1));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Not a valid prefix length: " + cidr, ex);
            }
        }
        this.requiredAddress = parse(address);
        int addressBits = requiredAddress.getAddress().length * 8;
        if (bits < 0) {
            bits = addressBits;
        }
        if (bits > addressBits) {
            throw new IllegalArgumentException("Prefix length " + bits + " exceeds address size in: " + cidr);
        }
        this.maskBits = bits;
    }

    public boolean matches(String address) {
        if (address == null) {
            return false;
        }
        InetAddress candidate;
        try {
            candidate = parse(address);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        // An IPv4 address never falls inside an IPv6 block, and the reverse
        if (!requiredAddress.getClass().equals(candidate.getClass())) {
            return false;
        }

        byte[] required = requiredAddress.getAddress();
        byte[] remaining = candidate.getAddress();
        int wholeBytes = maskBits / 8;
        int remainingBits = maskBits % 8;

        for (int i = 0; i < wholeBytes; i++) {
            if (required[i] != remaining[i]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = (0xFF << (8 - remainingBits)) & 0xFF;
        return (required[wholeBytes] & mask) == (remaining[wholeBytes] & mask);
    }

    private static InetAddress parse(String address) {
        // Reject hostnames: only literal addresses may configure or match a rule
        if (address.isEmpty() || (address.indexOf(':') < 0 && !address.matches("[0-9.]+"))) {
            throw new IllegalArgumentException("Not a literal IP address: " + address);
        }
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Not a valid IP address: " + address, ex);
        }
    }
}
