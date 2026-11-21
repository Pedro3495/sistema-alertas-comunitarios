# SDD — gestão administrativa de representantes e auditoria inicial

Data21/11/2026. Continuação após #19. Usuário autorizou desenvolver continuamente até lacuna e confirmou criar primeiro administrador por comando local, com login/senha fornecidos na execução, sem padrão/código. Fonte RF004, RF005, RF013 e RS001/002/005. Word intacto.

## Divisão e migrações

V5 `V5__criar_administrador.sql`: conta administrativa única. V6 `V6__criar_auditoria.sql`: registro append-only de operações. Implementação de administrador/CLI em worktree separado; executor principal integra HTTP/gestão/auditoria. Testes/revisão independentes; nunca executar bancos compartilhados em paralelo.

## Administrador — contrato para agente

Pacote `br.edu.alertas.administracao`.
- `AcessoAdministrador(Long id, String login, long versaoCredencial)`: record Serializable/Principal, getName retorna login, sem hash/senha.
- `AdministradorService.criarPrimeiro(String login,String senha)` retorna `Administrador`; singleton id1 (banco check id=1), rejeita segundo cadastro sem sobrescrever. Login obrigatório literal; reutilizar mínimo8Unicode semcomposição/max do sistema. PBKDF2 bean existente, sem hash público.
- `autenticar(String login,String senha)` retorna Optional<AcessoAdministrador>, REQUIRES_NEW: 5 falhas15min, sucesso zera, limiteexato libera, falhas confirmadas, lockmesmalinha; nunca conta padrão. Igualdade exata, hash dummy para nãoexistente, sem original.
- `validarSessao(AcessoAdministrador)` boolean; versão/identidade/login conferidos. `redefinirSenhaLocal(String login,String novaSenha)` void: recuperação manual pelo responsável técnico fora do app conformeRF005regra7; mesma conta, incrementaversão, preserva bloqueio vigente. Sem rota de recuperação admin.
- Tabela administradores: id,login,senha_hash,falhas_login,bloqueado_ate,versao_credencial. Sem dependência nova ou perfilnovo. Getters públicos somente id/login.
- Comando `alertas.admin=criar` ou `alertas.admin=recuperar`, sem servidor web (`spring.main.web-application-type=none`). Runner sóativoquandoopçãoexplícita, exige consoleinterativo; login via readLine, senha via readPassword, limpar char[]; nenhuma senha/argumentoambiente/log. Erro claro e código de saída nãozero. Sem console, falhar sem fallbackseguroaparente. Script `scripts/configurar-admin.sh` chama Java21 jarcompilado, com parâmetro criar/recuperar (sem senha); usa comum.sh e exec java. Não executarcriaçãoreal semcredenciaisdoPedro; testes usamfictícias. Executorprincipal marcaSegurancaConfig como ConditionalOnWebApplication para CLI.

## HTTP e gestão — executor principal

Cadeia SpringSecurity separada para `/admin/**`, com provider exclusivo e principal AcessoAdministrador; outra cadeia continua representante. Loginliteral via details, CSRF/renovaçãosessão, POST `/admin/sair`. Sem tentativa de autenticar representante como admin ou aceitar ADMIN informado em formulário. Form GET/POST `/admin/entrar` login/senha, falha genérica, preserva login, sem senha. Sucesso `/admin/representantes`. Validar versão de admin a cada requisição.

GET `/admin/representantes`: pesquisa nome OUlogin, filtrosentidade/situação. GET/POST `/admin/representantes/novo`: nomeCompleto,entidadePublica,login,senhaTemporaria; senha escolhida no ato pelo admin, entregar diretamente conformeRF004, nunca guardaroriginal em sessão/redirect/auditoria. GET/POST `/admin/representantes/{id}/editar`: só3campos, semsenha. POST `/.../{id}/desativar`, `/reativar`, `/senha-temporaria`: últimasenha no corpo, nãoemURL. Confirmação explícitaantesdesativar/reativar/reset. Sem delete, sem consultaantiga. Cada operação usa a mesma conta estável, conservaexclusividadelogin. Falhaspreservamcamposnãosecretos, identificamobrigatórios; errodeunicidade/concorrência tratadosem500. Formulários com token CSRF.

Fachada de gestão exige principal administrativo atual e válido no servidor, não permite ao controlleratribuirator. Transação inclui alteração e auditoria; falha de auditoria revertealteração. Desativação/reset revogam sessões via controleexistente. Nenhuma rota de gestão para representantes.

## Auditoria inicial

Tabela `registros_auditoria`: id,acao,responsavel_tipo,responsavel_id,ocorrido_em. Tipo identifica os perfis já aprovados ADMINISTRADOR/REPRESENTANTE, idestável do usuário. Não criar novo perfil de usuário. Como referência polimórfica, serviço confirma o ator pelo principal autenticado; não aceita ator livre em HTTP. Ação inclui tipo da operação e ID do representante-alvo, nunca senha/hash/código. Append-only: sem setters, update/delete ou endpoints correspondentes.

GET `/admin/auditoria` para admin e GET `/auditoria` para representante definitivo. Consulta filtros `responsavel` (tipo:id de opção válida), `acao`, `inicio`,`fim` (datetime-local UTC identificado). Início/fim inclusivos; erroseinválidos e intervaloinvertido preservamfiltros. Representante temporário não consulta. Nome/loginatual naopção não muda identidade no registro. Operações de cadastro/correção/desativação/reativação/resetrepresentantes cobertas; demaisRF entram quandoimplementados. Ainda faltam valoresantes/depois de ocorrências e cobertura dos demaisdomínios, logoRF013parcial.

## Aceite

Autorização testada tanto noHTTP quantofachada; administrador configuraçãolocal semdefault; duascontasnomesiguaisentreperfis nãoescalam; contas/secretosausentesdasessãoehistórico; fluxocadastro→entregadireta→representantetroca/login→correção→desativação/reativação→reset, históricoestável, pesquisafiltros, CSRF, bloqueioserevogação. Auditoriaatransação e semmutaçãopelosperfis. CLI validado porConsolefictício/testes semsenhasreais. SQLem bancovazio, suítelocal/CI,visuallarguras390/1440. Nada de merge ou deploy.

## Próximo trabalho

Seguir áreas/residências/moradores/ocorrências pelas dependências aprovadas. Provedor SMS/WhatsApp continua lacuna de integraçãoreal, perguntar quando necessário e não simularentrega. Loginadministrativo e configuração não concedem ao admin as funções exclusivasdosrepresentantes.
