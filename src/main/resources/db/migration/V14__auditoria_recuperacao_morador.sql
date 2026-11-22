-- RF007 (decisão de Pedro, 22/11/2026): o morador anônimo da recuperação passa a constar na auditoria.
ALTER TABLE registros_auditoria
    DROP CONSTRAINT registros_auditoria_responsavel_tipo_check,
    ADD CONSTRAINT registros_auditoria_responsavel_tipo_check
        CHECK (responsavel_tipo IN ('ADMINISTRADOR', 'REPRESENTANTE', 'MORADOR'));
