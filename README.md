# Sistema de Alertas Comunitários

Projeto acadêmico. Em 21/11/2026, Pedro assumiu a continuidade sem o colega, com apoio de IA e subagentes. O fluxo atual é [desenvolvimento orientado por especificação](docs/SDD.md). Base em Java 21, Spring Boot 4.1.2, Spring MVC/Thymeleaf, Spring Data JPA, PostgreSQL 17 e Flyway; Maven Wrapper versionado.

**Estado da branch de desenvolvimento (21/11/2026):** infraestrutura e bases parciais de áreas e representantes implementadas, com 67 testes. Os 13 RF completos continuam em desenvolvimento. A página inicial é apenas um marcador; telas protegidas e fluxos completos ainda serão integrados. Consulte [o estado das entregas](docs/SDD.md).

## Começar no seu computador

Instale Git, JDK 21 e Docker Desktop com Docker Compose. Abra o Docker Desktop antes de começar. Cada integrante usa seu próprio clone e seu próprio banco local; não compartilhem a pasta de código por Drive/OneDrive nem o arquivo `.env`.

Repositório público: <https://github.com/Pedro3495/sistema-alertas-comunitarios>.

Cada integrante clona no próprio computador:

```sh
git clone https://github.com/Pedro3495/sistema-alertas-comunitarios.git
cd sistema-alertas-comunitarios
```

Depois execute na raiz:

**macOS / Linux**

```sh
./scripts/preparar.sh
./scripts/iniciar.sh
```

**Windows PowerShell** (com JDK 21 em `JAVA_HOME`)

```powershell
./scripts/preparar.ps1
./scripts/iniciar.ps1
```

A preparação cria `.env` com senha aleatória local, sobe somente o PostgreSQL, aplica a migração inicial e compila/testa a aplicação. Não instala ferramentas globalmente e não cria usuários do sistema.

Abra <http://localhost:8080>. Diagnóstico: <http://localhost:8080/actuator/health>. Para encerrar o servidor, use Ctrl+C. `docker compose stop db` para o banco sem apagar seus dados. Se uma porta estiver ocupada, ajuste `POSTGRES_PORT` ou `SERVER_PORT` no `.env` antes de iniciar.

## Trabalhar em dupla com IA

1. Escolham uma tarefa em `docs/tarefas/BACKLOG.md`. Registrem responsável, revisor e branch na issue ou cartão antes de começar.
2. Atualizem a `main` e criem uma branch de tarefa. Cada IA trabalha apenas na pasta e branch do seu responsável.
3. Implementem a spec, rodem `./scripts/verificar.sh` ou `./scripts/verificar.ps1` e abram um pull request. O colega revisa e só então integra.

O passo a passo e os comandos estão em [Fluxo de trabalho](docs/FLUXO_DE_TRABALHO.md). As instruções comuns às IAs estão em [AGENTS.md](AGENTS.md). Usem [o prompt de tarefa](docs/PROMPT_PARA_IA.md) para começar cada execução.

## Onde consultar

- [PRD](prd.md), [arquitetura](arquitetura.md) e [exigências do AV03](docs/AV03.md).
- [Specs de implementação](specs/README.md) e [transcrições dos requisitos](docs/requisitos/).
- Documento aprovado preservado em `docs/referencias/Requisitos_Aprovados.docx`, identificado por SHA-256 em `docs/referencias/manifesto.json`.
- Protótipos editáveis e prints em `design/`; exemplos de POO a documentar em [docs/POO.md](docs/POO.md).
- Script de banco em `database/schema.sql`; migrações em `src/main/resources/db/migration/`.

## Conferência

```sh
docker compose up -d --wait db
./scripts/verificar.sh
```

O CI repete a compilação e os testes com PostgreSQL novo em cada PR. O teste inicial verifica conexão real, migração, página HTTP e bloqueio de rota não autorizada; não comprova nenhum RF concluído.

Nenhum provedor de SMS/WhatsApp, deploy público da aplicação ou serviço pago está configurado. Essas integrações entram nas tarefas correspondentes, com escolha técnica registrada e testes reais antes de serem declaradas prontas.
