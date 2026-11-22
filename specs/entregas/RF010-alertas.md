# SDD — emitir e distribuir alertas (RF010)

Fontes: RF010 regras 1–7, alteração, exclusão e pesquisa (`docs/requisitos/RF010.md`); RF009 regra 4; RF011 regras 2–4 (formato do aviso de alteração); RF001 (desativação e aviso no cadastro); RF013 regra 1; RS008. Responsável humano: Pedro; executor: IA na branch `Pedro3495/rf010`.

## Por que este RF

Pendentes pelas fontes, em ordem: RF010, RF011, RF012 e partes do RF013 (RF001–RF009 entregues; RF008 regras 3/6 e RF009 regra 4 aguardavam RF010–RF012). O RF010 é o primeiro da ordem e deixou de depender de decisão aberta: Pedro decidiu (22/11/2026) que o provedor de SMS/WhatsApp é simulado.

## Entrega

- `POST /ocorrencias/{protocolo}/alertas` (representante, CSRF): título, mensagem e orientações obrigatórios; só de ocorrência Confirmada, por ação explícita (regra 1, RS008). Qualquer área, novas emissões manuais permitidas, área atual da ocorrência e data/hora geradas no servidor (regra 6). Auditoria `EMITIR_ALERTA #<protocolo>` (RF013 regra 1).
- Destinatários: moradores com conta ativa cuja residência está na área da ocorrência (regra 2; RF001 desativação). Representantes não têm celular (RF004).
- Distribuição (regra 3) pela preferência do cadastro: SMS ou WhatsApp, com tentativa automática pelo outro canal se o escolhido falhar; "ambos" tenta os dois. O aviso no cadastro já existia (RF001 regra 3). Credenciais seguem pela porta antiga, sem "ambos" nem alternativa (regra 5).
- Falha (regra 4): o alerta fica salvo e visível; a tela do representante mostra quantos falharam nos dois canais e oferece `POST /ocorrencias/{protocolo}/alertas/{id}/reenvio`, que tenta só esses destinatários (contas ativas) e não repete quem já recebeu.
- RF009 regra 4 (pendência anterior): ao rever a verificação de uma ocorrência que já teve alerta, é gerado o aviso de alteração no formato do RF011 regra 4 (protocolo, indicação de alteração, resumo "Situação alterada de X para Y." e orientações opcionais), sem a justificativa interna, para a união dos destinatários dos alertas anteriores, excluindo desativados e mantendo quem mudou de área (regra 7). Sem alerta anterior, nenhum aviso (RF011 regra 2).
- `GET /morador/alertas` (morador): alertas e avisos dos quais é destinatário (pesquisa: "conforme os destinatários definidos"). Link na tela inicial do morador; rota adicionada ao `SegurancaConfig` (perfil MORADOR).
- Porta `EnvioAlertas` + `EnvioAlertasSimulado` (rotulado "envio simulado"): sem rede, sem log de celular ou texto; falha simulável por canal com `alertas.envio-simulado.canais-com-falha=SMS,WHATSAPP`.
- `V15__alertas_ocorrencias.sql`: `comunicados_ocorrencia` (tipo ALERTA/ALTERACAO, textos, área, representante, instante) e `destinatarios_comunicado` (entregue por destinatário), FKs `RESTRICT`. Alteração/exclusão: entidades sem setters e nenhuma rota de edição ou exclusão. `database/schema.sql` regenerado.

## Limites

- Nenhum envio real: o simulado não é integração entregue. Com provedor real, o envio deve sair da transação (`shortcut:` em `AlertaService`).
- Avisos de alteração de campos (RF011) e de encerramento (RF012) não existem; o tipo `ALTERACAO` e `avisarAlteracao` ficam prontos para reaproveitamento.
- Aviso de alteração não tem reenvio manual: a regra 4 do RF010 fala do alerta e o RF011 regra 4 não lista reenvio.
- Destinatário desativado depois da emissão não é reenviado (RF001: desativação bloqueia novos alertas e avisos).
- Regra 2 (antecedência ou no momento) não gera campo: é decisão do órgão sobre quando emitir.

## Evidências

`./scripts/verificar.sh` com JDK 21 em PostgreSQL isolado (base nova `alertas_rf010_alertas` no container `alertas-rf006-test`, porta 55440, `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=4`): 286 testes, 0 falhas, `BUILD SUCCESS`.

- `AlertaServiceTests` (5): só Confirmada e campos obrigatórios sem gravar; preferência SMS/WhatsApp/ambos com alternativa automática, inativos e outra área fora, reenvio só de quem falhou nos dois canais, área gravada e auditoria por emissão; aviso de alteração para a união dos destinatários anteriores sem desativado e com quem mudou de área, sem justificativa, e nenhum aviso sem alerta anterior; perfis (morador, administrador, anônimo) negados e qualquer representante emite; envio simulado falha só no canal configurado.
- `OcorrenciasHttpTests` (+1): formulário real com login. Em verificação recusa (400), sem CSRF 403, orientações em branco 400 mantendo o texto, protocolo inexistente 404, sucesso 302 com contagem de destinatários, reenvio inexistente 404; morador da área vê o alerta em `/morador/alertas`, morador de outra área não, anônimo negado, POST do morador não emite; revisão pelo formulário gera o aviso visível ao morador sem a justificativa.

Revisão de IA não é aprovação humana.
