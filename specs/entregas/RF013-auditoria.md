# SDD — registrar e consultar auditoria (RF013) e preferências do morador (RF001 regra 6)

Fontes: RF013 regras 1–4, alteração, exclusão e pesquisa (`docs/requisitos/RF013.md`); RF001 regras 3, 4 e 6 e "Alteração" (`docs/requisitos/RF001.md`); RF008 regras 3 e 6; RS005. Responsável humano: Pedro; executor: IA na branch `Pedro3495/rf013`. Issue: #13.

## Por que este RF

Pendentes pelas fontes, em ordem: RF001 regra 6 (o morador altera no aplicativo a preferência de alertas e o canal de credenciais — `RF001.md` ainda a listava como pendente e não havia rota nem serviço) e as partes restantes do RF013 (issue #13; RF002–RF012 entregues). Nenhuma depende de decisão aberta nem envia mensagens. A regra 6 do RF001 é exatamente a "operação permitida de manutenção de moradores" que faltava na cobertura do RF013 regra 3, então as duas foram entregues juntas.

## Conferência do RF013 em `main` (antes deste PR)

- Regra 1: `REGISTRAR_OCORRENCIA` (representante e morador), `VERIFICAR_OCORRENCIA`, `ALTERAR_OCORRENCIA`, `EMITIR_ALERTA`, `ENCERRAR_OCORRENCIA`, com responsável (tipo + id) e data/hora do servidor.
- Regra 2 / alteração / exclusão: consulta em `/admin/auditoria` e `/auditoria` só para administrador e representante definitivo (`UsuarioAtual.exigirConsultaAuditoria`); entidade `@Immutable`, sem rota ou serviço de alteração/exclusão.
- Regra 3: cadastro/alteração/desativação/reativação/redefinição de representantes; cadastro/alteração/exclusão de áreas e residências; cadastro/alteração/desativação/reativação e envio simulado de senha de moradores; pedido/redefinição de recuperação (RF007). Ações têm só o ID-alvo, nunca senha, hash ou código.
- Regra 4: verificação e encerramento guardam situação anterior → nova; alteração guarda `Campo: de "anterior" para "novo"` só dos campos alterados; justificativas ficam fora (RF008 regra 6). O responsável é a referência estável `tipo:id`, então corrigir login/nome do representante não reatribui ações antigas.
- Pesquisa: filtros por responsável (lista de referências), ação (trecho, sem curingas) e período UTC inclusivo, com validação de início ≤ fim.
- RF008 regras 3 e 6: já cumpridas pelo RF012 (`Ocorrencia` recusa verificar/alterar/encerrar Encerrada; alerta só de Confirmada; seções com justificativas só renderizam sem `morador`).

## Entrega deste PR

- `GET/POST /morador/preferencias` (morador com senha definitiva, CSRF; link na página inicial do morador): canal de credenciais (SMS/WhatsApp) e preferência de alertas (SMS/WhatsApp/ambos) separados; informa que o outro canal é tentado em falha (regra 3) e que o telefone só muda pela entidade (regra 4). O telefone não é exibido nem alterável.
- `Morador.alterarPreferencias` (mesma validação do cadastro) e `MoradorService.alterarPreferencias`/`proprio`: relê a conta sob lock e revalida a sessão (`bloquearAutor`); auditoria `ALTERAR_PREFERENCIAS_MORADOR #<id>` em nome do próprio morador (RF013 regra 3).
- Sem migração: as colunas já existem (V8). `database/schema.sql` não muda.

## Decisões de interpretação (registradas, sem regra nova)

- A definição da primeira senha (RF005) não foi auditada: é autenticação, não cadastro/manutenção pela entidade, e o RF013 não a cita. Se a dupla entender o contrário, basta `auditoria.registrarMorador` em `AutenticacaoMoradorService.trocarSenha` (cuidado com o ciclo de dependência via `UsuarioAtual`).
- Imutabilidade garantida pela aplicação (sem rota/serviço, `@Immutable`), não por trigger no banco: os testes limpam fixtures de auditoria por `DELETE`, e o requisito proíbe a alteração pelos usuários do sistema.
- Valores anteriores/novos só nas ocorrências, como diz a regra 4; a ação de preferências guarda só o ID.

## Limites

- Nenhum envio de mensagem neste PR; os existentes continuam simulados.
- Exibição do responsável na consulta continua como `TIPO #id` (referência), com nomes na lista do filtro.

## Evidências

`./scripts/verificar.sh` com JDK 21 em PostgreSQL isolado (base nova `alertas_rf013_verif` no container `alertas-rf006-test`, porta 55440, `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=4`): 298 testes, 0 falhas, 0 erros, `BUILD SUCCESS`, `git diff --check` limpo (exit 0).

- `MoradorServiceTests` (+1): representante não altera preferências pelo morador; morador autenticado com valor ausente recusado sem auditoria; alteração persiste canal e preferência mantendo o celular; auditoria `ALTERAR_PREFERENCIAS_MORADOR #id` com responsável MORADOR; conta desativada negada.
- `MoradoresHttpTests` (+1): anônimo e representante 403; link na página do morador; formulário com valores atuais, orientação de outro canal e de troca de telefone pela entidade, sem exibir o celular; POST sem CSRF 403; valor inválido 200 com erro; sucesso 302 → "Preferências salvas." e persistência real; uma linha de auditoria.

Revisão de IA não é aprovação humana.
