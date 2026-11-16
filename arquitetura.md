# Arquitetura do sistema

## Decisão técnica inicial

Java 21 + Spring Boot + PostgreSQL escolhidos pelo responsável em 16/11/2026. Aplicação web responsiva em um único projeto, usando Spring MVC e Thymeleaf; persistência com Spring Data JPA e migrações SQL com Flyway. Banco separado em container Docker local, um por desenvolvedor.

Usar o mesmo projeto reduz a quantidade de processos, contratos e ferramentas necessários para a dupla. O AV03 permite essa stack, mas não a impõe. O exemplo de arquitetura citado como apresentado em sala não foi fornecido; esta é a decisão inicial da equipe.

## Organização

```text
src/main/java/br/edu/alertas/
  config/          configuração transversal e segurança
  inicio/          página técnica inicial
  # Próximos pacotes serão criados por funcionalidade, conforme a spec:
  # moradores/, residencias/, areas/, representantes/, acesso/,
  # ocorrencias/, alertas/, auditoria/
src/main/resources/
  templates/       páginas Thymeleaf
  static/          CSS e recursos locais
  db/migration/    migrações Flyway versionadas
src/test/          testes das regras e da integração
database/          script SQL de entrega
specs/             escopo de implementação por RF
docs/              decisões, requisitos, guias e avaliação
```

Os pacotes comentados são planejados; não representam módulos já implementados.

## Fluxo de uma operação

HTTP → controller → serviço do caso de uso → domínio/repositório → PostgreSQL → página de resposta.

Controller recebe dados e apresenta respostas. Serviço delimita transações e coordena o caso de uso. Objetos de domínio protegem invariantes. Repositórios consultam e persistem. A sessão de persistência não fica aberta durante a renderização (`open-in-view=false`). Hibernate valida o esquema; Flyway é responsável por criá-lo/evoluí-lo.

## POO e integrações

Priorizar modelos com comportamento e invariantes, evitando entidades usadas só como listas de setters. Interfaces de integração podem permitir polimorfismo nos canais de envio e dublês em testes quando o RF010 for implementado. Não criar classes de envio que finjam sucesso real nem escolher provedor nesta base. Herança somente com relação de substituição válida; mapear as demonstrações reais em `docs/POO.md`.

## Segurança e dados

Na base inicial, somente `/`, `/css/**` e `/actuator/health` são públicos; as demais rotas são negadas. Não existe autenticação de moradores/representantes entregue ainda. CSRF permanece habilitado. A autorização de negócio será implementada com os RF004/RF005 e exercitada por operação, não apenas por visibilidade de botão.

Credenciais do PostgreSQL ficam em `.env` local ignorado pelo Git; CI usa banco descartável próprio. Não compartilhar banco entre os computadores, não publicar uploads, dumps com dados ou credenciais. Usar apenas dados fictícios nos testes e apresentações de desenvolvimento.

## Banco e mudanças compartilhadas

Combinar os números de migração antes de tarefas paralelas. Nunca editar uma migração já integrada/aplicada; adicionar a próxima. Para o AV03, manter a exportação de estrutura em `database/schema.sql`, sem dados pessoais ou senhas. Na preparação inicial há somente a versão técnica Flyway, ainda sem tabelas de domínio.

## Fontes técnicas consultadas

- https://docs.spring.io/spring-boot/system-requirements.html
- https://start.spring.io/metadata/client
- https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml
- https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven

Versão do Spring Boot fixada em `pom.xml`; o build resolve a versão publicada `4.1.2`, sem o sufixo `.RELEASE` retornado pelo gerador.
