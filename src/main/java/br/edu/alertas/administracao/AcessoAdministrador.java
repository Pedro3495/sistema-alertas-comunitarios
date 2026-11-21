package br.edu.alertas.administracao;

import java.io.Serializable;
import java.security.Principal;

public record AcessoAdministrador(Long id, String login, long versaoCredencial)
        implements Serializable, Principal {
    @Override
    public String getName() {
        return login;
    }
}
