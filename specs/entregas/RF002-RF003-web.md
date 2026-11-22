# SDD — áreas e residências para representantes

Fontes: RF002, RF003, RF013 e RS001/RS005. Continuação do PR #20 na branch `feat/rf002-residencias-areas`. O usuário ainda não escolheu o provedor de SMS/WhatsApp; esta entrega não envia mensagens nem cria moradores. O Word aprovado permanece intacto.

## Coordenação

V7 `V7__criar_residencias.sql` reservada. Um agente implementa o pacote, templates e CSS de residências; outro prepara testes HTTP independentes. O coordenador implementa áreas, permissões e navegação, integra os commits e executa os testes. Cada executor usa worktree próprio.

Contratos compartilhados: `UsuarioAtual.exigirRepresentante()` exige principal definitivo válido e retorna `Responsavel`; administrador não recebe essa permissão. `AuditoriaService.registrar(String acao)` usa o ator autenticado e exige transação existente.

## Residências

DTO público: `DadosResidencia(String ruaLogradouro, String numero, String bairro, String municipio, String complemento, String pontoReferencia, Long areaId)`.

Operações de `ResidenciaService`:

- `cadastrar(DadosResidencia)` e `alterar(Long id, DadosResidencia)` retornam `Residencia`.
- `buscar(Long id)` retorna `Residencia`.
- `pesquisar(String rua, String numero, String bairro, String municipio, Long areaId)` retorna lista.
- `excluir(Long id)` não retorna valor.

Todas exigem representante definitivo. Mutações e auditoria participam da mesma transação; ações são `CADASTRAR_RESIDENCIA`, `ALTERAR_RESIDENCIA` e `EXCLUIR_RESIDENCIA`, incluindo o ID do alvo. Validar todos os campos antes de alterar o domínio.

Rua/logradouro, número, bairro e município são obrigatórios. Complemento e ponto de referência são opcionais. Número é texto e aceita `s/n`. Uma área existente é selecionada manualmente. Não acrescentar CEP, UF, coordenadas, comprovante, exclusividade do endereço ou máximos de caracteres.

V7 cria `residencias`: id, rua_logradouro, numero, bairro, municipio, complemento, ponto_referencia e area_id. Textos são `TEXT`; os quatro obrigatórios rejeitam conteúdo vazio. `area_id` é obrigatório e referencia `areas_risco(id)` com `ON DELETE RESTRICT`. Carregar a área necessária à renderização sem depender de OpenInView. Expor getters de id, campos, areaId e areaNome para formulário/resultado.

Escolha técnica da pesquisa: trechos sem distinção de caixa nos quatro campos textuais, combinados com AND; `%`, `_` e barra invertida são literais. Filtro vazio não restringe; área usa igualdade.

Rotas:

- GET `/residencias`: filtros `rua`, `numero`, `bairro`, `municipio`, `areaId`.
- GET/POST `/residencias/novo`: campos do DTO.
- GET/POST `/residencias/{id}/editar`.
- POST `/residencias/{id}/excluir`: exige `confirmacao=true`.

Sucesso redireciona para `/residencias?sucesso`. Erros de preenchimento retornam200, explicam o campo e preservam os valores, inclusive seleção de área inválida ou removida durante o preenchimento. ID inexistente retorna404. Sem áreas, orientar o cadastro em `/areas/novo`, sem seed. Não excluir por GET. Falta de confirmação não altera nem audita. Não consultar vínculos fictícios com moradores ainda inexistentes.

## Áreas

Preservar o contrato existente: `pesquisarPorNome` rejeita termo vazio. Acrescentar `buscar`, `listar` e `excluir`; a fachada usa `listar` quando a pesquisa vier vazia. Exclusão deve executar flush para que conflito de FK reverta a transação. A fachada exige representante definitivo e audita cadastro, alteração e exclusão com os prefixos `CADASTRAR_AREA`, `ALTERAR_AREA` e `EXCLUIR_AREA`, incluindo ID.

GET `/areas` recebe filtro `nome`. GET/POST `/areas/novo` e `/areas/{id}/editar` recebem `nome` e `descricaoLocalizacaoLimites`. POST `/areas/{id}/excluir` exige `confirmacao=true`. Obrigatórios inválidos retornam200 com erro junto ao campo e valores preservados; ID inexistente retorna404. A alteração mantém o ID e os vínculos residenciais, sem redistribuição automática. O FK real proíbe excluir área com residência vinculada.

## Autorização e interface

A cadeia dos representantes libera `/areas`, `/areas/**`, `/residencias` e `/residencias/**` somente para representante definitivo. Temporária e administrador são negados; anônimo pode receber403 ou redirecionamento ao login. O ator vem da sessão, nunca do formulário.

Formulários mantêm CSRF. Navegação oferece áreas, residências, auditoria e sair. Preservar Astryx Neutral e os padrões existentes, com labels, botões grandes e layout responsivo. Não conceder ao administrador funções exclusivas dos representantes.

## Aceite e limites

Verificar com HTTP e PostgreSQL reais: cadastro/edição/pesquisa/exclusão, campos obrigatórios e opcionais, `s/n`, filtros AND e literais, transferência manual de área, vínculos preservados na correção da área, recusa da exclusão vinculada sem auditoria falsa, confirmação, autorização e CSRF. O histórico deve manter o responsável estável.

Testar criação de residência versus exclusão de área e transferência versus exclusão da área de destino em transações concorrentes: nenhuma residência órfã e nenhuma confirmação conjunta de estado inválido. Conferir SQL em banco vazio, suíte local/CI, revisão independente e telas a390/1440px.

Moradores e ocorrências ainda não existem. Seus bloqueios de exclusão não estão entregues; não criar counts ou tabelas fictícias. As migrações desses módulos precisarão de FKs restritivas antes de expor seus cadastros. RF002/RF003 permanecem parciais quanto a essas integrações. Sem merge ou deploy.
