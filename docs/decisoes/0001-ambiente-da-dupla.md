# ADR 0001 — Ambiente de desenvolvimento da dupla

Data: 16/11/2026.

Estado: stack e publicação pública aprovadas pelo responsável; preparação local verificada. O acesso do colega será adicionado depois pelo responsável.

## Contexto

Dois integrantes desenvolverão usando IA. O AV03 exige orientação a objetos, banco, documentação Markdown, specs e repositório público. A equipe aprovou Java + Spring Boot + PostgreSQL.

## Decisão

Manter uma aplicação Java 21/Spring Boot MVC com Thymeleaf e JPA. Banco PostgreSQL 17 local por computador, Docker Compose, migrações Flyway e Maven Wrapper versionado. Uma cópia/branch por responsável, tarefa rastreada a RF e revisão pelo colega antes de integrar na main. Centralizar instruções das IAs em AGENTS.md.

## Verificação da preparação

No computador inicial: compilação/empacotamento Maven concluídos; três testes de infraestrutura com PostgreSQL real passaram; página inicial respondeu HTTP 200 e health respondeu UP. Isso não equivale a qualquer RF implementado.

## Consequências

Os dois precisam de JDK 21, Git e Docker. Não há necessidade de Node.js ou servidor frontend separado. Portas e credenciais locais ficam em .env não versionado. Alterações em contratos comuns e migrações exigem coordenação. Os modelos de PRD/arquitetura citados pelo professor em sala ainda devem ser comparados se ele exigir reprodução exata do formato.
