package br.edu.unijui.gca.api.identity;

import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.exceptions.ExpiredTokenException;
import br.edu.unijui.gca.api.exceptions.InvalidTokenException;
import br.edu.unijui.gca.api.exceptions.InvalidPasswordException;
import br.edu.unijui.gca.api.exceptions.ResourceNotFoundException;
import br.edu.unijui.gca.api.identity.application.AuthService;
import br.edu.unijui.gca.api.identity.application.ports.PasswordVerifier;
import br.edu.unijui.gca.api.identity.application.ports.RefreshTokenCookies;
import br.edu.unijui.gca.api.identity.application.ports.TokenOperations;
import br.edu.unijui.gca.api.identity.application.ports.UserStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthenticationTests {

    @Test
    void loginValidatesPasswordSetsRefreshCookieThenReturnsAccessTokenWithoutServletDependencies() {
        var users = mock(UserStore.class);
        var passwords = mock(PasswordVerifier.class);
        var tokens = mock(TokenOperations.class);
        var cookies = mock(RefreshTokenCookies.class);
        var user = new User();
        user.setId(7L);
        user.setName("Researcher");
        user.setEmail("researcher@example.test");
        user.setPassword("encoded-password");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(tokens.generateAccessToken(user)).thenReturn("access-token");

        var response = new AuthService(passwords, users, tokens).authenticate(user.getEmail(), "password", cookies);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Researcher");
        assertThat(response.email()).isEqualTo(user.getEmail());
        assertThat(response.accessToken()).isEqualTo("access-token");
        var order = inOrder(passwords, cookies, tokens);
        order.verify(passwords).validatePassword("password", "encoded-password");
        order.verify(cookies).setRefreshToken(user);
        order.verify(tokens).generateAccessToken(user);
    }

    @Test
    void refreshSetsTheCookieBeforeGeneratingTheAccessToken() {
        var users = mock(UserStore.class);
        var passwords = mock(PasswordVerifier.class);
        var tokens = mock(TokenOperations.class);
        var cookies = mock(RefreshTokenCookies.class);
        var user = new User();
        user.setEmail("researcher@example.test");
        when(tokens.getSubject("refresh-token")).thenReturn(user.getEmail());
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(tokens.generateAccessToken(user)).thenReturn("new-access-token");

        var response = new AuthService(passwords, users, tokens).refresh(cookies, "refresh-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        var order = inOrder(tokens, users, cookies);
        order.verify(tokens).getSubject("refresh-token");
        order.verify(users).findByEmail(user.getEmail());
        order.verify(tokens).isTokenInvalid("refresh-token", user);
        order.verify(cookies).setRefreshToken(user);
        order.verify(tokens).generateAccessToken(user);
        verifyNoInteractions(passwords);
    }

    @Test
    void expiredRefreshRetainsCookieRemovalAndTheOriginalException() {
        var users = mock(UserStore.class);
        var passwords = mock(PasswordVerifier.class);
        var tokens = mock(TokenOperations.class);
        var cookies = mock(RefreshTokenCookies.class);
        var failure = new ExpiredTokenException();
        when(tokens.getSubject("expired")).thenThrow(failure);

        assertThatThrownBy(() -> new AuthService(passwords, users, tokens).refresh(cookies, "expired")).isSameAs(failure);
        verify(cookies).removeRefreshToken();
        verifyNoMoreInteractions(cookies);
        verifyNoInteractions(users, passwords);
    }

    @Test
    void invalidRefreshDoesNotChangeCookiesOrGenerateAnAccessToken() {
        var users = mock(UserStore.class);
        var tokens = mock(TokenOperations.class);
        var cookies = mock(RefreshTokenCookies.class);
        var user = new User();
        user.setEmail("researcher@example.test");
        when(tokens.getSubject("invalid")).thenReturn(user.getEmail());
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(tokens.isTokenInvalid("invalid", user)).thenReturn(true);

        assertThatThrownBy(() -> new AuthService(mock(PasswordVerifier.class), users, tokens).refresh(cookies, "invalid"))
                .isInstanceOf(InvalidTokenException.class);
        verifyNoInteractions(cookies);
        verify(tokens, never()).generateAccessToken(any());
    }

    @Test
    void badPasswordDoesNotIssueCookiesOrTokens() {
        var users = mock(UserStore.class);
        var passwords = mock(PasswordVerifier.class);
        var tokens = mock(TokenOperations.class);
        var cookies = mock(RefreshTokenCookies.class);
        var user = new User();
        user.setPassword("encoded");
        when(users.findByEmail("researcher@example.test")).thenReturn(Optional.of(user));
        doThrow(new InvalidPasswordException()).when(passwords).validatePassword("wrong", "encoded");

        assertThatThrownBy(() -> new AuthService(passwords, users, tokens).authenticate("researcher@example.test", "wrong", cookies))
                .isInstanceOf(InvalidPasswordException.class);
        verifyNoInteractions(cookies, tokens);
    }

    @Test
    void missingUserRetainsItsErrorWithoutIssuingCredentials() {
        var users = mock(UserStore.class);
        var passwords = mock(PasswordVerifier.class);
        var tokens = mock(TokenOperations.class);
        var cookies = mock(RefreshTokenCookies.class);
        when(users.findByEmail("missing@example.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new AuthService(passwords, users, tokens).authenticate("missing@example.test", "password", cookies))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(passwords, tokens, cookies);
    }
}
