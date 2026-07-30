package br.edu.unijui.gca.api.filters;

import br.edu.unijui.gca.api.entities.ApiKey;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.exceptions.InvalidTokenException;
import br.edu.unijui.gca.api.exceptions.ResourceNotFoundException;
import br.edu.unijui.gca.api.exceptions.TokenNotFoundException;
import br.edu.unijui.gca.api.services.ApiKeyService;
import br.edu.unijui.gca.api.services.JwtService;
import br.edu.unijui.gca.api.services.UserService;
import br.edu.unijui.gca.api.token.ApiKeyToken;
import br.edu.unijui.gca.api.token.BearerToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Objects;


@RequiredArgsConstructor
@Component
public class AuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    private final ApiKeyService apiKeyService;

    private final UserService userService;

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
        String email = jwtService.getSubject(bearerToken.token());

        User user = userService.findByEmail(email).orElseThrow(ResourceNotFoundException::new);

        if (jwtService.isTokenInvalid(bearerToken.token(), user)) {
            throw new InvalidTokenException();
        }

        setAuthentication(request, user);
    }

    private void authenticateApiKey(
            HttpServletRequest request,
            ApiKeyToken token) {
        ApiKey apiKey = apiKeyService.findByKeyPrefix(token.prefix()).orElseThrow(ResourceNotFoundException::new);
        apiKeyService.validateApiKey(token.secret(), apiKey);
        User user = userService.findById(apiKey.getUser().getId());
        setAuthentication(request, user);
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
