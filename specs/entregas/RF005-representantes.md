# SDD — RF005: acesso web de representantes

Fonte: RF004/RF005 e RS001/RS002, sem alteração do documento aprovado. Branch `feat/rf005-acesso-representantes`, base PR #17. Entrega parcial de RF005: moradores e recuperação administrativa ainda não implementados.

## Contrato do domínio e persistência

No pacote `br.edu.alertas.representantes`:

- `AcessoRepresentante(Long id, String login, long versaoCredencial, boolean trocaSenhaObrigatoria)`: record público `Serializable`, `Principal`; `getName()` retorna login. Não contém senha/hash.
- `AutenticacaoRepresentanteService.autenticar(String login, String senha)` retorna `Optional<AcessoRepresentante>`. Vazio para qualquer rejeição; erros de infraestrutura não se tornam falhas de senha. O contador de falhas confirma sua própria transação antes da resposta HTTP.
- `validarSessao(AcessoRepresentante acesso)` retorna boolean. Verifica conta ativa, versão e estado de troca atuais, e validade se temporária. Login não é fornecido pelo cliente após autenticação; identidade vem do principal.
- `trocarSenha(AcessoRepresentante acesso, String novaSenha)` retorna void. Exige sessão válida de primeira troca, senha mínima de oito pontos Unicode sem composição/máximo; grava hash, desmarca troca, incrementa versão. `IllegalArgumentException` para senha inválida, `IllegalStateException` para sessão inválida. Após sucesso, sair e pedir login com a nova senha.
- V4 `V4__controle_acesso_representantes.sql`: adicionar `falhas_login INTEGER NOT NULL DEFAULT 0`, `bloqueado_ate TIMESTAMPTZ NULL`, `versao_credencial BIGINT NOT NULL DEFAULT 0` à tabela existente. Check contador entre 0 e 5, versão não negativa.
- Login consulta conta com bloqueio pessimista; todos os writes de manutenção de representantes também adquirem lock da mesma linha, evitando perder contador/versão em concorrência. Não editar V1–V3.
- Somente senha incorreta de conta ativa registra falha de credencial. Na quinta falha, bloquear login por 15 minutos, inclusive senha correta. Tentativas durante bloqueio não estendem o prazo. No limite exato de 15min pode tentar novamente; contador reinicia. Sucesso zera falhas. Falha de senha é resultado, não exceção transacional que reverta o contador.
- Temporária vencida (agora >= expiração) e conta inativa não autenticam. Temporária correta antes do vencimento retorna principal com troca obrigatória; não dá acesso às demais funções.
- Redefinição de temporária e desativação invalidam sessões anteriores via versão. Correção de login também invalida a sessão com login antigo. Reativação não restaura sessões antigas. Versão é controle técnico, não novo campo no Word.
- A troca não acrescenta proibição de reutilização da senha, política de composição, limite de tamanho ou regra de expiração para a senha definitiva. Reset não libera um bloqueio de login ainda vigente.

## Contrato HTTP — coordenação do executor principal

Spring Security `AuthenticationProvider` delega ao serviço e usa falha genérica sem distinguir conta inexistente/inativa/vencida/bloqueada. Sessão não guarda credenciais. Sem Basic, remember-me, conta padrão ou rotas administrativas liberadas.

- `GET /entrar` formulário de representante, login e senha. `POST /entrar` autenticação com CSRF. Erro genérico; preservar login, nunca senha.
- Sucesso com temporária redireciona `/trocar-senha`; com definitiva, `/representante`.
- `GET/POST /trocar-senha`: somente principal em troca obrigatória; formulário `novaSenha`. Erro min8 junto ao campo; não repopular senha. Sucesso invalida sessão e redireciona `/entrar?senhaAlterada`.
- `GET /representante`: somente representante com senha definitiva. Página mínima de acesso, sem simular funções ainda não implementadas.
- `POST /sair` com CSRF invalida sessão. GET não realiza logout.
- Revalidar conta/versão/validade em cada requisição autenticada, antes da autorização. Conta desativada, reset, login corrigido ou temporária vencida não mantém acesso em sessão existente. Não registrar o filtro como servlet e security filter ao mesmo tempo.
- `/`, `/css/**`, `/actuator/health`, `/entrar` públicos. Todas as outras rotas seguem negadas, incluindo áreas, gestão de representantes e auditoria. A temporária dá somente autoridade `TROCAR_SENHA`, a definitiva `REPRESENTANTE`; nenhuma concede ADMIN.
- CSRF e proteção de fixação de sessão permanecem ativas. Mensagem de falha genérica orienta conferir login/senha, aguardar15min após5erros e procurar administrador se necessário, sem revelar existência da conta.

## Aceite e limites

Testes com PostgreSQL e HTTP reais: cadastro de fixture fictícia → login temporário → recusa de função completa → troca → sessão antiga inválida → login definitivo → acesso → sair. Testar5falhas persistidas,15min e24h nos limites, reinício consecutivo por sucesso, conta inativa, reset/desativação em sessão existente, CSRF e rotas negadas, sessão sem hash/senha e nenhum usuário padrão. Concorrência deve confirmar contador sob lock sem transação de teste mascarando commits.

Telas responsivas com labels, botões grandes e linguagem simples, seguindo referências visuais existentes sem nova ferramenta. HTTP em loopback para desenvolvimento/testes não comprova RS009/HTTPS de implantação. Não publicar deploy, enviar SMS/WhatsApp, gerar administrador fixo ou declarar RF004/RF005 completos. Próximas entregas incluem autenticação de moradores, administração/autorização, auditoria e demais fluxos.

**Decisão de Pedro (22/11/2026):** a troca da senha temporária pela definitiva (primeira troca) de morador e de representante é registrada na auditoria existente (`registros_auditoria`), sem senha nem hash. Ver [RF013-auditoria](RF013-auditoria.md).
