# SDD — base persistente de moradores

Fontes: RF001, RF002 (exclusão), RF005 regra 2, RF013, RS001/RS005/RS006. Responsável humano: Pedro; executor e integrador: IA nesta branch `feat/rf001-base-moradores`, sobre o PR #22. Revisão técnica estática pelo executor; não equivale a aprovação humana.

## Recorte

Implementar domínio, persistência e serviço autorizado de cadastro base, correção, pesquisa, desativação e reativação. Não expor cadastro HTTP: RF005 exige envio imediato da temporária no cadastro e o provedor ainda não foi escolhido. `cadastrarBase` é preparação interna, sem senha, autenticação ou promessa de entrega de credenciais. RF001/RF005 continuam parciais; a montagem do fluxo público deverá integrar cadastro e envio conforme o contrato aprovado.

V8 `V8__criar_moradores.sql` reservada: id, nome_completo, cpf, celular, residencia_id, canal_credenciais, preferencia_alertas, ativa. Nenhum comprovante armazenado nem campo novo de negócio. CPF e celular únicos inclusive desativados; referência obrigatória a residência com `ON DELETE RESTRICT`, sem cascata. Preservar V2–V7. Exportar estrutura sem dados.

`DadosMorador(nomeCompleto, cpf, celular, residenciaId, canalCredenciais, preferenciaAlertas)` usa enums SMS/WHATSAPP e SMS/WHATSAPP/AMBOS. CPF aceita exatamente 11 dígitos ASCII com verificadores válidos; celular exatamente 11 dígitos ASCII, sem regras adicionais de operadora/DDD. Não normalizar implicitamente entradas nem impor máximos aos textos. Validar todos os campos antes de alterar estado.

`MoradorService.cadastrarBase`, `alterar(id, nomeCompleto, cpf, celular, residenciaId)`, `buscar`, `pesquisar(termo)`, `desativar`, `reativar` exigem representante definitivo real pelo contrato `UsuarioAtual`. Correção pela entidade mantém canais; alteração das preferências pelo morador aguarda sua autenticação. Não existe exclusão de morador no serviço. Conferência presencial do comprovante continua responsabilidade da entidade, sem upload ou novo indicador persistido.

Escolha técnica da pesquisa: trecho literal sem distinção de caixa em nome OU CPF OU celular; termo vazio lista todos; ordenar por ID. Prechecks de duplicidade dão erro de preenchimento, restrições únicas decidem corridas e revertem operação e auditoria. Mutações existentes usam bloqueio de linha; FK decide disputas com exclusão de residência. Cada operação efetiva registra ação com ID e ator estável na mesma transação, sem CPF, telefone, senha ou código na ação.

## Aceite

Testar validadores, atomicidade da correção, canais separados, múltiplos moradores por residência, pesquisa literal, exclusividade com desativação, transferência, reativação da mesma identidade, negação de anônimo/admin/temporária/sessão revogada, rollback com falha da auditoria, FK e disputas reais de duplicidade e exclusão de residência. Testar também a recusa HTTP de exclusão vinculada no fluxo já existente, sem auditoria falsa. Suíte com PostgreSQL isolado descartável, restauração SQL em banco vazio, diff e CI do novo PR. Sem tela nova, merge ou deploy.

## Evidências locais — 22/11/2026

- `./scripts/verificar.sh` com `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55439/alertas_rf001_clean`, usuário fictício `alertas` e senha fictícia no ambiente: 177 testes, zero falhas/erros; inclui os 136 anteriores, 28 de domínio, 12 de serviço/persistência e uma integração HTTP nova. Duas execuções completas na mesma base limpa passaram, com zero fixtures restantes. A primeira tentativa de configurar o spy atravessou o proxy transacional `MANDATORY` fora de uma transação; corrigida com `AopTestUtils`, sem alterar produção. Essa tentativa deixou fixtures no primeiro banco descartável; a validação final usou outro banco vazio.
- PostgreSQL 17.10 isolado: container `alertas-rf001-test`, porta somente em loopback, `--tmpfs`, sem volume nem conexão com o banco de trabalho. Usado `docker run` isolado em lugar do Compose padrão para não iniciar/reutilizar o banco de trabalho.
- `pg_dump --schema-only --no-owner --no-acl --exclude-table=flyway_schema_history` atualizou `database/schema.sql`; `psql -v ON_ERROR_STOP=1` restaurou em `alertas_rf001_restore`, vazio: seis tabelas, FK com `confdeltype=r` e duas restrições únicas em moradores. Nenhum dado exportado.
- Revisão estática pelo executor: permissões em todas as operações, ausência de controller/cadastro público, correção sem trocar canais, validação anterior à mutação, constraints e auditoria na mesma transação. Sem revisão humana ou independente nesta execução.
- Word: SHA-256 `f47da7f8a689957ed9041011230cf887175911f832caaab8b017662e85b70658` conferido sem regravar. V2–V7 e configurações de segurança preservadas; `git diff --check` passou. Templates/CSS não foram alterados; não houve nova conferência visual.

Decisão necessária para expor o cadastro e implementar acesso/recuperação do morador: provedor, conta de testes e configuração local de segredos para envio real pelo canal escolhido. Não solicitar senhas/tokens no chat. O registro de ocorrências por representantes (RF006) continua sendo trabalho independente possível, incluindo fotos e FK restritiva de área; não está entregue nesta base.
