# Áreas e residências — entrega web RF002/RF003

Tarefa #21, continuação do PR #20. Contrato: [RF002-RF003-web.md](../../specs/entregas/RF002-RF003-web.md). Implementação por subagentes em worktrees separados e integração pelo coordenador. O Word aprovado não foi modificado.

## Fluxo disponível

Após entrar com senha definitiva, o representante encontra áreas e residências em sua página. Administrador e representante com temporária não recebem essas permissões.

Em `/areas`, pode cadastrar nome/descrição de limites, corrigir, pesquisar pelo nome e excluir com confirmação. Alterar a área mantém seu ID e os vínculos existentes; nenhuma residência é redistribuída automaticamente. Uma área vinculada a residência não pode ser excluída.

Em `/residencias`, pode cadastrar endereço, pesquisar pelos quatro campos textuais e pela área, corrigir o endereço ou trocar manualmente a área e excluir com confirmação. Rua/logradouro, número, bairro, município e área são obrigatórios. Número é texto e aceita `s/n`; complemento e ponto de referência são opcionais. Não há CEP, geocodificação, comprovante anexado ou regra de endereço único.

Sem áreas, o formulário orienta cadastrar uma antes e desabilita o envio. Campos inválidos ou área removida durante o preenchimento preservam os valores e apresentam erro. ID inexistente retorna404. Toda mutação usa CSRF, autorização no servidor e auditoria na mesma transação; falhas não criam registro de sucesso.

## Persistência e concorrência

V7 cria `residencias` com uma referência obrigatória a `areas_risco`, protegida por FK `ON DELETE RESTRICT`. Dados de endereço são texto, sem novos máximos. A área necessária à página é carregada antes de encerrar a transação; OpenInView continua desabilitado.

Pesquisa de residência usa trechos sem distinção de caixa, combinados com AND. `%`, `_` e barra invertida são literais. Filtros vazios não restringem. Área usa igualdade. Essas escolhas técnicas não acrescentam requisitos ao Word.

Alteração e exclusão de áreas usam o mesmo bloqueio de linha. A revisão independente encontrou uma corrida que produzia erro otimista quando uma área era removida após sua leitura. Um teste reproduziu essa falha no código anterior; com o bloqueio, a busca observa a ausência e retorna o erro correto. Residências também são bloqueadas nas mutações. O FK decide disputas com criação/transferência, sem contagens fictícias de vínculos.

## Evidências

- 136 testes passaram em PostgreSQL descartável: os119 anteriores,16 testes HTTP de áreas/residências e uma regressão concorrente determinística.
- HTTP real cobre fluxos, campos obrigatórios/opcionais, filtros literais, transferência, vínculos preservados, autorização, confirmação, CSRF, auditoria e duas disputas com exclusão de área, sem órfãos.
- SQL restaurado em banco vazio: cinco tabelas, oito campos de residência e FK restritiva para área.
- Telas conferidas no navegador em390px e1440px, incluindo ausência de áreas, cadastro, listagem e recusa de exclusão vinculada. Dados fictícios ficaram em banco visual separado.
- Revisão estática independente reconferiu a correção da concorrência; isso não equivale a aprovação humana. Detector mecânico de UI sem achados. Hash do Word preservado.

O script SQL contém somente estrutura, sem dados das fixtures. Os bancos descartáveis de testes/visual são removidos ao final. Não houve merge, deploy ou alteração das proteções.

## Limites reais

A base persistente de moradores da tarefa #23 acrescenta V8 e FK restritiva para residência, inclusive com morador desativado. A recusa de exclusão também foi testada por HTTP no fluxo existente. O cadastro público de moradores ainda depende do envio real de credenciais; ver [contrato da base](../../specs/entregas/RF001-base.md). A tarefa #25 acrescenta ocorrências por representantes com V9 e FK restritiva de área, antes da exposição do registro. RF013 continua parcial quanto às demais operações e aos valores anteriores/novos das ocorrências.

O usuário confirmou que ainda não escolheu um provedor de SMS/WhatsApp. A entrega real de senha temporária do morador e códigos de recuperação depende dessa escolha e configuração. Nenhum envio foi simulado como concluído. HTTPS de implantação também permanece pendente.

Integrar a cadeia na ordem #16 → #17 → #19 → #20 → áreas/residências, preservando V2 atéV7. Não editar migrações aplicadas nem habilitar out-of-order.
