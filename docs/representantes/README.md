# Base de representantes — RF004, tarefa #15

Entrega parcial guiada por [contrato SDD](../../specs/entregas/RF004-base.md). O domínio, a persistência e os testes estão implementados. Autorização do administrador, autenticação e primeira troca de senha, auditoria e telas protegidas ainda serão integradas; RF004/RF005 não estão completos.

## Operações implementadas

`RepresentanteService` oferece cadastro, correção de nome/entidade/login, desativação, reativação, redefinição de temporária e pesquisa. A identidade numérica não muda na manutenção. Não há exclusão no serviço: desativar mantém o registro e o login reservado àquela conta, incluindo contas inativas.

O login atual é único por igualdade exata, garantida também por restrição no PostgreSQL. Não foi criada uma tabela de aliases ou reserva histórica dos logins corrigidos: essa política não está definida no requisito. Campos textuais usam `TEXT`, sem impor máximo adicional de caracteres.

## Senhas e tempo

A senha recebida pelo serviço tem no mínimo oito pontos de código Unicode, sem composição ou máximo adicional. Espaços não são removidos nem proibidos por regra implícita. O banco armazena somente hash PBKDF2 com sal, usando o `PasswordEncoder` já disponível no Spring Security. A entidade não tem getter público de senha ou hash nem `toString` que exponha credenciais.

Criação e redefinição registram a troca obrigatória e expiração da temporária 24 horas após a operação. `Clock` é injetado para permitir testes determinísticos sem congelar o horário da aplicação real. Esta base registra a validade; bloquear autenticação após vencimento e executar a primeira troca pertencem à integração RF005, não estão simulados aqui.

O serviço recebe a senha temporária do chamador. Não implementa envio de SMS/WhatsApp, consulta da senha antiga, conta administrativa fixa ou tela de entrega direta. A operação administrativa completa terá autorização e auditoria antes de ser disponibilizada por HTTP.

## Pesquisa

O termo pesquisa trecho do nome OU login, sem distinção de caixa. Os filtros de entidade (igualdade sem distinção de caixa) e situação são combinados com AND. Filtros vazios/null não restringem e situação null inclui ativas/desativadas. `%`, `_` e barra invertida são literais. Não há projeção pública de senha/hash.

## Evidências e apresentação

- 43 testes de representantes com PostgreSQL real: obrigatórios, alteração atômica, login duplicado inclusive inativo, reativação, hash com sal, senha longa Unicode sem truncamento, 24h/reset, filtros e restrição única no banco.
- Um teste desliga a transação externa do teste e verifica que o próprio serviço confirma cadastro e correção; os registros criados são removidos ao final.
- 67 testes no conjunto com infraestrutura e áreas. A exportação SQL foi restaurada em banco temporário vazio com as duas tabelas.
- Produção e testes foram preparados por subagentes em worktrees separados, a partir do mesmo contrato. A integração e os testes foram executados pelo coordenador. Uma revisão estática independente não apontou achados acionáveis no recorte; isso não equivale a aprovação humana de PR.

Para explicar POO: mostre os campos privados e operações que preservam invariantes em `Representante`; o serviço coordena transações e hash; o repositório abstrai persistência. `PasswordEncoder` e `Clock` são contratos reais de dependências substituíveis nos testes. Não apresentar herança de framework como prova de herança de negócio já implementada.

## Migrações e execução

V3 `V3__criar_representantes.sql` segue a V2 de áreas. Enquanto V2 estiver no PR #16 sem merge, o PR de representantes usa a branch de áreas como base. Antes de integrar em main, preservar a ordem V2 → V3 e conferir CI. Não editar versões aplicadas nem habilitar out-of-order.

```sh
docker compose up -d --wait db
./scripts/verificar.sh
```

Usar a branch da entrega ao executar contra um banco que já recebeu V3; a main anterior ainda não contém essa migração. Não apagar volumes para esconder divergências de versão.
