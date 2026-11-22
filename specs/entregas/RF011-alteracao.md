# SDD — alterar ocorrências e notificar (RF011)

Fontes: RF011 regras 1–6, alteração, exclusão e pesquisa (`docs/requisitos/RF011.md`); RF008 regras 3 e 6; RF010 regras 3 e 7 (distribuição e união dos destinatários); RF013 regras 1 e 4; RS008. Responsável humano: Pedro; executor: IA na branch `Pedro3495/rf011`.

## Por que este RF

Pendentes pelas fontes, em ordem: RF011, RF012 e partes do RF013 (RF001–RF010 entregues; issues #11, #12 e #13 abertas). O RF011 é o primeiro da ordem e não depende de decisão aberta: o envio usa a porta simulada `EnvioAlertas` já decidida no RF010.

## Entrega

- `POST /ocorrencias/{protocolo}/alteracao` (representante, CSRF), formulário na tela de detalhe com os valores atuais: tipo, especificação de Outros, descrição, localização, área, justificativa interna obrigatória e orientações atualizadas opcionais (regra 4, "quando necessárias"). Qualquer representante, qualquer área (regra 5).
- `Ocorrencia.alterar` (regras 1 e 5): só os cinco campos permitidos, com as mesmas validações do registro (RF006); ocorrência Encerrada recusada (RF008 regra 3); alteração sem mudança recusada (nada a auditar nem avisar). Fotos, autor, protocolo e situação não mudam. Erros voltam à tela com 400 e o texto digitado; protocolo inexistente 404.
- Auditoria (regra 6, RF013 regra 4): `ALTERAR_OCORRENCIA #<protocolo>: Campo: de "anterior" para "novo". ...` só com os campos alterados; responsável e data/hora vêm do registro de auditoria. A justificativa fica fora da auditoria e do aviso.
- `V16__alterar_ocorrencias.sql`: `alteracoes_ocorrencia` (justificativa, resumo, representante, instante; FKs `RESTRICT`), sem rota de edição ou exclusão. Histórico com justificativa só na tela do representante (RF008 regra 6). `database/schema.sql` regenerado.
- Aviso (regras 2–4): reaproveita `AlertaService.avisarAlteracao` do RF010. Só se a ocorrência já teve alerta; vai para a união dos destinatários das emissões anteriores, exceto contas desativadas (quem só mora na nova área não recebe); título "Alteração da ocorrência <protocolo>" (protocolo e indicação de alteração), mensagem = resumo da mudança, orientações se informadas; distribuição simulada pelas regras do alerta (preferência, outro canal em falha) e visível em `/morador/alertas`. Sem alerta, nenhum aviso; a ocorrência atualizada aparece na consulta do RF008.
- Pesquisa: pela lista existente do RF008 (protocolo, tipo, situação, área). Morador não altera (serviço exige representante; rota protegida por perfil).

## Decisões de interpretação (registradas, sem regra nova)

- "Resumo aprovado" (regra 6): o resumo é gerado pelo sistema a partir dos campos alterados, no mesmo padrão do aviso da revisão do RF009 ("Situação alterada de X para Y."). Não foi criado campo de resumo livre. Se a dupla preferir um resumo digitado pelo representante, é mudança pequena no formulário.
- Recusar alteração sem nenhuma mudança é validação de entrada, não regra de negócio nova.

## Limites

- Nenhum envio real; o simulado não é integração entregue. O envio continua dentro da transação (`shortcut:` em `AlertaService`).
- Aviso de alteração não tem reenvio manual (igual ao RF010).
- Encerramento (RF012) ainda não existe; a recusa de Encerrada foi testada gravando a situação direto no banco.

## Evidências

`./scripts/verificar.sh` com JDK 21 em PostgreSQL isolado (base nova `alertas_rf011_alteracao` no container `alertas-rf006-test`, porta 55440, `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=4`): 291 testes, 0 falhas, `BUILD SUCCESS`.

- `OcorrenciaAlteracaoServiceTests` (4): corrige os campos permitidos com fotos intactas, histórico com justificativa, auditoria com valores anteriores e novos sem justificativa, nenhum aviso sem alerta e morador da área vê a versão atual; com alerta, aviso à união dos destinatários ativos (desativado e morador só da nova área fora), resumo sem justificativa, orientações nulas quando vazias, situação preservada; validações do registro, justificativa, sem mudança e Encerrada recusados sem gravar; morador, administrador e anônimo negados, outro representante altera relato de morador de outra área mantendo o autor; protocolo inexistente.
- `OcorrenciasHttpTests` (+1): formulário real com login; sem CSRF 403, justificativa vazia 400 mantendo o texto, protocolo inexistente 404, sucesso 302 com histórico e auditoria; morador que recebeu o alerta vê o aviso com resumo e orientações, sem justificativa; tela do morador sem formulário de alteração; POST do morador não altera; fotos preservadas.
- `OcorrenciaServiceTests`: a verificação de que o serviço não expõe operações destrutivas deixou de proibir o nome `alterar` (agora permitido pelo RF011) e continua proibindo excluir, remover, deletar e substituir.

Revisão de IA não é aprovação humana.
