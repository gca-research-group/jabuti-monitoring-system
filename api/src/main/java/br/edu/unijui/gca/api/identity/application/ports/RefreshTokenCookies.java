package br.edu.unijui.gca.api.identity.application.ports;

import br.edu.unijui.gca.api.entities.User;

public interface RefreshTokenCookies {
    void setRefreshToken(User user);
    void removeRefreshToken();
}
