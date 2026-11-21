# Acesso de representantes — RF005 parcial

Entrega da tarefa #18, baseada no [contrato SDD](../../specs/entregas/RF005-representantes.md). Continua a base RF004 do PR #17. Não conclui todo RF005: o acesso de moradores ainda depende dos respectivos cadastros e credenciais.

## Fluxo disponível

A página inicial oferece acesso a `/entrar`. Um representante cadastrado na persistência pode informar login literal e senha. A temporária vale por 24 horas e permite somente definir a primeira senha; acesso direto a `/representante` é negado até a troca. A nova senha tem mínimo de oito caracteres Unicode, sem composição ou máximo adicional. Após salvar, a sessão é encerrada e a pessoa entra novamente com a nova senha.

A página autenticada confirma a conta e permite sair. Ela informa que as funções de ocorrências ainda estão em desenvolvimento; não simula essas funções. O logout altera estado somente por POST com CSRF. Não existe conta padrão, cadastro público ou administrador fixo. Nesta entrega, fixtures fictícias dos testes exercitam o cadastro pela camada de serviço; não há procedimento administrativo completo disponível para uso real.

## Proteções verificadas

- Cinco senhas incorretas consecutivas de conta ativa bloqueiam novos logins por 15 minutos, com contagem persistida no PostgreSQL. Sucesso zera a sequência; tentativas durante o bloqueio não estendem o prazo. O limite exato de 15 minutos libera nova tentativa.
- Temporária vencida, conta inativa ou bloqueada não autenticam. No instante exato de expiração a temporária já é recusada, inclusive no formulário aberto anteriormente.
- A sessão guarda identidade estável e versão de credencial, sem senha/hash. Cada requisição autenticada revalida conta, versão, estado de troca e validade da temporária. Reset, desativação, correção do login e primeira troca invalidam sessões antigas. Reativação não revive uma sessão revogada.
- A autenticação confirma sua transação antes de o provider produzir a falha de login; a exceção HTTP não apaga o contador. Autenticação, troca e manutenção travam a mesma linha para coordenar concorrência.
- Erros não revelam existência/inatividade/vencimento da conta. O login é preservado e escapado no formulário; senhas nunca são repopuladas. O filtro padrão do Spring remove espaços do username: o provider recebe o parâmetro literal por authentication details para respeitar o cadastro, que usa igualdade exata. Details contém somente login.
- CSRF e renovação do identificador de sessão no login continuam ativos. As autoridades de primeira troca e representante são distintas; nenhuma concede ADMIN. Rotas de gestão, moradores, áreas e auditoria continuam negadas.

V4 acrescenta três controles técnicos à tabela existente: contador, fim do bloqueio e versão de credencial. V1–V3 permanecem intactas. A senha definitiva não recebe uma expiração de 24h; essa validade aplica-se somente à temporária. Redefinir temporária não libera um bloqueio de login vigente.

## Evidências

`./scripts/verificar.sh`: **97 testes**, zero falhas/erros: 67 anteriores, 20 de autenticação e 10 HTTP reais. Incluem cinco falhas concorrentes, limites de tempo, Unicode e senha longa sem truncamento, revogação, identidade literal, CSRF, fixação de sessão e fluxo completo até logout. A exportação SQL foi restaurada em banco temporário vazio: duas tabelas, 11 campos de representantes. Banco temporário e fixture visual removidos após conferência.

Revisão estática independente encontrou a remoção implícita de espaços no login; o problema foi corrigido, coberto por teste HTTP e reconferido pelo revisor. Não equivale a aprovação humana de PR. Login e troca foram conferidos no navegador a 390×844 e 1440×1000, incluindo erro de senha curta, usando conta fictícia temporária. O detector mecânico Impeccable não apontou achados. As referências visuais são os prints Astryx existentes; não houve reabertura das decisões de design.

## Limites e continuidade

Ainda faltam autorização administrativa e telas de gestão RF004, auditoria RF013, acesso de moradores, recuperação e entrega de credenciais. Não há integração SMS/WhatsApp nem deploy. HTTP de loopback nos testes/desenvolvimento **não comprova RS009/HTTPS de implantação**; não disponibilizar estes formulários publicamente sem HTTPS configurado.

O PR usa a branch do #17 como base. Integrar a cadeia na ordem #16 → #17 → acesso RF005, preservando V2 → V3 → V4. Nenhum merge ou alteração da proteção foi realizado.
