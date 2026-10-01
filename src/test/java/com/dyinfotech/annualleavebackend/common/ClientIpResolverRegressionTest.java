package com.dyinfotech.annualleavebackend.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverRegressionTest {

    @Test
    void directUntrustedPeer_ignoresSpoofedForwardingHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("X-Real-IP", "5.6.7.8");

        assertEquals("203.0.113.10", ClientIpResolver.resolve(request));
    }

    @Test
    void trustedLoopbackProxy_usesRightmostForwardedHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(
                "X-Forwarded-For",
                "198.51.100.15, 203.0.113.20");

        assertEquals("203.0.113.20", ClientIpResolver.resolve(request));
    }

    @Test
    void trustedLoopbackProxy_fallsBackToRealIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::1");
        request.addHeader("X-Real-IP", "198.51.100.25");

        assertEquals("198.51.100.25", ClientIpResolver.resolve(request));
    }
}
