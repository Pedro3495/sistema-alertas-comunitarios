# Decisões de Pedro sobre a revisão de `main` (23/11/2026)

Responsável humano: Pedro; executor: IA na branch `Pedro3495/decisoes-revisao`. Complementam o Word aprovado sem alterá-lo. Nenhuma migração nova (última continua V17).

## 1. Reenvio de alerta de ocorrência encerrada (RF010 regra 4, RF012)

Recusado. `AlertaService.reenviar` lança `ErroOcorrencia("reenvio", ...)` antes de distribuir quando a situação é Encerrada: nada é enviado e nenhum `REENVIAR_ALERTA` é auditado. `OcorrenciaController.reenviarAlerta` devolve 400 com a tela da ocorrência e a mensagem "Ocorrência encerrada: o reenvio de alerta não é permitido." para o representante. O botão de reenvio não aparece em ocorrência encerrada.

## 2. Recuperação de acesso: resposta idêntica (RF007 regra 6 × RS003)

A regra 6 manda "informar a falha"; o RS003 e a Pesquisa do RF007 mandam não revelar se o telefone tem conta. Só um telefone cadastrado pode ter falha de envio, então informar a falha revelaria a conta. Pedro escolheu o RS003:

- `RecuperacaoAcessoService.solicitarCodigo` não devolve resultado (o enum `ResultadoEnvio` foi removido). Telefone conhecido, desconhecido, conta desativada, pedido acima do limite e falha de envio têm o mesmo status (302), o mesmo redirecionamento (`/morador/recuperar/codigo`) e a mesma tela.
- A tela do código mostra "Se o código não chegar, peça um novo"; o novo pedido é o reenvio manual pelo mesmo canal e invalida o anterior. A mensagem "O código não foi enviado" e o botão "Reenviar código" saíram.
- A falha é auditada como `FALHA_ENVIO_CODIGO_RECUPERACAO` (responsável: o morador; sem código nem telefone). O pedido continua auditado e conta no limite de 3 por hora.

## 3. Resumo do aviso de alteração escrito pelo representante (RF011 regra 6)

O formulário de alteração ganhou o campo "Resumo da mudança para o aviso". Com alerta anterior, ele é obrigatório (`Comunicado.alteracao` valida; erro no campo `resumo`, 400, nada gravado nem auditado) e é o texto do aviso, no lugar do texto gerado por campo. Sem alerta, não é exigido. A tela orienta a não incluir dados pessoais nem a justificativa interna; o sistema não tem como detectar isso. O texto gerado por campo continua em `alteracoes_ocorrencia.resumo` e na auditoria (valores anteriores e novos, RF013 regra 4).

O RF012 já usava esse mecanismo (resumo do motivo digitado, obrigatório só com alerta); nada mudou nele. O aviso automático da revisão da verificação (RF009 regra 4, "Situação alterada de X para Y.") também não mudou: a decisão trata da alteração de campos do RF011.

## 4. Exclusão de área com histórico de comunicados (RF003)

O histórico prevalece: a FK `comunicados_ocorrencia.area_id ... ON DELETE RESTRICT` (V15) continua impedindo a exclusão. Sem mudança de código.

## Testes

- `AlertaServiceTests.recusaReenvioDeOcorrenciaEncerradaSemEnviar` e, em `OcorrenciasHttpTests`, a recusa HTTP 400 com a mensagem, sem botão e sem auditoria nova.
- `RecuperacaoAcessoServiceTests.falhaDeEnvioFicaNaAuditoria...` (auditoria `PEDIDO`/`FALHA_ENVIO`) e `MoradoresHttpTests.falhaNoEnvioDoCodigoRespondeIgualATelefoneDesconhecido...` (mesmo status, mesmo `Location` e mesma página, salvo o token CSRF).
- `OcorrenciaAlteracaoServiceTests`: resumo em branco com alerta é recusado sem gravar; o aviso usa o resumo digitado e o histórico guarda o gerado. `OcorrenciasHttpTests`: 400 sem resumo e aviso do morador com o resumo, sem a justificativa nem o texto gerado.

## Evidências locais — 23/11/2026

`./scripts/verificar.sh` com JDK 21 em PostgreSQL isolado (`alertas-rf006-test`, loopback 55440), base nova exclusiva `alertas_decisoes_2`: **300 testes, 0 falhas, 0 erros**, `BUILD SUCCESS`, `git diff --check` limpo. Uma execução anterior na base `alertas_decisoes` foi interrompida no meio e deixou dados que quebraram a seguinte (CPF duplicado); por isso a base nova.
