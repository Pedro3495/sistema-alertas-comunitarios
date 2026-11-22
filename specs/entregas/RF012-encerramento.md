# SDD — encerrar ocorrências e notificar (RF012)

Fontes: RF012 regras 1–4, alteração, exclusão e pesquisa (`docs/requisitos/RF012.md`); RF008 regras 3 e 6; RF010 regras 3 e 7 (distribuição e união dos destinatários); RF013 regras 1 e 4; RS008. Responsável humano: Pedro; executor: IA na branch `Pedro3495/rf012`.

## Por que este RF

Pendentes pelas fontes, em ordem: RF012 e partes do RF013 (RF001–RF011 entregues; issues #12 e #13 abertas). O RF012 é o primeiro da ordem e não depende de decisão aberta: o envio usa a porta simulada `EnvioAlertas` do RF010. Ele também fecha as pendências do RF008 regras 3 e 6 (sem alterações após o encerramento; justificativa de encerramento interna).

## Entrega

- `POST /ocorrencias/{protocolo}/encerramento` (representante, CSRF), formulário "Encerrar ocorrência" na tela de detalhe: justificativa interna obrigatória, resumo do motivo e orientações quando necessárias (regras 1, 3 e 4). Qualquer representante, qualquer área, qualquer situação ainda não encerrada (regra 4).
- `Ocorrencia.encerrar`: situação Encerrada; novo encerramento recusado. Verificação (RF009), alteração (RF011) e emissão de alerta (RF010, só Confirmada) já recusavam Encerrada, então não há reabertura nem novas alterações (alteração; RF008 regra 3). Após encerrar, a tela mostra "Encerrada · somente consulta" com a justificativa só para representantes (RF008 regra 6) e esconde os formulários.
- Auditoria (RF013 regras 1 e 4): `ENCERRAR_OCORRENCIA #<protocolo>: <anterior> -> Encerrada`, sem a justificativa.
- Aviso (regras 2 e 3): só se a ocorrência já teve alerta; vai para a união dos destinatários das emissões anteriores, exceto contas desativadas, com título "Encerramento da ocorrência <protocolo>" (protocolo e indicação de encerramento), mensagem = resumo do motivo, orientações se informadas. Distribuição simulada pelas regras do alerta (preferência, outro canal em falha) e visível em `/morador/alertas` como "Aviso de encerramento". Sem alerta anterior, nenhum aviso.
- `V17__encerrar_ocorrencias.sql`: `encerramentos_ocorrencia` (único por ocorrência; justificativa, representante, instante; FKs `RESTRICT`) e tipo `ENCERRAMENTO` no `CHECK` de `comunicados_ocorrencia`. Sem rota de edição ou exclusão (exclusão). `database/schema.sql` regenerado.
- Pesquisa: pela lista do RF008 (protocolo e filtro de situação Encerrada), com as mesmas permissões.

## Decisões de interpretação (registradas, sem regra nova)

- O resumo do motivo só é exigido quando há alerta anterior: o texto aprovado o lista em "Conteúdo do aviso quando houver alerta anterior", e o único campo obrigatório do encerramento é a justificativa. Sem alerta, o resumo digitado é ignorado (não há aviso).
- O reenvio manual de um alerta emitido antes do encerramento (RF010 regra 4) continua disponível: é nova tentativa de entrega do mesmo alerta, não alteração da ocorrência. Se a dupla entender o contrário, é um teste de situação em `AlertaService.reenviar`.

## Limites

- Nenhum envio real; o simulado não é integração entregue. O envio continua dentro da transação (`shortcut:` em `AlertaService`).
- Aviso de encerramento não tem reenvio manual (igual ao aviso de alteração).

## Evidências

`./scripts/verificar.sh` com JDK 21 em PostgreSQL isolado (base nova `alertas_rf012_encerramento2` no container `alertas-rf006-test`, porta 55440, `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=4`): 296 testes, 0 falhas, `BUILD SUCCESS`.

- `OcorrenciaEncerramentoServiceTests` (4): sem alerta encerra sem aviso, justificativa limpa no histórico, auditoria sem justificativa, pesquisa por protocolo/situação para representante e morador da área; com alerta, resumo exigido sem gravar nada, aviso à união dos destinatários ativos (desativado e quem não recebeu alerta fora), título com protocolo, resumo e orientações, sem justificativa; justificativa obrigatória, novo encerramento, verificação, alteração e emissão de alerta recusados após o encerramento; `DELETE` da ocorrência e segundo encerramento barrados pelo banco; morador, administrador e anônimo negados, outro representante encerra relato de morador de outra área; protocolo inexistente.
- `OcorrenciasHttpTests` (+1): formulário real com login; sem CSRF 403, resumo vazio com alerta 400 mantendo o texto, protocolo inexistente 404, POST do morador não encerra, sucesso 302 com "Encerrada · somente consulta" e sem formulários, novo encerramento 400; morador que recebeu o alerta vê o aviso com resumo e orientações, sem justificativa; fotos preservadas.
- `OcorrenciasFixtures`: limpeza das fixtures inclui `encerramentos_ocorrencia`.

Revisão de IA não é aprovação humana.
