CREATE TABLE administradores (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    login TEXT NOT NULL CHECK (length(trim(login)) > 0),
    senha_hash TEXT NOT NULL CHECK (length(trim(senha_hash)) > 0),
    falhas_login INTEGER NOT NULL DEFAULT 0 CHECK (falhas_login BETWEEN 0 AND 5),
    bloqueado_ate TIMESTAMPTZ,
    versao_credencial BIGINT NOT NULL DEFAULT 0 CHECK (versao_credencial >= 0)
);
