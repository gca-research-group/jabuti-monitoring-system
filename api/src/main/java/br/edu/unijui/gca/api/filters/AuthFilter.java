package br.edu.unijui.gca.api.filters;

import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.exceptions.InvalidTokenException;
import br.edu.unijui.gca.api.exceptions.ResourceNotFoundException;
import br.edu.unijui.gca.api.exceptions.TokenNotFoundException;
import br.edu.unijui.gca.api.services.JwtService;
import br.edu.unijui.gca.api.services.PasswordService;
import br.edu.unijui.gca.api.services.UserService;
import br.edu.unijui.gca.api.valueobjects.AuthToken;
import br.edu.unijui.gca.api.valueobjects.BasicToken;
import br.edu.unijui.gca.api.valueobjects.BearerToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;


@Component
public class AuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    private final PasswordService passwordService;

    private final UserService userService;

    private final HandlerExceptionResolver handlerExceptionResolver;

    public AuthFilter(JwtService jwtService,
                      UserService userService,
                      PasswordService passwordService,
                      HandlerExceptionResolver handlerExceptionResolver) {
        this.jwtService = jwtService;
        this.passwordService = passwordService;
        this.userService = userService;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        var isImage = path.startsWith("/images/");
        var isAuth = path.contains("auth");
        var isFavicon = path.contains("favicon");
        return isAuth || isImage || isFavicon;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    )  {
        try {
            String authorization = request.getHeader("Authorization");

            if (authorization == null) {
                throw new TokenNotFoundException();
            }

            AuthToken authToken = AuthToken.from(authorization);

            if (authToken instanceof BearerToken bearerToken) {
                authenticateBearer(request, bearerToken);
            }

            if (authToken instanceof BasicToken basicToken) {
                authenticateBasic(request, basicToken);
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

        if (!jwtService.isTokenValid(bearerToken.token(), user)) {
            throw new InvalidTokenException();
        }

        setAuthentication(request, user);
    }

    private void authenticateBasic(
            HttpServletRequest request,
            BasicToken token
    ) {
        User user = userService.findByEmail(token.email()).orElseThrow(ResourceNotFoundException::new);

        passwordService.validatePassword(token.password(), user.getPassword());

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
