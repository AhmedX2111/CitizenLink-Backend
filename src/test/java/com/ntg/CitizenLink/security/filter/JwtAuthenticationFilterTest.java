package com.ntg.CitizenLink.security.filter;

import com.ntg.CitizenLink.security.JwtBlocklist;
import com.ntg.CitizenLink.service.interfaces.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthenticationFilter} — exercises the filter
 * against a mocked JwtService/JwtBlocklist/UserDetailsService using Spring's
 * MockHttpServletRequest/Response.
 *
 * Covers: missing/non-Bearer headers (pass-through), valid token (authenticates),
 * invalid/expired token (skips auth), blocklisted JTI (skips auth), and
 * unknown user (401 and filter chain stops).
 *
 * NOT covered: real signature validation (JwtServiceImplTest) or the full
 * security chain (SecurityConfig integration).
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "valid.jwt.token";
    private static final String USERNAME = "handler01";
    private static final String JTI = "jti-1";

    @Mock private JwtService jwtService;
    @Mock private JwtBlocklist jwtBlocklist;
    @Mock private UserDetailsService userDetailsService;
    @Mock private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, jwtBlocklist, userDetailsService);
        SecurityContextHolder.clearContext();
        userDetails = new User(USERNAME, "password",
                List.of(new SimpleGrantedAuthority("ROLE_HANDLER")));
    }

    @Nested
    class WithoutToken {

        @Test
        void shouldPassThrough_whenNoAuthHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void shouldPassThrough_whenHeaderNotBearer() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Basic abc123");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    class WithValidToken {

        @Test
        void shouldSetAuthentication_whenTokenValid() throws Exception {
            when(jwtService.extractUsername(TOKEN)).thenReturn(USERNAME);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails);
            when(jwtService.isTokenValid(TOKEN, userDetails)).thenReturn(true);
            when(jwtService.extractJti(TOKEN)).thenReturn(JTI);
            when(jwtBlocklist.isBlocked(JTI)).thenReturn(false);

            MockHttpServletRequest request = requestWithBearer();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            org.springframework.security.core.Authentication auth =
                    SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isEqualTo(userDetails);
            assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_HANDLER");
        }

        @Test
        void shouldNotAuthenticate_whenAccountDisabled() throws Exception {
            UserDetails disabledUser = new User(USERNAME, "password", true, true, true, false,
                    List.of(new SimpleGrantedAuthority("ROLE_HANDLER")));
            when(jwtService.extractUsername(TOKEN)).thenReturn(USERNAME);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(disabledUser);
            when(jwtService.isTokenValid(TOKEN, disabledUser)).thenReturn(true);
            when(jwtService.extractJti(TOKEN)).thenReturn(JTI);
            when(jwtBlocklist.isBlocked(JTI)).thenReturn(false);

            MockHttpServletRequest request = requestWithBearer();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    class WithInvalidToken {

        @Test
        void shouldNotAuthenticate_whenTokenInvalid() throws Exception {
            when(jwtService.extractUsername(TOKEN)).thenReturn(USERNAME);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails);
            when(jwtService.isTokenValid(TOKEN, userDetails)).thenReturn(false);

            MockHttpServletRequest request = requestWithBearer();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void shouldNotAuthenticate_whenJtiBlocklisted() throws Exception {
            when(jwtService.extractUsername(TOKEN)).thenReturn(USERNAME);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails);
            when(jwtService.isTokenValid(TOKEN, userDetails)).thenReturn(true);
            when(jwtService.extractJti(TOKEN)).thenReturn(JTI);
            when(jwtBlocklist.isBlocked(JTI)).thenReturn(true);

            MockHttpServletRequest request = requestWithBearer();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void shouldNotAuthenticate_whenUsernameCannotBeParsed() throws Exception {
            when(jwtService.extractUsername(TOKEN)).thenThrow(new RuntimeException("bad token"));

            MockHttpServletRequest request = requestWithBearer();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void shouldReturn401AndStopChain_whenUserNotFound() throws Exception {
            when(jwtService.extractUsername(TOKEN)).thenReturn(USERNAME);
            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenThrow(new UsernameNotFoundException("missing"));

            MockHttpServletRequest request = requestWithBearer();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            // filter chain must NOT be invoked — the 401 response is final
            org.mockito.Mockito.verifyNoInteractions(filterChain);
        }
    }

    private MockHttpServletRequest requestWithBearer() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }
}
