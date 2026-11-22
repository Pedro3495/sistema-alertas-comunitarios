# Evidências de orientação a objetos

POO representa 60% da avaliação. As evidências abaixo acompanham os objetos de negócio implementados; isso não significa que os quatro pilares ou os RF completos já estejam entregues.

| Pilar | O que demonstrar | Classe/método e teste reais |
|---|---|---|
| Encapsulamento | Estado e invariantes protegidos por operações válidas | `AreaRisco`: campos privados, sem setters públicos; `alterarDefinicao` valida os dois campos antes de modificar o objeto. `AreaRiscoTests` verifica rejeição de campos vazios e preservação do estado na alteração inválida. |
| Abstração | Contrato que expõe uma operação sem acoplar detalhes | `AreaRiscoService` expõe cadastro, alteração e pesquisa sem exigir SQL do chamador. `AreaRiscoRepository` abstrai o acesso ao PostgreSQL por JPA; não é uma interface artificial criada para cada classe. Os testes de persistência verificam o resultado gravado. |
| Herança | Relação de substituição válida, com comportamento compreendido | Pendente; não forçar hierarquia para cumprir tabela |
| Polimorfismo | Mesmo contrato com comportamentos substituíveis | Pendente |

Expliquem por que cada escolha ajuda o domínio. Anotações JPA ou estender uma classe do framework, por si só, não demonstram domínio dos quatro pilares. A escolha de onde usar herança deve ser discutida durante a modelagem, antes de construir uma hierarquia artificial.

O recorte de áreas não demonstra ainda herança ou polimorfismo de negócio. A herança da interface `JpaRepository` faz parte da infraestrutura e não deve ser apresentada como se os quatro pilares estivessem resolvidos. Veja [o guia da base de áreas](areas/README.md).

## Base de representantes

`Representante` protege seus campos e valida o cadastro antes de alterar o estado. Senha/hash não têm getter público. `RepresentanteService` coordena operações e transações, usando `RepresentanteRepository` para persistência, `PasswordEncoder` para hash e `Clock` para tempo. São dependências com contratos reais; o teste substitui o relógio e usa o encoder real para conferir hash e validade. Veja [o guia de representantes](representantes/README.md).

Isso acrescenta evidências de encapsulamento, abstração e substituição de dependências. Não foi inventada uma hierarquia de herança de domínio para preencher o critério acadêmico; essa parte permanece a demonstrar quando houver relação adequada na modelagem.

## Acesso de representantes — RF005 parcial

`Representante` encapsula contador, bloqueio, validade e versão: operações de domínio mantêm os estados coerentes sem setters públicos. O serviço coordena lock/transação e encoder; o controller não escolhe o ID da conta a partir do formulário. `AcessoRepresentante` carrega somente a identidade necessária à sessão.

`RepresentanteAuthenticationProvider` implementa o contrato `AuthenticationProvider`: o Spring chama a implementação de autenticação da aplicação pelo mesmo contrato usado por outros providers. É um exemplo real de polimorfismo de integração, sem inventar hierarquia de negócio. O filtro herda `OncePerRequestFilter` para participar do ciclo HTTP; isso é herança técnica do framework, não uma hierarquia do domínio.

## Gestão e auditoria

A fachada `GestaoRepresentantesService` aplica autorização e transação do caso de uso; domínio/persistência não dependem de formulários. `AuditoriaService` exige transação existente para que histórico e manutenção sejam confirmados ou revertidos juntos. `RegistroAuditoria` não expõe setters e é mapeado como imutável; a interface oferece somente consulta. Os principals distintos impedem confundir a identidade do administrador com a do representante quando IDs ou logins coincidem.

## Residências e áreas

`Residencia` valida o endereço completo antes de alterar seus campos privados e mantém uma única referência de área. `ResidenciaService` coordena autorização, persistência e auditoria. `GestaoAreasService` acrescenta as regras da operação autenticada à base de áreas sem colocar decisões de autorização no formulário. Locks e FKs preservam os vínculos em transações concorrentes; a interface de consulta do repositório abstrai o mecanismo de persistência.

## Base de moradores

`Morador` mantém campos privados e valida nome, verificadores do CPF, celular e residência antes de aplicar uma correção. Enums distintos impedem confundir o canal único de credenciais com a preferência de alertas. `MoradorService` coordena autorização, transação e auditoria; a FK e as restrições únicas protegem vínculos e identidade também nas disputas concorrentes. Não há herança artificial nem envio de credenciais nesta base; `MoradorTests` e `MoradorServiceTests` verificam essas regras.
