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
