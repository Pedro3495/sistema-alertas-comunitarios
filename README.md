# Sistema de Alertas Comunitários

Projeto acadêmico. Em 21/11/2026, Pedro assumiu a continuidade sem o colega, com apoio de IA e subagentes. O fluxo atual é [desenvolvimento orientado por especificação](docs/SDD.md). Base em Java 21, Spring Boot 4.1.2, Spring MVC/Thymeleaf, Spring Data JPA, PostgreSQL 17 e Flyway; Maven Wrapper versionado.

**Envio de SMS/WhatsApp é SIMULADO.** Nenhuma mensagem real sai do sistema: senha temporária, código de recuperação, alertas e avisos passam por implementações simuladas, sem rede e sem provedor contratado.

## Como rodar (do zero)

Pré-requisitos: Git, JDK 21 e Docker Desktop **aberto** (com Docker Compose).

```sh
git clone https://github.com/Pedro3495/sistema-alertas-comunitarios.git
cd sistema-alertas-comunitarios
./scripts/preparar.sh                      # cria .env com senha aleatória, sobe o PostgreSQL, compila e roda os testes
./scripts/configurar-admin.sh criar        # cria o único administrador; login e senha são pedidos no terminal
ALERTAS_ENVIO_SIMULADO_EXIBIR=true ./scripts/iniciar.sh   # sobe em http://localhost:8080 com a caixa do envio simulado
```

Windows PowerShell (JDK 21 em `JAVA_HOME`):

```powershell
./scripts/preparar.ps1
.\mvnw.cmd -q -DskipTests package
java -jar target/alertas-comunitarios-0.0.1-SNAPSHOT.jar --spring.main.web-application-type=none --alertas.admin=criar
$env:ALERTAS_ENVIO_SIMULADO_EXIBIR="true"; ./scripts/iniciar.ps1
```

Abra <http://localhost:8080>. Se a porta 8080 ou 55432 estiver ocupada, ajuste `SERVER_PORT` ou `POSTGRES_PORT` no `.env`. Ctrl+C encerra o servidor; `docker compose stop db` para o banco sem apagar dados.

### Roteiro de demonstração

1. Administrador (`/admin/entrar`): cadastre um representante com senha temporária.
2. Representante (`/entrar`): troque a senha no primeiro acesso; cadastre área de risco, residência e morador.
3. O morador "recebe" a senha temporária pelo envio simulado. Com `ALERTAS_ENVIO_SIMULADO_EXIBIR=true`, o administrador vê a mensagem em **Envio simulado** (`/admin/simulador/mensagens`, só em memória; reiniciar apaga). Sem a variável, a página não existe (404) e a senha não aparece em lugar nenhum.
4. Morador (`/morador/entrar`, CPF + senha): troca a senha, registra ocorrência com foto, consulta alertas e muda preferências. "Esqueci minha senha" envia um código, também visível na caixa simulada.
5. Representante: consulta, verifica, emite alerta (simulado), altera e encerra a ocorrência; o morador vê alerta e avisos em "Alertas recebidos". Administrador e representante consultam a auditoria.

Use apenas dados fictícios. O administrador esquecido é recuperado com `./scripts/configurar-admin.sh recuperar` ([detalhes](docs/administracao/README.md)).

### Implantação com HTTPS (RS009)

Localmente a aplicação roda em HTTP no loopback. Numa implantação real, publique-a somente atrás de HTTPS: um proxy reverso (por exemplo Nginx ou Caddy) com certificado TLS encaminhando para a porta da aplicação, e `server.forward-headers-strategy=native` para o Spring reconhecer o esquema original. Não exponha a porta HTTP nem a do PostgreSQL. Isso não foi configurado nem testado nesta entrega.

## Desenvolver com IA

1. Escolha a issue e leia a spec e o contrato SDD da entrega. Registre responsável e branch.
2. Trabalhe em branch própria; subagentes usam worktrees separados. Não sobrescreva arquivos de outra execução.
3. Integre o recorte, execute a verificação com PostgreSQL, revise e abra PR. Merge exige autorização e as proteções remotas.

O fluxo atual está em [SDD](docs/SDD.md) e as instruções comuns em [AGENTS.md](AGENTS.md). O [guia da dupla](docs/FLUXO_DE_TRABALHO.md) preserva a organização inicial e informa a mudança de responsável.

## Documentação da entrega

- [PRD](prd.md) e [arquitetura](arquitetura.md) (camadas, pacotes, Spring/JPA/PostgreSQL/Flyway, portas simuladas).
- [Evidências de POO](docs/POO.md) com arquivo:linha para encapsulamento, abstração, herança e polimorfismo.
- [Specs por RF](specs/README.md), [contratos de entrega](specs/entregas/) e [requisitos transcritos](docs/requisitos/).
- Banco: [estrutura exportada](database/schema.sql), [contas e permissões](database/contas-permissoes.sql), [documento do banco](docs/entrega-banco-de-dados.md) e migrações em `src/main/resources/db/migration/`.
- [Skills e instruções de IA usadas](docs/SKILLS.md), [fluxo SDD](docs/SDD.md) e [exigências do AV03](docs/AV03.md).
- Documento aprovado preservado em `docs/referencias/Requisitos_Aprovados.docx` (SHA-256 em `docs/referencias/manifesto.json`); protótipos em `design/`.

## Conferência

```sh
docker compose up -d --wait db
./scripts/verificar.sh
```

Os testes exigem um banco **sem administrador nem dados de demonstração** e se recusam a rodar no banco usado para demonstrar. Para conferir depois de demonstrar, use outro banco: `COMPOSE_PROJECT_NAME=verificacao POSTGRES_PORT=55433 docker compose up -d --wait db` e rode o script com as mesmas duas variáveis exportadas. O CI repete a compilação e os testes com PostgreSQL novo em cada PR.

Nenhum provedor de SMS/WhatsApp, deploy público ou serviço pago está configurado. `EnvioCredenciaisSimulado` e `EnvioAlertasSimulado` não usam rede; envio simulado não é envio real.
