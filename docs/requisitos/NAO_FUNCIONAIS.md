# Requisitos não funcionais

Transcrição da tabela do documento aprovado; consultar também o original em `../referencias/Requisitos_Aprovados.docx`.

## [RNF01] — Facilidade de uso

A interface deverá usar linguagem simples, botões grandes e instruções próximas aos campos, facilitando o uso por pessoas com pouca familiaridade com tecnologia.

Prioridade: Essencial

## [RNF02] — Erros nos campos

Se um campo obrigatório estiver vazio ou preenchido incorretamente, o aplicativo deverá indicar o campo, explicar como corrigir e preservar os demais dados já preenchidos.

Prioridade: Essencial

## [RNF03] — Fotos e redução de tamanho

Cada ocorrência aceitará de uma a quatro fotos, de até 5 MB cada, nos formatos JPG/JPEG, PNG, HEIC e HEIF. Fotos acima de 5 MB serão reduzidas automaticamente antes do envio. Arquivo em formato não aceito ou cuja redução falhe será recusado, solicitando outra foto.

Prioridade: Essencial

## [RNF04] — Falha de conexão

Se a conexão cair durante o registro, o aplicativo deverá preservar os dados preenchidos, informar que o envio não foi concluído e permitir nova tentativa quando a conexão voltar.

Prioridade: Essencial

## [RNF05] — Persistência e isolamento do rascunho

Após falha de conexão, manter um rascunho por morador em cada aparelho, mesmo após fechar o aplicativo, isolado das outras contas do aparelho. Retomar com revisão e envio manual, sem envio automático ao reconectar. Remover somente após confirmação do registro e protocolo.

Prioridade: Essencial

## [RNF06] — Integridade do mesmo envio

Se a ocorrência já tiver sido salva, mas a confirmação não tiver aparecido por falha de conexão, uma nova tentativa do mesmo envio deverá retornar o protocolo existente, sem duplicar a ocorrência.

Prioridade: Essencial

## [RNF07] — Sistema web responsivo

O sistema será web responsivo, com acesso pelo navegador do celular e do computador, atendendo moradores, representantes e administrador.

Prioridade: Essencial
