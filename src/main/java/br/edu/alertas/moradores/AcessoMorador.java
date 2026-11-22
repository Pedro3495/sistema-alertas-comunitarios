package br.edu.alertas.moradores;

import java.io.Serializable;
import java.security.Principal;

/** Identidade do morador na sessão: sem senha, hash, CPF ou telefone. */
public record AcessoMorador(Long id, long versaoCredencial, boolean trocaSenhaObrigatoria)
        implements Serializable, Principal {
    @Override
    public String getName() {
        return "morador#" + id;
    }
}
