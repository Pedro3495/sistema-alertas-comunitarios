# SDD — RF007: recuperação de acesso do morador

Fontes: RF007 (regras 1–6, Alteração, Exclusão, Pesquisa), RS002, RS003, RF005 regras 3 e 7 e "Alteração", RF001 regra 3. Word aprovado apenas lido. Responsável humano: Pedro; executor: IA na branch `Pedro3495/rf007-recuperacao`, base `feat/rf003-base-areas` (PR #16 aberto). Issue: #7. Reaproveita `EnvioCredenciais` (simulado), o validador de senha, a revogação por `versao_credencial` e o `SessaoValidadaFilter` do RF005.

## Quem recupera

Somente o **morador**. As fontes não preveem recuperação do representante no aplicativo: a redefinição dele é feita exclusivamente pelo administrador (RF005 "Alteração", RF004), e a do administrador é manual, fora do aplicativo (RF005 regra 7). Nada disso foi alterado aqui.

## Regras atendidas

| Regra (fonte) | Implementação |
|---|---|
| Confirmar o telefone e enviar o código só ao telefone já cadastrado, pelo canal de credenciais (r1, RS003; RF001 r3; RF005 r3) | `GET/POST /morador/recuperar` (CSRF). O número digitado (pontuação ignorada) localiza a conta; o código vai para `morador.celular` pelo `canalCredenciais` da pessoa via `EnvioCredenciais.enviarCodigoRecuperacao`. Sem canal alternativo. |
| Sem pesquisa pública de pessoas (Pesquisa) | Telefone desconhecido ou conta desativada recebem a mesma resposta do sucesso e nenhum envio; a tela não mostra nome, CPF, canal nem telefone. |
| 10 minutos, uso único, inválido após redefinição (r2, RS003) | `CodigoRecuperacao` (`@Embeddable` em `Morador`): guarda só o hash; vencido quando `agora >= emissão + 10 min`; invalidado ao redefinir. |
| Código não dá entrada direta; criar nova senha e entrar com CPF e senha (r3) | `POST /morador/recuperar/codigo` grava a nova senha e redireciona para `/morador/entrar?senhaAlterada` sem autenticar. |
| Nova senha com no mínimo 8 caracteres (Alteração; RF005 r4) | `RepresentanteService.validarSenha`, o mesmo do RF005. Senha curta não consome o código. |
| Novo código invalida o anterior; 5 erros invalidam (r5, RS003) | Cada emissão substitui hash, prazo e contador. O quinto erro apaga o código; exige nova emissão. |
| Falha de envio: informar e permitir reenvio manual pelo mesmo canal (r6) | Resultado `FALHA_ENVIO` (retorno `false` ou exceção do provedor) mostra "O código não foi enviado" e o botão "Reenviar código"; o reenvio é nova emissão. |
| Sem acesso ao telefone: procurar a entidade (r4) | Texto fixo na tela; a atualização do número já é a correção do RF001. |
| Senha não consultável (RS002) | Hash PBKDF2 do código e da senha; código não aparece em tela nem em log (teste com `CapturedOutput`). |

Efeitos sobre o RF005: a redefinição usa `definirSenhaDefinitiva` (desmarca troca obrigatória e incrementa a versão, encerrando sessões abertas) e zera `credenciais_pendentes`, para que um reenvio de temporária não sobrescreva a senha criada pela pessoa.

## Questão aberta do RF005 — temporária entregue que vence sem uso

Coberta pelo RF007: as fontes não restringem a recuperação a quem já trocou a temporária, e o RF005 manda a redefinição do morador seguir o RF007. Com o código, a pessoa cria a própria senha e entra (teste `codigoPeloCanalDeCredenciaisRedefineUmaVezESoCpfESenhaEntram`, a 25 h da emissão). Não foi criada nova emissão de temporária.

## Persistência

`V12__recuperacao_acesso_moradores.sql`: `codigo_recuperacao_hash`, `codigo_recuperacao_expira_em` (ambos nulos ou ambos preenchidos) e `falhas_codigo_recuperacao` (0–4). V1–V11 preservadas. `database/schema.sql` reexportado sem dados.

## Limites e pendências

- Envio **simulado** (decisão de Pedro): nenhuma mensagem real; dublê de teste não é integração entregue.
- Auditoria: não registrada. `AuditoriaService` exige representante/administrador autenticado e o fluxo é anônimo; as fontes do RF007 não pedem trilha. Decisão da dupla se quiser registrar.
- Limite de pedidos de código por telefone/IP: as fontes não definem; não foi inventado. Com provedor real, avaliar custo e abuso.
- Formato do código: 6 dígitos (detalhe técnico, as fontes não fixam). Conta com login bloqueado (RS001) continua bloqueada até o fim dos 15 min mesmo após redefinir.
- A falha de envio revela que o telefone tem conta (exigência da regra 6 de informar a falha).
- RS009 (HTTPS) é de implantação; testes usam HTTP em loopback.

## Evidências locais — 22/11/2026

- `./scripts/verificar.sh` com JDK 21 em PostgreSQL isolado (`alertas-rf006-test`, loopback 55440), base nova exclusiva `alertas_rf007_1795378606`: **264 testes, 0 falhas, 0 erros**, `BUILD SUCCESS`, `git diff --check` limpo. Execução anterior (antes dos documentos e de 2 asserções do simulador) em outra base nova `alertas_rf007_*`: 264 verdes.
- Flyway aplicou V1–V12 ("Successfully applied 12 migrations … now at version v12"); após a suíte, 0 linhas em `moradores`.
- Novos: `RecuperacaoAcessoServiceTests` (7: temporária vencida → código pelo canal WhatsApp da pessoa → mínimo 8 → redefine → código não reutiliza → só CPF+nova senha entram; sessão anterior revogada; 10 min no limite exato; nova emissão invalida a anterior; 4 erros mantêm e 5 invalidam; falha (false/exceção) sem trocar canal, telefone desconhecido/nulo/conta inativa sem envio; conta desativada após emissão não redefine) e 2 em `MoradoresHttpTests` (HTTP real com CSRF: link na entrada, 403 sem CSRF, telefone formatado, erro de código, senha curta, redirecionamento para entrar sem sessão, temporária recusada e nova senha aceita, código fora do log; falha de envio informada com reenvio pelo mesmo canal e telefone desconhecido com a mesma resposta). Testes de RF001/RF005/RF006 continuam verdes.
- Word: SHA-256 `f47da7f8a689957ed9041011230cf887175911f832caaab8b017662e85b70658` conferido sem regravar. Revisão de IA não equivale a aprovação humana.
