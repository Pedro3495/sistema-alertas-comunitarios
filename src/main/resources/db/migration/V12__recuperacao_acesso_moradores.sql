-- Recuperação de acesso do morador (RF007 / RS003): só o hash do código mais recente, validade e erros.
ALTER TABLE moradores
    ADD COLUMN codigo_recuperacao_hash TEXT CHECK (codigo_recuperacao_hash ~ '[^[:space:]]'),
    ADD COLUMN codigo_recuperacao_expira_em TIMESTAMPTZ,
    ADD COLUMN falhas_codigo_recuperacao INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_moradores_codigo_recuperacao CHECK ((codigo_recuperacao_hash IS NULL) = (codigo_recuperacao_expira_em IS NULL)),
    ADD CONSTRAINT ck_moradores_falhas_codigo_recuperacao CHECK (falhas_codigo_recuperacao BETWEEN 0 AND 4);
