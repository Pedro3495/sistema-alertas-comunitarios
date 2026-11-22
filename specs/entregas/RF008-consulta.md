# SDD — consulta e acompanhamento de ocorrências (RF008)

Fontes: RF008 regras 1–6, alteração, exclusão e pesquisa (`docs/requisitos/RF008.md`); RF006 (registro já entregue). Responsável humano: Pedro; executor: IA na branch `feat/rf008-consulta-ocorrencias`.

## Por que este RF

Pendentes pelas fontes, em ordem: RF008, RF009, RF010, RF011, RF012 e partes do RF013 (RF007: limite/auditoria em PR próprio, #31). RF009–RF012 alteram ou notificam ocorrências que precisam ser consultadas; o RF008 é o primeiro na ordem e não depende de decisão aberta (não envia mensagens).

## Entrega

- `GET /ocorrencias` (representante): todas as áreas; busca por protocolo e filtros por tipo, situação e área (regra 4, pesquisa).
- `GET /morador/ocorrencias` (morador): próprios relatos, mesmo de área antiga, e relatos da área **atual** da residência (regras 1 e 5); filtros por protocolo, tipo e situação, sem filtro de área. A residência é relida a cada consulta (`bloquearAutor`), então a mudança de área vale na hora; ocorrências antigas não mudam de área.
- `GET /morador/ocorrencias/{protocolo}` e fotos passam a abrir relatos de terceiros da mesma área; outra área continua 404. A tela do morador nunca mostra nome, CPF ou celular do autor (regra 2), e só o autor recebe a chave de rascunho.
- Detalhe do representante: nome do autor e, quando o autor é morador, celular; sem CPF (regra 4). Representantes não têm celular cadastrado (RF004), então só o nome aparece para eles.
- `SituacaoOcorrencia` com as quatro situações da regra 3 (rótulos para a tela e filtros). A lista usa a projeção `ResumoOcorrencia` (sem autor e sem bytes das fotos).
- Nenhuma rota de alteração ou exclusão; nenhuma migração (o `CHECK` da V9 continua aceitando só `EM_VERIFICACAO` até o RF009).

## Limites

- Regra 3 (sem alterações após o encerramento) e regra 6 (justificativas internas) só ficam verificáveis com RF009/RF011/RF012.
- Sem paginação (`shortcut:` no serviço). Horário da lista formatado em `America/Sao_Paulo`.

## Evidências

- `OcorrenciaConsultaServiceTests` (3): próprios + área atual, mudança de área (antigos mantidos, terceiros da nova área), filtros de protocolo/tipo/situação/área, isolamento por perfil e anônimo.
- `OcorrenciasHttpTests` (+1): login real; vizinho lista e abre o relato sem nome/CPF/celular nem chave de rascunho; outra área 404; protocolo inválido; filtros; representante vê nome e celular sem CPF e filtra por área; perfis não usam as rotas um do outro.
- `OcorrenciaMoradorServiceTests` e o teste HTTP do RF006 ajustados: o morador "de fora" agora é de outra área, já que a mesma área passou a ser visível pelo RF008.

Revisão de IA não é aprovação humana.
