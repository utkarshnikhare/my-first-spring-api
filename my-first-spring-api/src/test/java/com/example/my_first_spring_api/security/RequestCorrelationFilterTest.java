package com.example.my_first_spring_api.security;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void acceptsUuidRequestIdAndClearsItAfterTheRequest() throws Exception {
        String requestId = "658d3e2f-aac1-4e51-92c7-ea8f13041f99";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", requestId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(MDC.get("requestId")).isEqualTo(requestId));

        assertThat(response.getHeader("X-Request-ID")).isEqualTo(requestId);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void replacesMalformedRequestIdWithGeneratedUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "password=secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(MDC.get("requestId")).isNotEqualTo("password=secret"));

        assertThat(UUID.fromString(response.getHeader("X-Request-ID"))).isNotNull();
        assertThat(MDC.get("requestId")).isNull();
    }
}
