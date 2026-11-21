ALTER TABLE representantes
    ADD COLUMN falhas_login INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN bloqueado_ate TIMESTAMPTZ,
    ADD COLUMN versao_credencial BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_representantes_falhas_login CHECK (falhas_login BETWEEN 0 AND 5),
    ADD CONSTRAINT ck_representantes_versao_credencial CHECK (versao_credencial >= 0);
