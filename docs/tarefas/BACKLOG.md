# Backlog inicial e sequência de integração

Planejamento técnico proposto para a dupla, sem reduzir os 13 RF aprovados. Todos os RF começam **a fazer**. Antes de começar, escolher o responsável e o revisor; os nomes ainda não foram informados. Use as issues/PRs como registro de trabalho em andamento; este arquivo é a referência inicial.

| Etapa | Tarefas candidatas | Coordenação |
|---|---|---|
| 0 | Ambiente de execução e colaboração | Base preparada; não conta como RF |
| 1 | RF003 — áreas; RF004 — representantes | Duas tarefas independentes; coordenar auditoria e migrações |
| 2 | RF002 — residências; acesso de representantes do RF005 | RF005 ainda parcial até integrar o acesso de moradores |
| 3 | RF001 — moradores; RF013 — auditoria | Fechar credenciais e autenticação; o envio de credenciais precisa integração, não mock declarado pronto |
| 4 | RF006 — registro; RF008 — consultas | Depende de cadastros, acesso, regras regionais e auditoria |
| 5 | RF009 — verificação; RF007 — recuperação | Coordenar mudanças em acesso e domínio da ocorrência |
| 6 | RF010 — alertas e distribuição | Decidir provedor e verificar canais, falhas e reenvio |
| 7 | RF011 — alterações; RF012 — encerramento | Contrato de avisos e auditoria compartilhado; evitar edição concorrente |

A avaliação exige no mínimo sete funcionalidades completas. Não selecionar sete telas aleatórias: os fluxos precisam de persistência, regras, segurança e dependências integradas. Os 13 RF continuam no escopo aprovado.

## Cartão de tarefa

- RF/spec e recorte do PR.
- Responsável humano e revisor humano.
- Branch e link de PR.
- Critérios de aceite retirados da spec.
- Arquivos/contratos compartilhados e número de migração reservado.
- Comandos/testes e roteiro de demonstração.

## Primeira sessão da dupla

1. Rodar a preparação nos dois computadores e conferir a página inicial e os testes.
2. Ler os requisitos RF003/RF004 juntos; combinar uma tarefa para cada pessoa.
3. Modelar o mínimo necessário e reservar números de migração distintos antes de pedir execução às IAs.
4. Fazer PRs pequenos, revisar o trabalho um do outro e explicar as escolhas de POO.
