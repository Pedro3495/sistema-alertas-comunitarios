# Registro de ocorrências por representantes

Tarefa #25, continuação do PR24. Contrato e limites: [RF006-representantes.md](../../specs/entregas/RF006-representantes.md). O Word aprovado não foi alterado.

## Fluxo disponível

Representante com senha definitiva acessa Registrar ocorrência em sua página, escolhe uma área existente, informa tipo, descrição e localização e seleciona uma a quatro fotos. Outros exige especificação separada. O protocolo único, o instante e a situação inicial Em verificação vêm do servidor. Não há emissão de alerta automática.

JPEG/PNG e HEIC/HEIF são identificados pelo conteúdo, sem confiar no nome/MIME do upload. Acima de5MB, o navegador tenta reduzir para JPEG antes de enviar; falha pede outra foto. O servidor valida tamanho e estrutura, preserva os bytes recebidos e não executa decoder nativo HEIF. PNG exige chunks com CRC, IHDR coerente, IDAT consecutivos com zlib completo na quantidade exata de bytes de linhas declarada e IEND final; JPEG exige SOF com dimensões, SOS com dados de entropia e EOI. A saída do zlib é descartada e os coeficientes JPEG não são decodificados. O navegador identifica JPEG/PNG só pela assinatura; a recusa estrutural vem do servidor, junto ao campo de fotos. Evidências ficam no PostgreSQL, fora do Git, em transação com ocorrência e auditoria. HEIC/HEIF original é oferecido para abrir em aplicativo compatível; não há promessa de preview em todo navegador.

O rascunho com fotos fica em IndexedDB, separado por conta nesse navegador. Recarregar recupera dados e exige revisão/envio manual, sem envio ao reconectar. Falha de armazenamento informa que os dados podem se perder e impede afirmar que houve persistência. O token CSRF e a senha não são guardados no rascunho. A remoção compara conta/chave do envio e só ocorre no detalhe, após apresentação do protocolo; consultar outro envio não apaga o atual.

Se a conexão falhar após a gravação, repetir manualmente mantém a chave e recupera o protocolo existente. Enquanto o envio estiver incerto, campos e fotos ficam bloqueados com o conteúdo tentado, também após recarregar; assim o protocolo recuperado nunca corresponde a uma correção descartada. Só uma recusa de preenchimento (400) libera a edição com a mesma chave, pois nada foi gravado. O detalhe remove apenas rascunho marcado como tentativa enviada. O servidor serializa envios da mesma conta, revalida credencial após obter o lock e não repete fotos/auditoria. Área com ocorrência vinculada não pode ser excluída, por FK RESTRICT. Não existem rotas de exclusão da ocorrência ou de remoção/substituição de fotos.

## Evidências locais — 22/11/2026

Retomada após parada do executor anterior (correção dos dois P2 da revisão independente):

- `./scripts/verificar.sh` com Java21 e base exclusiva nova `alertas_rf006_claude_163910` no container isolado `alertas-rf006-test` (loopback55440): 239 testes Java, zero falhas/erros/skips (226 anteriores + 13 de `FotoEstruturaRegressaoTests`: PNG de 33bytes só com assinatura/IHDR, IDAT cortado, CRC inválido, zlib truncado, scanlines insuficientes, IHDR forjado, JPEG sem SOS, sem EOI, sem entropia e assinatura falsa; aceitação de PNG/JPEG reais e PNG Adam7). Depois, o caso de JPEG progressivo real foi acrescentado e a classe rodou isolada: 14 testes, zero falhas.
- `node --test src/test/js/ocorrencias.test.cjs`: 14 testes, zero falhas. O novo cenário de resposta perdida falha no código anterior (13/14) e passa no atual.

Rodada anterior:

- `./scripts/verificar.sh` com Java21 e `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55440/alertas_rf006`:226 testes Java, zero falhas/erros/skips. São177 anteriores,34 de serviço e15 HTTP novos. Os novos testes também passaram em base exclusiva do agente de testes. A primeira rodada nova detectou consulta JPA derivada inválida, corrigida com caminho explícito `representante_Id` e retestada.
- `node --test src/test/js/ocorrencias.test.cjs`:13 testes com dublês de DOM/IndexedDB/rede/canvas; `node --check` passou. Cobrem falhas antes/depois do commit, sessão vencida, validação e redução, rascunho/reabertura/isolamento/limpeza e identificação HEIF/recusa AVIF. Não substituem navegador real.
- PostgreSQL17.10 isolado em `alertas-rf006-test`, porta loopback55440 e tmpfs, sem volumes ou conexão ao banco de trabalho. SQL exportado com `pg_dump --schema-only --no-owner --no-acl --exclude-table=flyway_schema_history` e restaurado com `psql -v ON_ERROR_STOP=1` em banco vazio:8 tabelas e FKs restritivas para área, responsável e evidências. V2–V8 preservadas.
- HTTP real cobre autorização, CSRF, rollback de registro/fotos após falha de auditoria, bytes e formatos reais, tamanho/quantidade, repetição serial/concorrente e FK em disputa com exclusão de área. Duas regressões determinísticas observam a espera real de lock no PostgreSQL antes de desativar/redefinir a conta: a sessão revogada não registra.
- Navegador Orca real: rascunho e Blob restaurados após reload com mesma chave e revisão desmarcada; PNG sintético de5242999bytes reduzido para JPEG de767bytes; sessão vencida conserva dados. Falha da resposta foi injetada após commit real: repetição manual retornou o protocolo existente, com apenas uma ocorrência/foto/ação de auditoria, e IndexedDB ficou sem o rascunho somente após o detalhe exibir esse protocolo. Formulário e detalhe conferidos em390/1440px com dados fictícios.
- Fixtures HEIC: imagem sintética32x32 gerada por Python `struct`/`zlib` e convertida por `sips`; HEIF derivada validada por `sips`. Não são fotos de pessoas, arquivos recebidos ou evidências reais de ocorrência.

## Limites

RF006 continua parcial: não há registro/acesso/rascunho de moradores nem alteração de ocorrências. RF008 tem somente consulta do protocolo/evidências para representantes, sem pesquisa e filtros completos. Verificação, alteração, encerramento, distribuição de alertas e respectivas notificações não foram entregues. Provedor/conta de testes continuam sem decisão; nenhum segredo foi solicitado, mensagem real enviada, merge/deploy realizado ou HTTPS de implantação configurado.

Referência técnica de identificação HEIF: [libheif](https://github.com/strukturag/libheif), sem acrescentar dependência ao projeto. Reconhecer estrutura não equivale a decodificar todos os codecs de HEIF no servidor.
