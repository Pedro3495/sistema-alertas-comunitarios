package br.edu.alertas.representantes;

import java.io.Serializable;
import java.security.Principal;

public record AcessoRepresentante(Long id, String login, long versaoCredencial,
        boolean trocaSenhaObrigatoria) implements Serializable, Principal {
    @Override
    public String getName() {
        return login;
    }
}
