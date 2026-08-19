package com.domus.api.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClienteIpResolverTest {

    @Test
    void usaCfConnectingIpQuandoConfiaEmForwarded() {
        ClienteIpResolver resolver = new ClienteIpResolver(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", "203.0.113.9");
        request.setRemoteAddr("10.0.0.1");

        assertThat(resolver.resolver(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void usaUltimoElementoDoXForwardedForQuandoSemCfConnectingIp() {
        ClienteIpResolver resolver = new ClienteIpResolver(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1, 203.0.113.2");
        request.setRemoteAddr("10.0.0.1");

        assertThat(resolver.resolver(request)).isEqualTo("203.0.113.2");
    }

    @Test
    void usaRemoteAddrQuandoNaoConfiaEmForwarded() {
        ClienteIpResolver resolver = new ClienteIpResolver(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1");
        request.setRemoteAddr("10.0.0.1");

        assertThat(resolver.resolver(request)).isEqualTo("10.0.0.1");
    }
}
