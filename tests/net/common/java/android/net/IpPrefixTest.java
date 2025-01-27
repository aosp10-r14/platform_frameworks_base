/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net;

import static com.android.testutils.MiscAsserts.assertEqualBothWays;
import static com.android.testutils.MiscAsserts.assertFieldCountEquals;
import static com.android.testutils.MiscAsserts.assertNotEqualEitherWay;
import static com.android.testutils.ParcelUtils.assertParcelingIsLossless;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.util.Random;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class IpPrefixTest {

    private static InetAddress address(String addr) {
        return InetAddress.parseNumericAddress(addr);
    }

    // Explicitly cast everything to byte because "error: possible loss of precision".
    private static final byte[] IPV4_BYTES = { (byte) 192, (byte) 0, (byte) 2, (byte) 4};
    private static final byte[] IPV6_BYTES = {
        (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
        (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef,
        (byte) 0x0f, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xa0
    };

    @Test
    public void testConstructor() {
        IpPrefix p;
        try {
            p = new IpPrefix((byte[]) null, 9);
            fail("Expected NullPointerException: null byte array");
        } catch (RuntimeException expected) { }

        try {
            p = new IpPrefix((InetAddress) null, 10);
            fail("Expected NullPointerException: null InetAddress");
        } catch (RuntimeException expected) { }

        try {
            p = new IpPrefix((String) null);
            fail("Expected NullPointerException: null String");
        } catch (RuntimeException expected) { }


        try {
            byte[] b2 = {1, 2, 3, 4, 5};
            p = new IpPrefix(b2, 29);
            fail("Expected IllegalArgumentException: invalid array length");
        } catch (IllegalArgumentException expected) { }

        try {
            p = new IpPrefix("1.2.3.4");
            fail("Expected IllegalArgumentException: no prefix length");
        } catch (IllegalArgumentException expected) { }

        try {
            p = new IpPrefix("1.2.3.4/");
            fail("Expected IllegalArgumentException: empty prefix length");
        } catch (IllegalArgumentException expected) { }

        try {
            p = new IpPrefix("foo/32");
            fail("Expected IllegalArgumentException: invalid address");
        } catch (IllegalArgumentException expected) { }

        try {
            p = new IpPrefix("1/32");
            fail("Expected IllegalArgumentException: deprecated IPv4 format");
        } catch (IllegalArgumentException expected) { }

        try {
            p = new IpPrefix("1.2.3.256/32");
            fail("Expected IllegalArgumentException: invalid IPv4 address");
        } catch (IllegalArgumentException expected) { }

        try {
            p = new IpPrefix("foo/32");
            fail("Expected IllegalArgumentException: non-address");
        } catch (IllegalArgumentException expected) { }

        try {
            p = new IpPrefix("f00:::/32");
            fail("Expected IllegalArgumentException: invalid IPv6 address");
        } catch (IllegalArgumentException expected) { }
    }

    @Test
    public void testTruncation() {
        IpPrefix p;

        p = new IpPrefix(IPV4_BYTES, 32);
        assertEquals("192.0.2.4/32", p.toString());

        p = new IpPrefix(IPV4_BYTES, 29);
        assertEquals("192.0.2.0/29", p.toString());

        p = new IpPrefix(IPV4_BYTES, 8);
        assertEquals("192.0.0.0/8", p.toString());

        p = new IpPrefix(IPV4_BYTES, 0);
        assertEquals("0.0.0.0/0", p.toString());

        try {
            p = new IpPrefix(IPV4_BYTES, 33);
            fail("Expected IllegalArgumentException: invalid prefix length");
        } catch (RuntimeException expected) { }

        try {
            p = new IpPrefix(IPV4_BYTES, 128);
            fail("Expected IllegalArgumentException: invalid prefix length");
        } catch (RuntimeException expected) { }

        try {
            p = new IpPrefix(IPV4_BYTES, -1);
            fail("Expected IllegalArgumentException: negative prefix length");
        } catch (RuntimeException expected) { }

        p = new IpPrefix(IPV6_BYTES, 128);
        assertEquals("2001:db8:dead:beef:f00::a0/128", p.toString());

        p = new IpPrefix(IPV6_BYTES, 122);
        assertEquals("2001:db8:dead:beef:f00::80/122", p.toString());

        p = new IpPrefix(IPV6_BYTES, 64);
        assertEquals("2001:db8:dead:beef::/64", p.toString());

        p = new IpPrefix(IPV6_BYTES, 3);
        assertEquals("2000::/3", p.toString());

        p = new IpPrefix(IPV6_BYTES, 0);
        assertEquals("::/0", p.toString());

        try {
            p = new IpPrefix(IPV6_BYTES, -1);
            fail("Expected IllegalArgumentException: negative prefix length");
        } catch (RuntimeException expected) { }

        try {
            p = new IpPrefix(IPV6_BYTES, 129);
            fail("Expected IllegalArgumentException: negative prefix length");
        } catch (RuntimeException expected) { }

    }

    @Test
    public void testEquals() {
        IpPrefix p1, p2;

        p1 = new IpPrefix("192.0.2.251/23");
        p2 = new IpPrefix(new byte[]{(byte) 192, (byte) 0, (byte) 2, (byte) 251}, 23);
        assertEqualBothWays(p1, p2);

        p1 = new IpPrefix("192.0.2.5/23");
        assertEqualBothWays(p1, p2);

        p1 = new IpPrefix("192.0.2.5/24");
        assertNotEqualEitherWay(p1, p2);

        p1 = new IpPrefix("192.0.4.5/23");
        assertNotEqualEitherWay(p1, p2);


        p1 = new IpPrefix("2001:db8:dead:beef:f00::80/122");
        p2 = new IpPrefix(IPV6_BYTES, 122);
        assertEquals("2001:db8:dead:beef:f00::80/122", p2.toString());
        assertEqualBothWays(p1, p2);

        p1 = new IpPrefix("2001:db8:dead:beef:f00::bf/122");
        assertEqualBothWays(p1, p2);

        p1 = new IpPrefix("2001:db8:dead:beef:f00::8:0/123");
        assertNotEqualEitherWay(p1, p2);

        p1 = new IpPrefix("2001:db8:dead:beef::/122");
        assertNotEqualEitherWay(p1, p2);

        // 192.0.2.4/32 != c000:0204::/32.
        byte[] ipv6bytes = new byte[16];
        System.arraycopy(IPV4_BYTES, 0, ipv6bytes, 0, IPV4_BYTES.length);
        p1 = new IpPrefix(ipv6bytes, 32);
        assertEqualBothWays(p1, new IpPrefix("c000:0204::/32"));

        p2 = new IpPrefix(IPV4_BYTES, 32);
        assertNotEqualEitherWay(p1, p2);
    }

    @Test
    public void testContainsInetAddress() {
        IpPrefix p = new IpPrefix("2001:db8:f00::ace:d00d/127");
        assertTrue(p.contains(address("2001:db8:f00::ace:d00c")));
        assertTrue(p.contains(address("2001:db8:f00::ace:d00d")));
        assertFalse(p.contains(address("2001:db8:f00::ace:d00e")));
        assertFalse(p.contains(address("2001:db8:f00::bad:d00d")));
        assertFalse(p.contains(address("2001:4868:4860::8888")));
        assertFalse(p.contains(address("8.8.8.8")));

        p = new IpPrefix("192.0.2.0/23");
        assertTrue(p.contains(address("192.0.2.43")));
        assertTrue(p.contains(address("192.0.3.21")));
        assertFalse(p.contains(address("192.0.0.21")));
        assertFalse(p.contains(address("8.8.8.8")));
        assertFalse(p.contains(address("2001:4868:4860::8888")));

        IpPrefix ipv6Default = new IpPrefix("::/0");
        assertTrue(ipv6Default.contains(address("2001:db8::f00")));
        assertFalse(ipv6Default.contains(address("192.0.2.1")));

        IpPrefix ipv4Default = new IpPrefix("0.0.0.0/0");
        assertTrue(ipv4Default.contains(address("255.255.255.255")));
        assertTrue(ipv4Default.contains(address("192.0.2.1")));
        assertFalse(ipv4Default.contains(address("2001:db8::f00")));
    }

    @Test
    public void testContainsIpPrefix() {
        assertTrue(new IpPrefix("0.0.0.0/0").containsPrefix(new IpPrefix("0.0.0.0/0")));
        assertTrue(new IpPrefix("0.0.0.0/0").containsPrefix(new IpPrefix("1.2.3.4/0")));
        assertTrue(new IpPrefix("0.0.0.0/0").containsPrefix(new IpPrefix("1.2.3.4/8")));
        assertTrue(new IpPrefix("0.0.0.0/0").containsPrefix(new IpPrefix("1.2.3.4/24")));
        assertTrue(new IpPrefix("0.0.0.0/0").containsPrefix(new IpPrefix("1.2.3.4/23")));

        assertTrue(new IpPrefix("1.2.3.4/8").containsPrefix(new IpPrefix("1.2.3.4/8")));
        assertTrue(new IpPrefix("1.2.3.4/8").containsPrefix(new IpPrefix("1.254.12.9/8")));
        assertTrue(new IpPrefix("1.2.3.4/21").containsPrefix(new IpPrefix("1.2.3.4/21")));
        assertTrue(new IpPrefix("1.2.3.4/32").containsPrefix(new IpPrefix("1.2.3.4/32")));

        assertTrue(new IpPrefix("1.2.3.4/20").containsPrefix(new IpPrefix("1.2.3.0/24")));

        assertFalse(new IpPrefix("1.2.3.4/32").containsPrefix(new IpPrefix("1.2.3.5/32")));
        assertFalse(new IpPrefix("1.2.3.4/8").containsPrefix(new IpPrefix("2.2.3.4/8")));
        assertFalse(new IpPrefix("0.0.0.0/16").containsPrefix(new IpPrefix("0.0.0.0/15")));
        assertFalse(new IpPrefix("100.0.0.0/8").containsPrefix(new IpPrefix("99.0.0.0/8")));

        assertTrue(new IpPrefix("::/0").containsPrefix(new IpPrefix("::/0")));
        assertTrue(new IpPrefix("::/0").containsPrefix(new IpPrefix("2001:db8::f00/1")));
        assertTrue(new IpPrefix("::/0").containsPrefix(new IpPrefix("3d8a:661:a0::770/8")));
        assertTrue(new IpPrefix("::/0").containsPrefix(new IpPrefix("2001:db8::f00/8")));
        assertTrue(new IpPrefix("::/0").containsPrefix(new IpPrefix("2001:db8::f00/64")));
        assertTrue(new IpPrefix("::/0").containsPrefix(new IpPrefix("2001:db8::f00/113")));
        assertTrue(new IpPrefix("::/0").containsPrefix(new IpPrefix("2001:db8::f00/128")));

        assertTrue(new IpPrefix("2001:db8:f00::ace:d00d/64").containsPrefix(
                new IpPrefix("2001:db8:f00::ace:d00d/64")));
        assertTrue(new IpPrefix("2001:db8:f00::ace:d00d/64").containsPrefix(
                new IpPrefix("2001:db8:f00::ace:d00d/120")));
        assertFalse(new IpPrefix("2001:db8:f00::ace:d00d/64").containsPrefix(
                new IpPrefix("2001:db8:f00::ace:d00d/32")));
        assertFalse(new IpPrefix("2001:db8:f00::ace:d00d/64").containsPrefix(
                new IpPrefix("2006:db8:f00::ace:d00d/96")));

        assertTrue(new IpPrefix("2001:db8:f00::ace:d00d/128").containsPrefix(
                new IpPrefix("2001:db8:f00::ace:d00d/128")));
        assertTrue(new IpPrefix("2001:db8:f00::ace:d00d/100").containsPrefix(
                new IpPrefix("2001:db8:f00::ace:ccaf/110")));

        assertFalse(new IpPrefix("2001:db8:f00::ace:d00d/128").containsPrefix(
                new IpPrefix("2001:db8:f00::ace:d00e/128")));
        assertFalse(new IpPrefix("::/30").containsPrefix(new IpPrefix("::/29")));
    }

    @Test
    public void testHashCode() {
        IpPrefix p = new IpPrefix(new byte[4], 0);
        Random random = new Random();
        for (int i = 0; i < 100; i++) {
            final IpPrefix oldP = p;
            if (random.nextBoolean()) {
                // IPv4.
                byte[] b = new byte[4];
                random.nextBytes(b);
                p = new IpPrefix(b, random.nextInt(33));
            } else {
                // IPv6.
                byte[] b = new byte[16];
                random.nextBytes(b);
                p = new IpPrefix(b, random.nextInt(129));
            }
            if (p.equals(oldP)) {
                assertEquals(p.hashCode(), oldP.hashCode());
            }
            if (p.hashCode() != oldP.hashCode()) {
                assertNotEquals(p, oldP);
            }
        }
    }

    @Test
    public void testHashCodeIsNotConstant() {
        IpPrefix[] prefixes = {
            new IpPrefix("2001:db8:f00::ace:d00d/127"),
            new IpPrefix("192.0.2.0/23"),
            new IpPrefix("::/0"),
            new IpPrefix("0.0.0.0/0"),
        };
        for (int i = 0; i < prefixes.length; i++) {
            for (int j = i + 1; j < prefixes.length; j++) {
                assertNotEquals(prefixes[i].hashCode(), prefixes[j].hashCode());
            }
        }
    }

    @Test
    public void testMappedAddressesAreBroken() {
        // 192.0.2.0/24 != ::ffff:c000:0204/120, but because we use InetAddress,
        // we are unable to comprehend that.
        byte[] ipv6bytes = {
            (byte) 0, (byte) 0, (byte) 0, (byte) 0,
            (byte) 0, (byte) 0, (byte) 0, (byte) 0,
            (byte) 0, (byte) 0, (byte) 0xff, (byte) 0xff,
            (byte) 192, (byte) 0, (byte) 2, (byte) 0};
        IpPrefix p = new IpPrefix(ipv6bytes, 120);
        assertEquals(16, p.getRawAddress().length);       // Fine.
        assertArrayEquals(ipv6bytes, p.getRawAddress());  // Fine.

        // Broken.
        assertEquals("192.0.2.0/120", p.toString());
        assertEquals(InetAddress.parseNumericAddress("192.0.2.0"), p.getAddress());
    }

    @Test
    public void testParceling() {
        IpPrefix p;

        p = new IpPrefix("2001:4860:db8::/64");
        assertParcelingIsLossless(p);
        assertTrue(p.isIPv6());

        p = new IpPrefix("192.0.2.0/25");
        assertParcelingIsLossless(p);
        assertTrue(p.isIPv4());

        assertFieldCountEquals(2, IpPrefix.class);
    }
}
