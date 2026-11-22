# SDD — verificação de ocorrências (RF009)

Fontes: RF009 regras 1–4, alteração, exclusão e pesquisa (`docs/requisitos/RF009.md`); RF008 regras 3 e 6; RF013 regras 1 e 4; RS da verificação e RS008 (`docs/requisitos/SEGURANCA.md`). Responsável humano: Pedro; executor: IA na branch `Pedro3495/rf009`.

## Por que este RF

Pendentes pelas fontes, em ordem: RF009, RF010, RF011, RF012 e partes do RF013 (RF001–RF008 entregues). O backlog põe o RF009 na etapa 5, antes de alertas (RF010) e de alteração/encerramento (RF011/RF012), e a confirmação é pré-requisito do alerta. Não depende de decisão aberta: o RF010 ainda precisa do provedor de mensagens.

## Entrega

- `POST /ocorrencias/{protocolo}/verificacao` (só representante, com CSRF): situação Confirmada ou Não confirmada e justificativa obrigatória (regra 2). Funciona para qualquer área (regra 4). O formulário fica no detalhe do representante e avisa que confirmar não envia alerta (regra 3; nada é disparado).
- Revisão entre Confirmada e Não confirmada antes do encerramento, com justificativa (alteração). Repetir a decisão atual, voltar para Em verificação ou marcar Encerrada é recusado. Ocorrência Encerrada é recusada e não é reaberta.
- `V14__verificar_ocorrencias.sql`: amplia o `CHECK` de `ocorrencias.situacao` para as quatro situações do RF008 e cria `verificacoes_ocorrencia` (histórico só de inclusão, FK `RESTRICT`). Bloqueio pessimista da ocorrência evita decisões concorrentes sobrepostas.
- Auditoria (RF013 regras 1 e 4): `VERIFICAR_OCORRENCIA #<protocolo>: <anterior> -> <nova>`. A justificativa fica fora do texto da auditoria, que o administrador também consulta, porque é interna aos representantes (regra 4; RF008 regra 6).
- O histórico de verificações (situação, representante, data/hora, justificativa) aparece só na tela do representante. O morador continua vendo apenas a situação.
- Exclusão: nenhuma rota de exclusão. Pesquisa: a consulta do RF008 já filtra por protocolo, tipo, situação e área.

## Limites

- Regra 4, "se já houver alerta, enviar o aviso de alteração aprovado": alertas ainda não existem (RF010). Fica para o RF010/RF011 ligar o aviso a esta revisão; hoje nenhuma revisão tem alerta associado.
- A recusa da mesma decisão repetida interpreta "rever a decisão entre Confirmada e Não confirmada" como troca entre as duas; se a dupla preferir registrar repetições, basta remover essa linha em `Ocorrencia.verificar`.
- Encerrada só é gravada pelo RF012; os testes simulam o encerramento via SQL.

## Evidências

`./scripts/verificar.sh` com JDK 21 em PostgreSQL isolado (base nova `alertas_rf009_verificacao` no container `alertas-rf006-test`, porta 55440): 280 testes, 0 falhas, `BUILD SUCCESS`.

- `OcorrenciaVerificacaoServiceTests` (3): confirma e revê, com persistência da situação, do histórico com justificativa e da auditoria anterior→nova sem justificativa; filtro por situação; recusas (situação inválida, justificativa vazia, repetição, encerrada) sem gravar nada; qualquer representante e qualquer área; morador, administrador e anônimo negados; protocolo inexistente.
- `OcorrenciasHttpTests` (+1): formulário real com login. Sem CSRF dá 403, justificativa em branco dá 400 com mensagem, situação inválida dá 400 e protocolo inexistente dá 404. O sucesso redireciona e o detalhe mostra o histórico, o filtro `situacao=CONFIRMADA` encontra a ocorrência e a auditoria fica com uma linha. Autor e vizinho veem "Confirmada" sem justificativa nem formulário, e o POST deles não grava.

Revisão de IA não é aprovação humana.
