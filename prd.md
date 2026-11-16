# Sistema de Alertas Comunitários — PRD

## Objetivo

Permitir que moradores cadastrados comuniquem ocorrências e acompanhem relatos conforme sua área, e que representantes de entidades públicas verifiquem, emitam alertas, alterem e encerrem essas ocorrências. Contas de representantes são gerenciadas pelo administrador.

## Fontes

Requisitos aprovados: `docs/referencias/Requisitos_Aprovados.docx`, preservado sem alterações. Transcrições para consulta: `docs/requisitos/`. Critérios da avaliação: `docs/AV03.md`. Em caso de divergência de transcrição, consultar o original e o responsável pelo projeto.

Este PRD organiza o escopo já aprovado para desenvolvimento; não substitui o Word. O formato apresentado pelo professor em sala não foi fornecido; este é um PRD inicial para o repositório.

## Escopo

13 requisitos: cadastro de moradores; residências e áreas; áreas de risco; contas de representantes; autenticação e primeira senha; registro de ocorrências; recuperação de acesso do morador; consulta de ocorrências; verificação; emissão/distribuição de alertas; alteração/notificação; encerramento/notificação; auditoria.

Os campos, regras, alterações, exclusões e pesquisas de cada RF permanecem nas specs e transcrições. Os sete RNF e nove RS também integram o escopo, inclusive isolamento regional, preservação de fotos e registros, autorização por perfil e regras distintas de canais para alertas e credenciais.

## Regras que atravessam módulos

- Cadastro de moradores pelas entidades públicas, CPF e telefone exclusivos, residência vinculada a uma área e comprovante apenas conferido.
- Representante usa conta individual com login próprio; morador usa CPF e senha. Primeiro acesso exige trocar senha temporária. As limitações de senha, bloqueio e recuperação são as do documento.
- Ocorrência exige de uma a quatro fotos, até 5 MB por foto, nos formatos aprovados. Fotos enviadas não podem ser removidas/substituídas. O relato enviado não pode ser alterado pelo morador nem apagado por qualquer perfil.
- Confirmar ocorrência não emite alerta automaticamente. A emissão é ação explícita do representante. Alteração e encerramento notificam somente se houver alerta anterior, conforme destinatários e canais aprovados.
- Não expor dados pessoais dos autores de relatos de terceiros aos moradores. Restringir auditoria a consulta por administrador e representantes, sem guardar senhas ou códigos.

## Entrega acadêmica

O AV03 exige ao menos sete funcionalidades completas, com fluxo, persistência e regras de negócio. Isso não altera o escopo de 13 RF aprovado. Planejar integração por fluxos completos e identificar claramente o que ainda está pendente; não contar telas estáticas como funcionalidades entregues.

## Aceite de cada requisito

Spec atendida, persistência verificada em PostgreSQL, autorização testada no servidor, erros e fluxo de sucesso conferidos, testes relevantes passando, revisão pelo colega e demonstração compreensível dos conceitos de POO usados.

## Pontos técnicos ainda abertos

Provedor real de SMS/WhatsApp, implantação e metas quantitativas de desempenho, disponibilidade, backup e retenção não foram definidos. Registrar decisões quando necessárias; não inventar esses parâmetros nem acrescentar funções para justificar a implementação.
