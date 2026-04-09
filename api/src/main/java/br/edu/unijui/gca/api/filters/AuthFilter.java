package br.edu.unijui.gca.api.filters;

import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.exceptions.InvalidTokenException;
import br.edu.unijui.gca.api.exceptions.TokenNotFoundException;
import br.edu.unijui.gca.api.exceptions.UserIsRequiredException;
import br.edu.unijui.gca.api.services.JwtService;
import br.edu.unijui.gca.api.services.UserService;
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

    private final UserService userService;

    private final HandlerExceptionResolver handlerExceptionResolver;

    public AuthFilter(JwtService jwtService,
                      UserService userService,
                      HandlerExceptionResolver handlerExceptionResolver) {
        this.jwtService = jwtService;
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
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null) {
                throw new TokenNotFoundException();
            }

            if (!authHeader.startsWith("Bearer ")) {
                throw new InvalidTokenException();
            }

            String token = authHeader.substring(7);
            String email = jwtService.getSubject(token);

            if (email == null) {
                throw new UserIsRequiredException();
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                User user = userService.findByEmail(email);

                if (jwtService.isTokenValid(token, user)) {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    user, null, null);

                    auth.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            handlerExceptionResolver.resolveException(request, response, null, e);
        }
    }
}
