# SDD — base de representantes (#15)

Data: 21/11/2026. Responsável: Pedro, com execução assistida por IA e subagentes. O colega deixou o desenvolvimento por decisão informada pelo responsável. RF004 e o documento aprovado continuam como fonte de negócio.

## Recorte verificável

Implementar no pacote `br.edu.alertas.representantes`: conta persistida, cadastro, correção, desativação/reativação, redefinição de temporária e pesquisa. Não expor controllers ou alterar `SegurancaConfig` neste recorte. Autorização do administrador, autenticação/primeira troca e auditoria são integrações posteriores; não declarar RF004/RF005 completos.

## Contrato para implementação e testes independentes

- `RepresentanteService.cadastrar(String nomeCompleto, String entidadePublica, String login, String senhaTemporaria)` retorna `Representante`.
- `alterar(Long id, String nomeCompleto, String entidadePublica, String login)`, `desativar(Long id)`, `reativar(Long id)` e `redefinirSenhaTemporaria(Long id, String senha)` retornam `Representante`.
- `pesquisar(String termoNomeOuLogin, String entidadePublica, Boolean ativa)` retorna `List<Representante>`.
- Getters públicos do domínio: `getId`, `getNomeCompleto`, `getEntidadePublica`, `getLogin`, `isAtiva`, `isTrocaSenhaObrigatoria`, `getSenhaTemporariaExpiraEm`. Sem senha ou hash em getter público; sem `toString` com credenciais.
- `RepresentanteRepository` persiste e consulta; não expor operação de exclusão no serviço. Repositório pode usar as interfaces de consulta já fornecidas pelo Spring Data.
- Configuração de `PasswordEncoder` e `Clock` dentro do pacote representantes, sem mudar a cadeia HTTP de segurança. Relógio injetável para testar os 24h.

## Regras rastreadas

1. RF004: nome, entidade, login e senha individual; manter identidade técnica estável nas correções.
2. RF004: login atual exclusivo em todas as contas, inclusive desativadas. Desativação não apaga conta nem libera seu login. Não criar reserva histórica de logins antigos corrigidos: essa política não foi definida.
3. RF004/RS002: senha com mínimo de oito caracteres, sem exigir composição ou acrescentar máximo. Persistir somente hash adaptativo com sal; PBKDF2 do Spring já disponível evita acrescentar dependência ou o limite de bytes do BCrypt.
4. RF004/RF005: criação e redefinição marcam troca obrigatória e validade da temporária por 24 horas. O serviço recebe a temporária do chamador; não guarda a original nem implementa envio ou tela de entrega nesta base.
5. RF004: desativação/reativação preservam o mesmo registro; não confundir reativação com autenticação de conta inativa.
6. RF004: pesquisa por nome OU login, combinada com filtros de entidade e situação.

## Escolhas técnicas explícitas

Campos textuais em `TEXT`; login único por igualdade exata (sem normalização implícita). Nome/entidade/login do cadastro não aceitam null, vazio ou apenas espaços. Senha exige apenas mínimo de oito pontos de código Unicode, inclusive permitindo espaços; sem regra de composição. Pesquisa textual por trecho sem distinção de caixa, com `%` e `_` literais; entidade por igualdade sem distinção de caixa. Filtros null/vazios não restringem; `ativa=null` inclui ambas situações. Nenhuma dessas escolhas acrescenta campos de negócio ao Word.

## Migração e base

V3 `V3__criar_representantes.sql`, tabela `representantes`: `id`, `nome_completo`, `entidade_publica`, `login`, `senha_hash`, `ativa`, `troca_senha_obrigatoria`, `senha_temporaria_expira_em`. Expiração é `Instant`/PostgreSQL `TIMESTAMPTZ`. V3 depende de V2 já presente na branch de áreas; PRs empilhados mantêm essa dependência visível. Não reescrever V1/V2 nem habilitar `out-of-order`.

## Aceite

- Testes de conteúdo obrigatório, alteração atômica e senha mínima sem composição/máximo inventados.
- Hash no PostgreSQL diferente da original e verificável pelo encoder; ausência de getter público de hash/original.
- Duplicidade em cadastro e correção rejeitada, inclusive se a outra conta estiver desativada; restrição única no banco para concorrência.
- Desativação/reativação e correções mantêm identidade; redefinição substitui hash, renova 24h e exige troca.
- Pesquisa combina OR de nome/login e AND dos filtros, sem expor hash.
- Pelo menos um teste de gravação/edição confirma transação própria do serviço, sem transação de teste mascarando commit.
- Migrações e exportação SQL verificadas em PostgreSQL; CI aprovado; revisão independente de código sem alegar aprovação humana.

## Próximas integrações, ainda a fazer

Autenticação de representantes e troca inicial do RF005; autorização exclusiva do administrador; auditoria dos atos; telas protegidas de RF004. O acesso de moradores e envio de credenciais dependem dos respectivos cadastros e de decisão do provedor, sem tratar mocks como entrega real.
