-- RF013: registro de auditoria é imutável. Nenhum fluxo altera um registro; o trigger barra UPDATE até para o dono da tabela.
-- DELETE fica negado pelas permissões (database/contas-permissoes.sql): a aplicação só recebe SELECT e INSERT.
CREATE FUNCTION impedir_alteracao_auditoria() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'registros_auditoria é imutável: UPDATE do registro % não é permitido', OLD.id
        USING ERRCODE = 'restrict_violation';
END
$$;

CREATE TRIGGER tg_registros_auditoria_imutavel
    BEFORE UPDATE ON registros_auditoria
    FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_auditoria();
