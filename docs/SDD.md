# Desenvolvimento orientado por especificação (SDD)

SDD aqui é um fluxo de trabalho, sem instalar um framework ou acrescentar uma ferramenta: partir dos RF aprovados, definir um recorte verificável, implementar e testar o mesmo contrato, revisar e abrir PR.

## Fontes

`docs/referencias/Requisitos_Aprovados.docx` é a fonte preservada. `specs/RFxxx.md` registra a transcrição e o andamento do requisito. `specs/entregas/` contém contratos técnicos pequenos para cada PR, sem inventar novas regras de negócio nem alterar o Word.

## Ciclo

1. Escolher a issue e ler RF, RNF e RS aplicáveis.
2. Registrar escopo, contratos públicos, migrações, critérios de aceite e dependências. Distinguir regra aprovada de escolha de implementação.
3. Distribuir produção e testes entre worktrees separados, quando útil. Cada agente recebe arquivos permitidos, contrato e critério de término.
4. Integrar os commits na branch da tarefa; executar testes com PostgreSQL e conferir o script de banco.
5. Revisar implementação e diff de forma independente, corrigir achados e conferir CI.
6. Atualizar tarefa/spec com o resultado real. Abrir PR; não marcar um RF completo se apenas parte foi implementada.

## Continuidade sem o colega

Pedro informou em 21/11/2026 que o colega não continuará. A execução das tarefas passa a ser coordenada aqui, com responsabilidade de Pedro. O quadro do GitHub continua sendo o acompanhamento; o histórico das tarefas anteriores não constitui obrigação de esperar pelo colega ausente.

O PR #16 contém V2/áreas e permanece sem merge. A base de representantes #15/V3 começa sobre essa branch, e seu PR aponta para a branch de áreas enquanto a dependência não estiver integrada. Isso permite revisar diferenças pequenas e continuar o desenvolvimento sem fingir que V2 já está na main. Antes de integrar em main, respeitar a ordem V2 → V3, repetir testes e conferir as proteções remotas. Não habilitar Flyway out-of-order.

Revisão independente de subagente é evidência de revisão técnica, não aprovação humana de PR. As configurações de proteção da main permanecem ativas; merge e deploy não são consequência automática de implementar uma tarefa.

## Plano de retomada

| Entrega | Fonte | Estado de partida |
|---|---|---|
| Base de áreas, cadastro/alteração/pesquisa | RF003 / #14 | PR #16 em revisão |
| Base de representantes, credenciais protegidas e manutenção | RF004 / #15 | Implementação parcial e 43 testes de representantes prontos; 67 no conjunto. Aguardando revisão/integração; contrato em `specs/entregas/RF004-base.md` |
| Autenticação de representantes e troca inicial | RF005 + RF004 | A fazer; definir contrato antes de editar segurança |
| Autorização do administrador, auditoria e telas protegidas | RF004 + RF013 | A fazer; sem usuário administrador fixo criado por conveniência |
| Cadastros e acesso de moradores, ocorrências e alertas | RF001–RF013, conforme dependências | Continuam no escopo aprovado, sem funções fictícias ou mocks declarados prontos |

Não criar datas de entrega ou metas técnicas que não foram informadas. Solicitar decisão de Pedro quando faltar uma regra indispensável; continuar o trabalho que não depende da resposta.
