# SDD — registro de ocorrências por representantes

Fontes: RF006 regras 1–8/10, RF003 exclusão, RF013, RS001/RS005/RS007, RNF01–04/06/07. Responsável humano: Pedro; executor principal: IA na branch `feat/rf006-registro-representantes`, worktree `rf006-ocorrencias`, sobre PR24. Provedor continua sem decisão: nenhum envio nem cadastro público/acesso de moradores. O recorte entrega registro pelo representante, confirmação/consulta das próprias evidências e integridade; RF006/RF008 completos, rascunhos dos moradores e alteração/verificação/encerramento não serão declarados entregues.

## Coordenação e persistência

V9 `V9__criar_ocorrencias.sql` reservada ao executor principal, junto de segurança, contrato, controller/serviço e integração. UI terá escopo exclusivo em templates/ocorrencias e static/js/ocorrencias.js, static/css/ocorrencias.css. Testes em src/test/java/br/edu/alertas/ocorrencias, sem editar produção. Cada agente usa worktree própria e branch do commit deste contrato; não iniciar a implementação sobre main.

Ocorrência: id técnico, protocolo UUID único gerado no servidor, chave de envio UUID por representante (única na combinação), instante do servidor, tipo, especificação de Outros, descrição, localização, área e representante responsável. Situação inicial `EM_VERIFICACAO`. FK de área `ON DELETE RESTRICT`, antes de expor registro. Fotos em tabela própria com FK RESTRICT, ordem 1–4, tipo de mídia e bytes (`bytea`), inseridas na mesma transação; sem nome/caminho enviado pelo usuário. Armazenamento no PostgreSQL é escolha técnica local, sem novo serviço ou dados no Git. V2–V8 preservadas.

## Contratos Java

Pacote `br.edu.alertas.ocorrencias`. `TipoOcorrencia`: `DESASTRE`, `OBRA`, `BARREIRA_CONTENCAO`, `OUTROS`. `DadosOcorrencia(UUID envioId, TipoOcorrencia tipo, String especificacaoOutros, String descricao, String localizacao, Long areaId, List<byte[]> fotos)`.

`OcorrenciaService.registrar(DadosOcorrencia)` retorna `Ocorrencia`; exige representante definitivo via UsuarioAtual. Bloquear a linha do representante e revalidar a sessão após o lock; consultar combinação ator/chave antes de validar um reenvio. Repetição retorna o protocolo existente sem nova foto, ocorrência ou auditoria. Representantes diferentes não compartilham a chave. Protocolo, horário, responsável e situação vêm do servidor. Unique e FK decidem disputas; nenhuma confirmação conjunta inválida.

Validar tipo, descrição, localização e área existente; Outros exige especificação separada. Não acrescentar máximos de texto. Fotos: de uma a quatro, não vazias, até `5 * 1024 * 1024` bytes cada. Identificar JPEG/PNG e HEIC/HEIF pelos bytes, não pelo nome/MIME fornecido. Validar estrutura dos arquivos sem executar conteúdo; HEIF é mantido original, sem decoder nativo no servidor. Redução acima do limite ocorre no navegador antes do envio; falha de leitura/redução exige outra foto, conforme RF006. Não criar novo limite de dimensões.

`ErroOcorrencia` estende IllegalArgumentException e expõe `getCampo()` para `tipo`, `especificacaoOutros`, `descricao`, `localizacao`, `areaId`, `fotos` ou `envioId`. Serviço usa auditoria `REGISTRAR_OCORRENCIA #<protocolo>` na mesma transação; replay não audita novamente. `buscar(UUID protocolo)` e `foto(UUID protocolo, int ordem)` exigem representante definitivo. Nenhuma operação de exclusão/alteração de ocorrência/foto.

Getters da ocorrência para template: `protocolo`, `registradaEm`, `tipo` (enum com `getRotulo()`), `especificacaoOutros`, `descricao`, `localizacao`, `areaNome`, `representanteNome`, `situacao` (texto `Em verificação`), `fotos` (lista de metadados com `ordem`, `tipoMidia`). Bytes retornados são cópias; nunca incluir bytes na confirmação JSON ou lista HTML.

## HTTP e interface

