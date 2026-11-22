-- RF006 regras 1, 4, 7 e 9: o morador registra na área da residência; um autor por ocorrência; repetição por autor.
ALTER TABLE ocorrencias
    ALTER COLUMN representante_id DROP NOT NULL,
    ADD COLUMN morador_id BIGINT REFERENCES moradores(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_ocorrencias_um_autor CHECK ((representante_id IS NULL) <> (morador_id IS NULL)),
    ADD CONSTRAINT uk_ocorrencias_envio_morador UNIQUE (morador_id, envio_id);

-- RF013 regra 1: o registro da ocorrência pelo morador também é auditado.
ALTER TABLE registros_auditoria
    DROP CONSTRAINT registros_auditoria_responsavel_tipo_check,
    ADD CONSTRAINT registros_auditoria_responsavel_tipo_check
        CHECK (responsavel_tipo IN ('ADMINISTRADOR', 'REPRESENTANTE', 'MORADOR'));
