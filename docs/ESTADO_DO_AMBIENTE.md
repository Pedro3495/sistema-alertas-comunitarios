# Estado do ambiente — 16/11/2026

## Preparado

- Repositório Git local com branch main e cópia preservada do documento aprovado.
- Java 21, Spring Boot 4.1.2, PostgreSQL 17, JPA, Flyway e Thymeleaf configurados.
- PostgreSQL local em Docker; senha gerada no .env ignorado pelo Git.
- Scripts de preparação, execução e verificação para macOS/Linux e PowerShell.
- PRD, arquitetura, 13 specs, requisitos transcritos, guia da dupla e instruções compartilhadas para IAs.
- Workflow de CI e templates de issue e PR preparados para o GitHub.
- Três testes de infraestrutura passando localmente; HTTP 200 e health UP verificados.

## Pendente

- Confirmação de publicação do repositório público no GitHub, conforme pergunta enviada ao responsável. O AV03 exige que a entrega final seja pública.
- Usuário GitHub do colega, liberação de acesso e proteção de main com revisão e CI obrigatórios.
- Execução do CI no GitHub após publicar; até lá o workflow está preparado, sem execução remota verificada.
- Preparação no computador do colega e teste dos scripts PowerShell em Windows, se esse for o sistema usado por ele.
- Implementação dos 13 RF e suas regras transversais. O ambiente não declara sete funcionalidades operacionais.

Atualizar este arquivo quando as pendências forem resolvidas. Não marcar publicação, convite ou proteção de branch como concluídos sem conferir o estado remoto.
