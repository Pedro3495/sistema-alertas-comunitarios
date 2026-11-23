-- Contas e permissões do Sistema de Alertas Comunitários (rodar pelo DBA; não é migração Flyway,
-- porque papéis pertencem ao cluster e o usuário da aplicação não precisa de CREATEROLE).
-- Rodar como superusuário, conectado à base do sistema, depois das migrações:
--   psql -d alertas -v senha_admin='...' -v senha_app='...' -v senha_leitura='...' -f database/contas-permissoes.sql
-- As senhas chegam só por variável do psql; nunca gravar senhas reais neste arquivo.
\set ON_ERROR_STOP on

-- Contas
CREATE ROLE alertas_admin LOGIN PASSWORD :'senha_admin';      -- dono da estrutura: DDL e migrações Flyway
CREATE ROLE alertas_app LOGIN PASSWORD :'senha_app';          -- a aplicação Spring: só DML necessário
CREATE ROLE alertas_leitura LOGIN PASSWORD :'senha_leitura';  -- consulta: só a view de resumo

-- Ninguém recebe nada por padrão
REVOKE ALL ON DATABASE :"DBNAME" FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE :"DBNAME" TO alertas_admin, alertas_app, alertas_leitura;

-- alertas_admin passa a ser dono do schema, das tabelas, da view e da função do trigger
ALTER SCHEMA public OWNER TO alertas_admin;
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'public' AND c.relkind IN ('r', 'v') LOOP
        EXECUTE format('ALTER TABLE public.%I OWNER TO alertas_admin', r.relname);
    END LOOP;
END $$;
ALTER FUNCTION impedir_alteracao_auditoria() OWNER TO alertas_admin;

-- alertas_app: usa o schema, sem criar objetos
GRANT USAGE ON SCHEMA public TO alertas_app, alertas_leitura;
GRANT SELECT ON flyway_schema_history TO alertas_app;  -- validação do Flyway ao iniciar
-- cadastros e ocorrências mudam de estado (UPDATE também cobre os bloqueios SELECT ... FOR UPDATE)
GRANT SELECT, INSERT, UPDATE ON areas_risco, residencias, moradores, representantes, administradores,
    ocorrencias, fotos_ocorrencia, comunicados_ocorrencia, destinatarios_comunicado TO alertas_app;
-- só áreas e residências são excluídas pelo sistema (RF002/RF003)
GRANT DELETE ON areas_risco, residencias TO alertas_app;
-- históricos imutáveis: só incluir e consultar (o trigger de V18 também barra UPDATE na auditoria)
GRANT SELECT, INSERT ON registros_auditoria, verificacoes_ocorrencia, alteracoes_ocorrencia,
    encerramentos_ocorrencia TO alertas_app;

-- alertas_leitura: somente o resumo, sem dados pessoais
GRANT SELECT ON vw_resumo_ocorrencias_area TO alertas_leitura;
