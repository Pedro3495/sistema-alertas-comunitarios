# Sistema de Alertas Comunitários

Projeto acadêmico. Em 21/11/2026, Pedro assumiu a continuidade sem o colega, com apoio de IA e subagentes. O fluxo atual é [desenvolvimento orientado por especificação](docs/SDD.md). Base em Java 21, Spring Boot 4.1.2, Spring MVC/Thymeleaf, Spring Data JPA, PostgreSQL 17 e Flyway; Maven Wrapper versionado.

**Estado da branch de desenvolvimento (22/11/2026):** acesso de representantes, gestão administrativa com auditoria inicial e gestão web de áreas/residências implementados, com 136 testes. Os PRs aguardam revisão e integração; os 13 RF não estão concluídos. Configure o primeiro administrador pelo [comando local](docs/administracao/README.md) e consulte [os limites da entrega atual](docs/residencias/README.md).

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

## Desenvolver com IA

1. Escolha a issue e leia a spec e o contrato SDD da entrega. Registre responsável e branch.
2. Trabalhe em branch própria; subagentes usam worktrees separados. Não sobrescreva arquivos de outra execução.
3. Integre o recorte, execute a verificação com PostgreSQL, revise e abra PR. Merge exige autorização e as proteções remotas.

O fluxo atual está em [SDD](docs/SDD.md) e as instruções comuns em [AGENTS.md](AGENTS.md). O [guia da dupla](docs/FLUXO_DE_TRABALHO.md) preserva a organização inicial e informa a mudança de responsável.

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
