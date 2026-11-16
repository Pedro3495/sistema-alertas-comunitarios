# Requisitos de segurança

Transcrição da tabela do documento aprovado; consultar também o original em `../referencias/Requisitos_Aprovados.docx`.

## [RS001] — Acesso a funções fora das permissões do usuário. (RF001, RF002, RF003, RF004, RF005, RF006, RF007, RF008, RF009, RF010, RF011, RF012, RF013)

Verificar as permissões em cada operação, inclusive em tentativas de acesso direto fora das telas permitidas. O administrador cria e desativa contas individuais dos representantes; a desativação bloqueia o acesso. As funções da entidade permanecem restritas aos seus representantes. Cinco falhas consecutivas de login por conta geram bloqueio de 15 minutos.

## [RS002] — Exposição da senha original armazenada. (RF001, RF004, RF005, RF007)

Armazenar as senhas de modo que nenhum usuário, inclusive o administrador, consiga consultar a senha original. Manter a recuperação por redefinição; a senha temporária deverá ser trocada no primeiro acesso. Senhas com no mínimo 8 caracteres, sem combinação obrigatória. Senhas temporárias válidas por 24 horas; representantes recebem diretamente do administrador, com troca obrigatória.

## [RS003] — Recuperação de acesso por pessoa não autorizada. (RF001, RF005, RF007)

Enviar o código somente ao telefone cadastrado, pelo canal único escolhido para credenciais. O código terá uso único e validade de 10 minutos após a emissão. Após a validação e a criação da nova senha, o morador acessará com CPF e senha. Se perder o telefone, deverá procurar a entidade para atualizar o número. Novo código invalida o anterior; cinco erros invalidam o código. A validade de 10 minutos e o uso único permanecem.

## [RS004] — Exposição de dados pessoais e acesso a relatos de outra região. (RF001, RF006, RF008)

Permitir ao morador consultar seus relatos e os da área vinculada à sua residência. Na consulta de relatos de terceiros, exibir tipo, descrição, localização, fotos e situação, sem nome, CPF ou telefone do autor. Após mudança residencial, preservar os próprios relatos antigos e restringir a consulta de terceiros à nova área. Representantes veem nome e celular do autor na ocorrência, sem CPF nessa tela.

## [RS005] — Consulta indevida ou modificação dos registros de auditoria. (RF001, RF002, RF003, RF004, RF006, RF009, RF010, RF011, RF012, RF013)

Restringir a consulta da auditoria ao administrador e aos representantes. Os registros terão ação, usuário responsável, data e hora, e serão somente para consulta, sem alteração ou exclusão pelos usuários, inclusive pelo administrador. Incluir as operações cadastrais permitidas e os valores anteriores/novos nas alterações de ocorrências. Não registrar senhas ou códigos.

## [RS006] — Duplicidade de identidade cadastral ou comunicação sem vínculo residencial. (RF001, RF002, RF003, RF006, RF008)

Impedir CPF e telefone repetidos em cadastros de pessoas diferentes. Conferir o comprovante de residência sem armazenar cópia. Restringir o registro do morador à área de sua residência cadastrada, preenchida automaticamente no formulário. Validar 11 dígitos e verificadores do CPF sem consulta à Receita Federal, e celular com DDD e 11 dígitos. Manter exclusividade em contas desativadas.

## [RS007] — Alteração ou conclusão de ocorrência fora das regras definidas. (RF006, RF009, RF011, RF012, RF013)

Reservar a confirmação ou não confirmação aos representantes, com justificativa. Exigir justificativa para alterar e encerrar. Após o encerramento, manter a ocorrência somente para consulta, sem novas alterações. Registrar as ações na auditoria. Proibir remoção/substituição das fotos e exclusão da ocorrência por todos os perfis. Moradores não alteram registros enviados. Registrar valores anteriores e novos dos campos alterados.

## [RS008] — Emissão de alerta sem confirmação ou sem decisão da entidade. (RF009, RF010, RF011, RF012)

Permitir alerta somente de ocorrência Confirmada, mediante ação explícita do representante. A confirmação da ocorrência não disparará o alerta automaticamente. Preservar alertas emitidos sem edição ou exclusão. Novas emissões exigem nova ação manual sobre ocorrência confirmada.

## [RS009] — Interceptação dos dados transmitidos entre aplicativo e servidor. (RF001, RF002, RF003, RF004, RF005, RF006, RF007, RF008, RF009, RF010, RF011, RF012, RF013)

Exigir HTTPS em toda a comunicação entre aplicativo e servidor, incluindo CPF, telefone, senhas e fotos.