- GET `/ocorrencias/novo`: template `ocorrencias/formulario`, atributos `areas` (id/nome), `rascunhoChave` (`REPRESENTANTE:<id>`). Thymeleaf fornece `_csrf` como nos formulários atuais. Sem áreas, orientar cadastro e desabilitar envio. JS em `/js/ocorrencias.js` público; rotas de ocorrência/foto somente representante definitivo.
- POST `/ocorrencias`: multipart, campos `envioId`, `tipo`, `especificacaoOutros`, `descricao`, `localizacao`, `areaId`, arquivos repetidos `fotos`. CSRF obrigatório. Sucesso 200 JSON `{protocolo: "UUID", detalheUrl: "/ocorrencias/UUID"}`. Erro de preenchimento 400 JSON `{campo: "...", erro: "mensagem simples"}`; erro estrutural/multipart excedido 400 com campo fotos. Negação 403; preservar rascunho também se a sessão vencer. Não enviar estado/ator/protocolo pelo formulário.
- GET `/ocorrencias/{protocolo}`: template `ocorrencias/detalhe`, atributo `ocorrencia`; confirmação exibe protocolo, instante, situação, campos, área, nome do representante e evidências. Sem CPF ou celular inventado para representante. Consulta por protocolo de qualquer área para representantes; consulta regional de moradores/filtragem RF008 ficam pendentes. Inexistente 404.
- GET `/ocorrencias/{protocolo}/fotos/{ordem}`: bytes autorizados e `Cache-Control: no-store`, `nosniff`, MIME determinado pelo servidor. Nome fixo. JPEG/PNG podem ser exibidos; HEIC/HEIF oferece original para abrir em aplicativo compatível, sem alegar preview universal. Inexistente 404. Não existe rota de alteração/exclusão.

Preservar Astryx Neutral e classes de gestao/residencias, linguagem simples, labels e botões grandes. A navegação da página do representante ganha Registrar ocorrência. Usar frontend-design para estender o padrão existente; sem redesign ou animação ornamental.

JS: IndexedDB com um rascunho por chave de conta neste navegador, guardando textos, área, fotos/Blobs e envioId estável antes de tentar rede. Recuperar após recarregar/fechar, apresentar revisão e exigir envio manual; não enviar ao evento online. Nova seleção substitui fotos apenas no rascunho, antes do envio. Reduzir >5MB com canvas para JPEG permitido, iterando qualidade/dimensões até caber; se decode/canvas/redução falhar, explicar e pedir outra foto, sem enviar o arquivo grande. Validar formatos aceitos também no cliente. Erros de campo aparecem junto ao campo e preservam demais dados/fotos. Informar falha de persistência local sem afirmar que rascunho ficou salvo.

Após resposta JSON, navegar para detalhe; **somente no detalhe já exibindo protocolo** remover o rascunho correspondente, se seu envioId coincidir com `ocorrencia.envioId` e `rascunhoChave`. O detalhe recebe esses atributos apenas para a conta autora; consultar detalhe de outra ocorrência não remove rascunho. Se rede cair após commit, nova tentativa com mesma chave retorna protocolo original. Até uma resposta definitiva, o conteúdo tentado fica congelado no formulário e no rascunho; apenas 400 de preenchimento libera a correção com a mesma chave. Não persistir senha/token CSRF em IndexedDB.

## Verificação

Testes reais de serviço/HTTP/PostgreSQL: sucesso com tipos/fotos/campos aprovados, limites e erros com área removida, MIME falso/arquivo inválido, protocolo e instante de servidor, bytes preservados, replay serial/concorrente sem duplicar auditoria/evidências, isolamento de chave por conta, autorização de todas as rotas/serviço, CSRF, ausência de exclusão/alteração, rollback de fotos/registro se auditoria falhar, FK e corrida registro vs exclusão de área. Testar JS de redução/falha e rascunho com reload, isolamento de conta, rede interrompida antes/depois de commit e apresentação do protocolo, sem autoenvio. Conferir telas 390/1440, SQL em banco vazio, diff, suíte/CI e revisão independente.

Limites: nenhum login/rascunho de morador entregue, nenhum provedor, alerta, alteração/verificação/encerramento ou HTTPS de implantação. A confirmação/consulta deste recorte não equivale a RF008 completo. Não fazer merge/deploy.
