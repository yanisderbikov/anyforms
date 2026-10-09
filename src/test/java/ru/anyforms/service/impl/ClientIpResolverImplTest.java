package ru.anyforms.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientIpResolverImplTest {

    private static final String RAILWAY_PROXIES = "10.0.0.0/8,100.64.0.0/10,172.16.0.0/12,192.168.0.0/16,fd00::/8,127.0.0.1/32,::1/128";

    private final ClientIpResolverImpl resolver = new ClientIpResolverImpl(RAILWAY_PROXIES);

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader(ClientIpResolverImpl.FORWARDED_FOR, forwardedFor);
        }
        return request;
    }

    @Test
    void withoutHeaderTheSocketAddressIsUsed() {
        assertEquals("203.0.113.7", resolver.resolve(request("203.0.113.7", null)));
        assertEquals("203.0.113.7", resolver.resolve(request("203.0.113.7", "  ")));
    }

    @Test
    void proxyAppendedAddressWinsOverClientSuppliedPrefix() {
        assertEquals("203.0.113.7", resolver.resolve(request("100.64.1.1", "1.2.3.4, 203.0.113.7")));
        assertEquals("203.0.113.7", resolver.resolve(request("100.64.1.1", "203.0.113.7")));
        assertEquals("203.0.113.7", resolver.resolve(request("100.64.1.1", "9.9.9.9, 203.0.113.7, 10.1.2.3")));
    }

    @Test
    void headerFromUntrustedPeerIsIgnored() {
        assertEquals("198.51.100.5", resolver.resolve(request("198.51.100.5", "1.2.3.4")));
    }

    @Test
    void garbageAndPortsAreTolerated() {
        assertEquals("203.0.113.7", resolver.resolve(request("10.0.0.2", "unknown, 203.0.113.7:51234, 192.168.0.1")));
        assertEquals("2001:db8::1", resolver.resolve(request("10.0.0.2", "[2001:db8::1]:443, fd12::1")));
        assertEquals("10.0.0.2", resolver.resolve(request("10.0.0.2", "unknown, 192.168.0.1")));
    }

    @Test
    void emptyTrustedListNeverHonoursTheHeader() {
        ClientIpResolverImpl strict = new ClientIpResolverImpl("");

        assertEquals("127.0.0.1", strict.resolve(request("127.0.0.1", "203.0.113.7")));
    }

    @Test
    void invalidConfigurationFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> new ClientIpResolverImpl("10.0.0.0/33"));
        assertThrows(IllegalArgumentException.class, () -> new ClientIpResolverImpl("proxy.example.com"));
    }
}
