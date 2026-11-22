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

## Acesso de moradores — RF005

`BloqueioLogin` é um objeto embutido (`@Embeddable`) que encapsula contador e prazo de bloqueio: `Morador`, `Representante` e `Administrador` o **compõem** em vez de repetir a regra das 5 falhas/15 minutos, sem forçar herança entre contas diferentes. `SessaoValidadaFilter<T>` é um filtro genérico: cada chain informa o tipo de principal e a função que revalida a sessão (`AutenticacaoMoradorService::validarSessao` ou a do representante), um exemplo de abstração por parâmetro de tipo e referência de método. `MoradorAuthenticationProvider` implementa o mesmo contrato `AuthenticationProvider` do representante (polimorfismo de integração).

## Gestão e auditoria

A fachada `GestaoRepresentantesService` aplica autorização e transação do caso de uso; domínio/persistência não dependem de formulários. `AuditoriaService` exige transação existente para que histórico e manutenção sejam confirmados ou revertidos juntos. `RegistroAuditoria` não expõe setters e é mapeado como imutável; a interface oferece somente consulta. Os principals distintos impedem confundir a identidade do administrador com a do representante quando IDs ou logins coincidem.

## Residências e áreas

`Residencia` valida o endereço completo antes de alterar seus campos privados e mantém uma única referência de área. `ResidenciaService` coordena autorização, persistência e auditoria. `GestaoAreasService` acrescenta as regras da operação autenticada à base de áreas sem colocar decisões de autorização no formulário. Locks e FKs preservam os vínculos em transações concorrentes; a interface de consulta do repositório abstrai o mecanismo de persistência.

## Base de moradores

`Morador` mantém campos privados e valida nome, verificadores do CPF, celular e residência antes de aplicar uma correção. Enums distintos impedem confundir o canal único de credenciais com a preferência de alertas. `MoradorService` coordena autorização, transação e auditoria; a FK e as restrições únicas protegem vínculos e identidade também nas disputas concorrentes. Não há herança artificial; `MoradorTests` e `MoradorServiceTests` verificam essas regras.

## Registro de ocorrências por representantes

`Ocorrencia` valida o registro e compõe de uma a quatro `FotoOcorrencia`; mantém campos privados e não oferece remoção/alteração das evidências. `FotoOcorrencia` copia os bytes na entrada e na saída. `OcorrenciaService` reúne autorização, bloqueio/revalidação da sessão, persistência e auditoria; o chamador pede um registro sem controlar protocolo, instante ou responsável. `ErroOcorrencia` especializa `IllegalArgumentException` para informar o campo inválido ao HTTP. Isso demonstra encapsulamento, composição e abstração; não é uma hierarquia de entidades criada para preencher o critério de herança.

## Envio simulado de credenciais

`EnvioCredenciais` é uma porta (interface) com contrato útil: `MoradorService` depende apenas dela para entregar a senha temporária, sem conhecer o provedor. `EnvioCredenciaisSimulado` é a única implementação nesta entrega, sem rede; um provedor real poderá substituí-la por polimorfismo sem mudar o caso de uso. `Morador` encapsula o hash, a validade e a pendência de envio (`emitirSenhaTemporaria`, `registrarEnvio`), sem expor a senha original.

## Recuperação de acesso do morador — RF007

`CodigoRecuperacao` é outro objeto embutido em `Morador`, ao lado de `BloqueioLogin`: encapsula hash, prazo de 10 minutos e contador de erros, e só expõe `emitir`, `conferir` e `invalidar`, de modo que nenhum chamador consegue deixar um código vencido ou esgotado utilizável. `Morador.redefinirSenhaComCodigo` combina essa regra com a de senha definitiva já existente. O mesmo contrato `EnvioCredenciais` ganhou `enviarCodigoRecuperacao`; o caso de uso continua sem conhecer o provedor (simulado).

## Verificação de ocorrências — RF009

`Ocorrencia.verificar` é o único caminho para mudar a situação para Confirmada ou Não confirmada: recusa ocorrência encerrada, outra situação, a mesma decisão repetida e justificativa vazia, e devolve a `VerificacaoOcorrencia` imutável (`@Immutable`) que guarda justificativa, representante e instante. O campo `situacao` continua privado e sem setter. `OcorrenciaService.verificar` só autoriza, bloqueia a linha, persiste e audita.

## Alertas — RF010

`EnvioAlertas` é uma porta separada de `EnvioCredenciais` porque as regras diferem (RF010 regra 5: só alertas têm "ambos" e alternativa por falha); `EnvioAlertasSimulado` é a única implementação, sem rede. `Comunicado` encapsula o alerta e o aviso de alteração: as fábricas `alerta` (exige ocorrência Confirmada e os três textos) e `alteracao` são os únicos construtores, não há setters, e `distribuir` só alcança destinatários pendentes. `DestinatarioComunicado.tentar` decide os canais pela preferência do morador, com alternativa automática, e guarda se ao menos um canal aceitou.

## Alteração de ocorrências — RF011

`Ocorrencia.alterar` é o único caminho para corrigir tipo, especificação de Outros, descrição, localização e área: reaproveita as validações do registro (`validar`, compartilhado com o construtor), recusa ocorrência encerrada, justificativa vazia e alteração sem mudança, e devolve a `AlteracaoOcorrencia` imutável com justificativa interna e o resumo campo a campo. Fotos, autor e protocolo não têm caminho de mudança. `OcorrenciaService.alterar` autoriza, bloqueia a linha, persiste, audita e reaproveita `AlertaService.avisarAlteracao` do RF010.

## Encerramento de ocorrências — RF012

`Ocorrencia.encerrar` é o único caminho para a situação Encerrada: exige justificativa, recusa novo encerramento e devolve o `EncerramentoOcorrencia` imutável (um por ocorrência, `UNIQUE` na V17). Como `verificar` e `alterar` já recusam Encerrada e `Comunicado.alerta` exige Confirmada, não há reabertura nem novas alterações. O aviso reaproveita `Comunicado` com a fábrica `encerramento` (exige o resumo do motivo) e o mesmo critério de destinatários do aviso de alteração em `AlertaService`; o tipo `ENCERRAMENTO` é só mais um valor de `TipoComunicado`, sem nova hierarquia.
