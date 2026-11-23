# Arquitetura do sistema

## Decisão técnica inicial

Java 21 + Spring Boot + PostgreSQL escolhidos pelo responsável em 16/11/2026. Aplicação web responsiva em um único projeto, usando Spring MVC e Thymeleaf; persistência com Spring Data JPA e migrações SQL com Flyway. Banco separado em container Docker local, um por desenvolvedor.

Usar o mesmo projeto reduz a quantidade de processos, contratos e ferramentas necessários para a dupla. O AV03 permite essa stack, mas não a impõe. O exemplo de arquitetura citado como apresentado em sala não foi fornecido; esta é a decisão inicial da equipe.

## Organização

Um pacote por funcionalidade; em cada um, controller (HTTP), serviço (caso de uso e transação), entidade de domínio (regras) e repositório Spring Data JPA (persistência).

```text
src/main/java/br/edu/alertas/
  config/          SegurancaConfig: três cadeias Spring Security (admin, morador, representante), CSRF ativo
  administracao/   conta única de administrador e comando local configurar-admin
  gestao/          telas do administrador: representantes e caixa do envio simulado
  representantes/  conta, senha temporária/definitiva e bloqueio de login
  acesso/          login, troca de senha e recuperação; providers e filtro de sessão
  areas/           áreas de risco (RF003)
  residencias/     residências vinculadas a uma área (RF002)
  moradores/       moradores, credenciais, preferências e porta EnvioCredenciais (RF001, RF005, RF007)
  ocorrencias/     registro com fotos, consulta, verificação, alertas, alteração e encerramento (RF006, RF008–RF012), porta EnvioAlertas
  auditoria/       registro imutável e consulta (RF013)
  inicio/          página inicial com os três acessos
src/main/resources/
  templates/       páginas Thymeleaf por funcionalidade
  static/          CSS e JS (rascunho/redução de fotos das ocorrências)
  db/migration/    migrações Flyway V1–V19
database/          schema.sql (exportação da estrutura) e contas-permissoes.sql
```

## Fluxo de uma operação

HTTP → controller → serviço do caso de uso → domínio/repositório → PostgreSQL → página de resposta.

Controller recebe dados e apresenta respostas. Serviço delimita transações e coordena o caso de uso. Objetos de domínio protegem invariantes. Repositórios consultam e persistem. A sessão de persistência não fica aberta durante a renderização (`open-in-view=false`). Hibernate valida o esquema; Flyway é responsável por criá-lo/evoluí-lo.

## POO e integrações

Modelos com comportamento e invariantes; exemplos com arquivo:linha em `docs/POO.md`. SMS e WhatsApp passam por duas portas (`EnvioCredenciais` e `EnvioAlertas`) com uma única implementação **simulada**, sem rede: nenhuma mensagem real é enviada. Para demonstração local, `alertas.envio-simulado.exibir=true` mostra ao administrador, em `/admin/simulador/mensagens`, as senhas temporárias e códigos que seriam enviados (só em memória; nunca em log, banco ou auditoria). Um provedor real substituirá a implementação sem mudar os serviços.

## Segurança e dados

Três cadeias isoladas em `SegurancaConfig`: `/admin/**` (administrador), `/morador/**` (morador, login por CPF) e o restante (representante). Senha temporária só dá acesso à troca de senha; sessões são revalidadas por conta e versão da credencial; bloqueio após falhas repetidas. Rotas não listadas são negadas. CSRF ativo em todos os formulários. Templates usam apenas `th:text` (escape automático); não há `th:utext`.

HTTPS (RS009) é responsabilidade da implantação: a aplicação local roda em HTTP no loopback. Veja "Implantação com HTTPS" no README.

Credenciais do PostgreSQL ficam em `.env` local ignorado pelo Git; CI usa banco descartável próprio. Usar apenas dados fictícios.

## Banco e mudanças compartilhadas

Combinar os números de migração antes de tarefas paralelas. Nunca editar uma migração já integrada/aplicada; adicionar a próxima. Para o AV03, manter a exportação de estrutura em `database/schema.sql`, sem dados pessoais ou senhas.

## Fontes técnicas consultadas

- https://docs.spring.io/spring-boot/system-requirements.html
- https://start.spring.io/metadata/client
- https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml
- https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven

Versão do Spring Boot fixada em `pom.xml`; o build resolve a versão publicada `4.1.2`, sem o sufixo `.RELEASE` retornado pelo gerador.
