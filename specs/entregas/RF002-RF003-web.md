# SDD — áreas e residências para representantes

Fonte RF002/RF003, RF013 e RS001/005. Continuação do PR #20; branch feat/rf002-residencias-areas. Provedor SMS/WhatsApp permanece lacuna confirmada pelo usuário. Esta entrega não precisa enviar mensagens nem criar moradores.

## Coordenação

V7 `V7__criar_residencias.sql` reservada. Agente domínio/web de residências altera somente pacote residencias, templates/residencias, CSSopcionalresidencias eV7. Executorprincipal faz áreas/guards/segurança/navegação; outroagente testesHTTP. API compartilhada: `UsuarioAtual.exigirRepresentante()` retorna Responsavel e exige principal definitivo válido, semadmin. `AuditoriaService.registrar(String acao)` usa ator atual e exige transação. Não mudar resto dos contratos.

## Residências

`DadosResidencia(String ruaLogradouro,String numero,String bairro,String municipio,String complemento,String pontoReferencia,Long areaId)` record público. `ResidenciaService.cadastrar(DadosResidencia)` e `alterar(Long id,DadosResidencia)` retornam Residencia; `buscar(Long id)`, `pesquisar(String rua,String numero,String bairro,String municipio,Long areaId)`, `excluir(Long id)`.

Service exige representante definitivo em cada operação; mutações transacionais com auditoria (CADASTRAR_RESIDENCIA/ALTERAR_RESIDENCIA/EXCLUIR_RESIDENCIA #id). Domínio valida todos campos antesmutar. Quatrotextos obrigatórios, opcionais livres, número String aceitas/n; sem CEP, UF, geocodificação, unicidadedeendereço oucomprovante. Uma área existente porresidência, selecionadamanualmente. CamposTEXT, semnovosmáximos. V7 table residencias id,rua_logradouro,numero,bairro,municipio,complemento,ponto_referencia,area_id NOTNULL FKareas_risco(id) ON DELETE RESTRICT. JPA/consultapré-carregaarea necessáriaaHTML, semOpenInView. Gettersid/campos/areaId/areaNome paraform/resultado.

Pesquisa opcional por trecho case-insensitive nos4textos comAND; % _ e barra literais, blanknãofiltra; areaId porigualdade. Nenhum actor/id deresidência decidido pelo clientealémdopathdaoperação; atoréprincipal.

HTTP GET `/residencias` filtros rua,numero,bairro,municipio,areaId; GET/POST `/residencias/novo` campos DTO; GET/POST `/residencias/{id}/editar`; POST `/residencias/{id}/excluir` confirmacao=true obrigatório. SemGETdestrutivo. GETedição exibecheckbox de exclusão cominstrução. Sucesso PRG/residencias?sucesso. Erros200comalert/erroscampo e preserva dados, inclusiveseárea sumir durantepreenchimento. IDnãoexistente404. Semáreas, informarcadastrararea/link/areas/novo, sempseudoseed. ExclusãocomvínculoFKfuturorecusadacomtextocompreensível. Sem consultasfictícias de moradoresnãoexistentes.

## Áreas — executor principal

Preservar service atual (pesquisarPorNome rejeitabranco). Acrescentar buscar/listar/excluir viarepo; exclusão usa flush parafalhadeFKrevertertransação. Fachada exigeREPdefinitivo e auditoria em cadastro/alteração/exclusão (prefixos CADASTRAR_AREA/ALTERAR_AREA/EXCLUIR_AREA #id).

HTTP GET `/areas` filtro nome, GET/POST `/areas/novo` campos nome/descricaoLocalizacaoLimites, GET/POST `/areas/{id}/editar`, POST `/areas/{id}/excluir` confirmacao=true. Faltaobrigatório200erropróximoecampospreservados, id404. Área alterada mantémIDevínculosresidenciais, semredistribuiçãoautomática. FKrealproíbeexcluircomresidências. Mesmaordem transacionalaauditoria.

## Autorização/UI

Cadeiarep libera somente /areas,/areas/**,/residencias,/residencias/** paraREPdefinitiva; temporária/admin/anônimonegados(anonpoderedirecionarlogin). FormsCSRF, naváreas/residências/auditoria/sair, respeitarAstryxNeutral/telasexistentes e botõesgrandes/labels/responsivo. Nenhumcampodeator. Adminnãoherdapermissões.

## Aceite e limite real

HTTPPostgreSQL: cadastrar/editar/pesquisar/excluirregistros, obrigatórios, opcionais,s/n,filtroAND e literais, transferência manual deárea, correçãodeáreapreservaIDs,FKbloqueiaexclusãovinculada/rollbackauditoria, confirmaçãosemconsentimento nãoaltera/audita, autorizaçãoCSRF, históricoatorestável. Pelo menos testeconcorrrente criaçãoresidênciavsdeleteárea e transferência vsdeleteáreadestino: semórfãos e ambosnuncaconfirmamestadoinválido. SQLrestaurado e CI/testeslocais,revisãotécnica independente,390/1440visual.

Ainda não existem moradores e ocorrências. Não declarar seus bloqueios de exclusão entregues, nem criar counts/tabelasfake. Migrações desses módulos precisarão FKs restritivas pararesidência/área antesdosfluxos públicos. RF002/RF003 seguemparciais nessa integração. Não editar Word,nemmerge/deploy.
