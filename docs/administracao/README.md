# Gestão administrativa e auditoria inicial

Continua o PR #19. Contrato: `specs/entregas/RF004-gestao.md`. O responsável confirmou em21/11/2026 a criação do primeiro administrador por comando local, sem conta padrão nem senha no código.

## Configurar e recuperar o administrador

No terminal, com PostgreSQL disponível e `.env` local preparado:

```sh
./scripts/configurar-admin.sh criar
```

O script seleciona Java21, recompila a aplicação e inicia somente o comando de configuração, sem servidor web. Informe o login e a senha quando solicitado; a senha não aparece no terminal. Há somente uma conta administrativa, protegida por restrição id1 no banco. Executar novamente não substitui a conta existente.

A recuperação aprovada é manual pelo responsável técnico, fora do aplicativo:

```sh
./scripts/configurar-admin.sh recuperar
```

Informe o login existente e a nova senha. Isso mantém a mesma conta e revoga sessões antigas. O bloqueio de login ainda vigente não é liberado pela redefinição. O comando exige terminal interativo; não aceita senha por argumento/entrada redirecionada. Nenhum administrador foi criado no banco de trabalho de Pedro durante esta execução: a validação usou banco descartável e dados fictícios.

## Fluxo web

`/admin/entrar` permite entrar como administrador e acessar `/admin/representantes`. É possível cadastrar conta, corrigir nome/entidade/login, desativar/reativar a mesma conta e definir outra temporária. Desativação, reativação e redefinição exigem confirmação explícita. A senha temporária deve ser definida e entregue diretamente ao representante; não é reencontrada em sessão, histórico ou consulta posterior. Validade de24h, primeira troca, exclusividade e preservação de contas desativadas seguem a base existente.

A pesquisa combina nome OU login, entidade e situação. Formulários preservam os dados não secretos quando há erro. Autorizações também são verificadas na fachada transacional; representantes, inclusive com senha definitiva, não gerenciam contas. O administrador não ganha automaticamente as funções reservadas aos representantes. Providers e AuthenticationManagers são isolados, sem fallback entre perfis mesmo com logins iguais.

`/admin/auditoria` e `/auditoria` permitem consulta pelo respectivo perfil; representante com temporária não consulta. Filtros por responsável estável (tipo+id), ação e intervalo UTC inclusivo. Login corrigido não troca a identidade dos registros anteriores. Não existem endpoints de alteração/exclusão da auditoria. Nesta entrega são auditados cadastro/correção/desativação/reativação/reset dos representantes; ação inclui o ID-alvo, nunca senha/hash/código. Transação inclui operação e histórico; erro na auditoria reverte a alteração.

## Validação e limites

119 testes locais passaram, incluindo gestão HTTP real, separação dos perfis, senha literal, confirmação, CSRF, revogação e rollback da alteração após falha de auditoria. Nove testes cobrem domínio administrativo/runner. O comando real foi executado em pseudo-terminal e banco descartável: exit0, prompts interativos, ausência da senha na saída. A exportação SQL foi restaurada em banco vazio com4tabelas. Telas administrativas conferidas a390×844 e1440×1000; conta fictícia de demonstração permaneceu somente no banco descartável, removido após a validação. Revisão estática independente sem achados acionáveis; não equivale a aprovação humana.

V5 é administrador; V6 é auditoria. A cadeia continua #16 → #17 → #19 → gestão administrativa, sem merge e sem alteração das proteções. O Word aprovado não foi modificado.

RF013 segue parcial: faltam operações dos demais domínios e valores anteriores/novos de ocorrências. Acesso de moradores/envio de credenciais também seguem pendentes. O usuário informou que ainda não escolheu provedor de SMS/WhatsApp; não há entrega real simulada. HTTPS de implantação/deploy não foi configurado. Scripts locais são para macOS/Linux; não foi criado nem verificado provisionamento equivalente em Windows nesta entrega.
