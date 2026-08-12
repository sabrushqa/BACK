package com.example.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Verifie le filtre de defense-en-profondeur pour les appels internes
 * chatbot -> backoffice: il ne s'applique qu'aux routes /api/chatbot/**,
 * bloque toute requete sans jeton ou avec un jeton invalide avant meme
 * d'atteindre le controller, et laisse passer un jeton valide.
 */
class InternalTokenFilterTest {

    private InternalTokenFilter buildFilter() {
        InternalTokenFilter filter = new InternalTokenFilter();
        ReflectionTestUtils.setField(filter, "internalToken", "expected-internal-token");
        return filter;
    }

    @Test
    void doesNotFilterRoutesOutsideChatbotNamespace() throws Exception {
        InternalTokenFilter filter = buildFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/staff/affiliations");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void filtersChatbotRoutes() throws Exception {
        InternalTokenFilter filter = buildFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chatbot/reclamations");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        InternalTokenFilter filter = buildFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chatbot/reclamations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsRequestWithBlankToken() throws Exception {
        InternalTokenFilter filter = buildFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chatbot/reclamations");
        request.addHeader("X-Internal-Token", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsRequestWithInvalidToken() throws Exception {
        InternalTokenFilter filter = buildFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chatbot/reclamations");
        request.addHeader("X-Internal-Token", "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void allowsRequestWithValidToken() throws Exception {
        InternalTokenFilter filter = buildFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chatbot/reclamations");
        request.addHeader("X-Internal-Token", "expected-internal-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }
}
