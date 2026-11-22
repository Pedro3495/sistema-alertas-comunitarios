# SDD — registro de ocorrências por moradores (RF006)

Fontes: RF006 regras 1–10 (`docs/requisitos/RF006.md`), RF008 regra 5 (registro novo usa a nova área), RF013 regra 1, RS do projeto. Responsável humano: Pedro; executor: IA na branch `feat/rf006-registro-moradores`. Completa o recorte de moradores deixado pendente em [RF006-representantes.md](RF006-representantes.md).

## Por que este recorte

Pendências em ordem das fontes: RF006 (moradores), RF008, RF009, RF010, RF011, RF012, RF013 (consulta já existe; faltam valores anteriores/novos de alteração, que dependem do RF011). RF007 tem decisões abertas (limite de pedidos e auditoria da recuperação) e não foi tocado. O RF006 dos moradores é o primeiro pendente e não depende de decisão aberta; o RF008 precisa dele para "os próprios relatos".

## Entrega

- Migração **V13** `V13__ocorrencias_moradores.sql`: `representante_id` passa a opcional, nova `morador_id` (FK `ON DELETE RESTRICT`), `CHECK` de exatamente um autor, `UNIQUE (morador_id, envio_id)`; auditoria aceita responsável `MORADOR`. V1–V12 intactas; `database/schema.sql` reexportado.
- `OcorrenciaService.registrarPorMorador`: exige morador com senha definitiva e sessão válida (`UsuarioAtual.exigirMorador`), trava a conta (`AutenticacaoMoradorService.bloquearAutor`) e relê morador e residência após o lock. **A área vem da residência atual** (regra 4); `areaId` enviado é ignorado. Mesmas validações do domínio (tipo, Outros, descrição, localização, 1–4 fotos, 5 MB, formato pelos bytes). Repetição com a mesma chave retorna o protocolo existente sem nova auditoria (regra 7). Auditoria `REGISTRAR_OCORRENCIA #<protocolo>` com responsável `MORADOR` na mesma transação.
- HTTP (cadeia de segurança do morador, autoridade `MORADOR`, CSRF ativo): `GET /morador/ocorrencias/novo` (área da residência fixa, rascunho `MORADOR:<id>`), `POST /morador/ocorrencias` (multipart, mesma resposta JSON), `GET /morador/ocorrencias/{protocolo}` (confirmação com protocolo; remove o rascunho da própria conta) e `/fotos/{ordem}`. O morador só abre os próprios registros; outro protocolo responde 404.
- Formulário, detalhe e `ocorrencias.js` reaproveitados (rascunho IndexedDB por conta, sem envio automático, redução de fotos). O JS valida o `detalheUrl` contra o caminho do próprio formulário.
- Consulta de representante por protocolo funciona para ocorrência de morador (sem "Representante responsável").
- Filtro de auditoria lista moradores como `Morador #id` (sem nome/CPF).

## Limites

- Consulta regional do morador (relatos de terceiros da área), filtros e nome/celular do autor para representantes: RF008, pendente.
- Representante não vê o autor morador nesta tela (RF008 regra 4 fica para o RF008).
- Alteração/verificação/encerramento: RF009/RF011/RF012. Nenhum envio de mensagem.

## Evidências

- `OcorrenciaMoradorServiceTests` (5): área da residência com `areaId` forjado, replay sem duplicar auditoria, mudança de residência usa nova área sem transferir a antiga, validações, isolamento de perfis/autores, conta desativada ou com troca pendente.
- `OcorrenciasHttpTests` (+2): login real por CPF, formulário com área única e chave `MORADOR:`, multipart com `areaId` forjado, detalhe/foto próprios, 404 para outro morador, representante consulta o protocolo, morador sem acesso às rotas do representante, CSRF ausente 403.
- `./scripts/verificar.sh` com Java 21 em PostgreSQL 17 isolado (base nova no container `alertas-rf006-test`, porta 55440): 271 testes, 0 falhas, `BUILD SUCCESS`, `git diff --check` limpo. Nesse container compartilhado foi preciso `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=4` (sem isso, "too many clients" ao carregar contextos); configuração apenas do ambiente local, sem mudança no código.
- `node --test src/test/js/ocorrencias.test.cjs`: 14/14.

Revisão de IA não é aprovação humana.
