-- Senha temporária do morador (RF005 regra 2/4): só o hash; credenciais_pendentes marca envio não concluído.
ALTER TABLE moradores
    ADD COLUMN senha_hash TEXT CHECK (senha_hash ~ '[^[:space:]]'),
    ADD COLUMN senha_temporaria_expira_em TIMESTAMPTZ,
    ADD COLUMN credenciais_pendentes BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_moradores_senha_temporaria CHECK ((senha_hash IS NULL) = (senha_temporaria_expira_em IS NULL));
