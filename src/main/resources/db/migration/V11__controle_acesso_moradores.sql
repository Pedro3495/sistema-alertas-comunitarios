-- Login do morador (RF005 regras 1, 2, 4 e 5): troca obrigatória da temporária, bloqueio por falhas e versão da sessão.
ALTER TABLE moradores
    ADD COLUMN troca_senha_obrigatoria BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN falhas_login INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN bloqueado_ate TIMESTAMPTZ,
    ADD COLUMN versao_credencial BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_moradores_falhas_login CHECK (falhas_login BETWEEN 0 AND 5),
    ADD CONSTRAINT ck_moradores_versao_credencial CHECK (versao_credencial >= 0);
