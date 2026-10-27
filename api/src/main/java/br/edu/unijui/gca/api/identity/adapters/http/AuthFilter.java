package br.edu.unijui.gca.api.identity.adapters.http;

import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.exceptions.TokenNotFoundException;
import br.edu.unijui.gca.api.identity.application.AuthenticateRequest;
import br.edu.unijui.gca.api.token.ApiKeyToken;
import br.edu.unijui.gca.api.token.BearerToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Objects;


@RequiredArgsConstructor
@Profile({"intake", "metadata-management"})
@Component
public class AuthFilter extends OncePerRequestFilter {

    private final AuthenticateRequest authentication;

    private final HandlerExceptionResolver handlerExceptionResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        var isImage = path.startsWith("/images/");
        var isAuth = path.contains("auth");
        var isFavicon = path.contains("favicon");
        var isActuator = path.contains("actuator");

        return isAuth || isImage || isFavicon || isActuator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    )  {
        try {
            String apiKey = request.getHeader("X-API-Key");

            if (Objects.nonNull(apiKey)) {
                authenticateApiKey(request, ApiKeyToken.from(apiKey));
            }

            String authorization = request.getHeader("Authorization");

            if (Objects.nonNull(authorization)) {
                BearerToken bearerToken = BearerToken.from(authorization);
                authenticateBearer(request, bearerToken);
            }

            if (Objects.isNull(apiKey) && Objects.isNull(authorization)) {
                throw new TokenNotFoundException();
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            handlerExceptionResolver.resolveException(request, response, null, e);
        }
    }

    private void authenticateBearer(
            HttpServletRequest request,
            BearerToken bearerToken
    ) {
        setAuthentication(request, authentication.authenticateBearer(bearerToken));
    }

    private void authenticateApiKey(HttpServletRequest request, ApiKeyToken token) {
        setAuthentication(request, authentication.authenticateApiKey(token));
    }

    private void setAuthentication(HttpServletRequest request, User user) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, null);

        auth.setDetails(
                new WebAuthenticationDetailsSource()
                        .buildDetails(request)
        );

        SecurityContextHolder.getContext()
                .setAuthentication(auth);
    }
}
