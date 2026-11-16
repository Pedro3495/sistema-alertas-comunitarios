# Estado do ambiente — 16/11/2026

## Preparado

- Repositório Git local com branch main e cópia preservada do documento aprovado.
- Repositório público criado: https://github.com/Pedro3495/sistema-alertas-comunitarios.
- Java 21, Spring Boot 4.1.2, PostgreSQL 17, JPA, Flyway e Thymeleaf configurados.
- PostgreSQL local em Docker; senha gerada no .env ignorado pelo Git.
- Scripts de preparação, execução e verificação para macOS/Linux e PowerShell.
- PRD, arquitetura, 13 specs, requisitos transcritos, guia da dupla e instruções compartilhadas para IAs.
- Workflow de CI e templates de issue e PR preparados para o GitHub.
- Três testes de infraestrutura passando localmente; HTTP 200 e health UP verificados.

## Pendente

- Usuário GitHub do colega e liberação de acesso. O responsável optou por adicioná-lo depois, nas configurações do repositório, com permissão de escrita.
- Conferir o último resultado do CI na aba Actions e a proteção da main em Settings → Branches. Evidências da configuração inicial serão registradas abaixo após a verificação remota.
- Preparação no computador do colega e teste dos scripts PowerShell em Windows, se esse for o sistema usado por ele.
- Implementação dos 13 RF e suas regras transversais. O ambiente não declara sete funcionalidades operacionais.

Atualizar este arquivo quando as pendências forem resolvidas. Não marcar publicação, convite ou proteção de branch como concluídos sem conferir o estado remoto.
