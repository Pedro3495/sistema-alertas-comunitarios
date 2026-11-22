# SDD — cadastro de morador com envio SIMULADO da senha temporária

Fontes: RF001 regras 1–3 e 5, RF005 regras 2, 3, 4 e 6, RS001/RS002/RS003/RS005/RS006, RNF01/RNF02. Responsável humano: Pedro; executor: IA na branch `Pedro3495/rf005-credenciais-simuladas`. Decisão de Pedro (22/11/2026): usar um **simulador de provedor**, sem provedor real, rede, conta, SDK ou serviço pago. Envio simulado não é envio real nem integração entregue.

## Recorte

- Representante definitivo cadastra o morador em `/moradores/novo` (nome, CPF, celular, residência, canal único de credenciais SMS/WhatsApp e preferência de alertas SMS/WhatsApp/ambos). O formulário informa que o comprovante é só conferido e que, se um canal de alerta falhar, o outro será tentado (RF001 regra 3).
- No mesmo cadastro, o servidor gera uma senha temporária aleatória (10 caracteres, `SecureRandom`), grava somente o hash PBKDF2 e a validade de 24 h e a entrega à porta `EnvioCredenciais` pelo canal escolhido (RF005 regras 2 e 4, RS002).
- Falha (retorno `false` ou exceção do provedor) não desfaz o cadastro: a conta fica com `credenciais_pendentes`, a tela informa a falha e oferece **reenvio manual pelo mesmo canal**, sem troca automática (RF005 regras 3 e 6). Reenvio só para conta ativa com envio pendente; cada reenvio emite nova temporária, porque a anterior não é recuperável (RS002) — escolha técnica.
- Auditoria na mesma transação: `CADASTRAR_MORADOR #id`, `ENVIO_SIMULADO_SENHA_TEMPORARIA_<CANAL> #id` ou `FALHA_ENVIO_SIMULADO_SENHA_TEMPORARIA_<CANAL> #id`, sem senha, CPF ou telefone (RS005).
- Lista/pesquisa `/moradores` (nome, CPF ou celular) e detalhe `/moradores/{id}`, para reencontrar contas com envio pendente.

## Simulador

`EnvioCredenciais` (porta) e `EnvioCredenciaisSimulado` (única implementação, ativa por padrão): não faz chamada de rede, não grava nem registra a senha em log/console/banco e retorna sucesso. `alertas.envio-simulado.falhar=true` (ou `ALERTAS_ENVIO_SIMULADO_FALHAR=true`) simula falha para testar o caminho de erro. A senha não aparece em tela, log ou auditoria; como o login do morador ainda não existe, não há caixa de saída para teste manual.

## Persistência

`V10__credenciais_moradores.sql`: `senha_hash`, `senha_temporaria_expira_em` (ambos nulos ou ambos preenchidos) e `credenciais_pendentes` (padrão `true`, cobrindo linhas da base anterior sem credencial). V1–V9 preservadas. `database/schema.sql` reexportado sem dados.

## Fora do recorte (pendente)

Login do morador por CPF/senha, troca obrigatória no primeiro acesso, bloqueio por falhas e expiração em uso (RF005 regras 1, 4 e 5); recuperação por código (RF007); alteração/desativação/reativação pela tela (já existem no serviço); preferências pelo morador; provedor real e envio após o commit (marcado como `shortcut:` no serviço).

## Evidências locais — 22/11/2026

- `./scripts/verificar.sh` com JDK 21, PostgreSQL 17.10 isolado (`alertas-rf006-test`, loopback 55440, tmpfs, sem volume) em bases novas exclusivas: **248 testes, 0 falhas, 0 erros**, `BUILD SUCCESS`, `git diff --check` limpo — em `alertas_rf005_cred_1795377331` (sobre `6ce4513`) e, após o rebase sobre `feat/rf003-base-areas` (`ff54bfa`, mesma árvore), em `alertas_rf005_cred_1795377436`. Primeira execução (outra base nova) teve 2 falhas só de teste — a auditoria da residência criada pela fixture entrava na lista esperada; corrigido filtrando as ações do morador.
- Novos: `MoradoresHttpTests` (4, HTTP real com CSRF: cadastro → envio simulado → hash confere com a senha entregue à porta; senha ausente da página e da saída capturada do console; falha → aviso → reenvio pelo mesmo canal; erros de campo preservando dados; duplicidade; anônimo 403) e 4 em `MoradorServiceTests` (hash e 24 h, falha/exceção e reenvio com nova senha, conta desativada sem reenvio, simulador com e sem falha); negação do reenvio incluída nas verificações de perfil.
- Após a suíte, zero linhas restantes nas tabelas de fixtures. `schema.sql` restaurado com `psql -v ON_ERROR_STOP=1` em base vazia: 8 tabelas, colunas novas presentes.
- Word: SHA-256 `f47da7f8a689957ed9041011230cf887175911f832caaab8b017662e85b70658` conferido sem regravar. Sem JS alterado. Revisão de IA não equivale a aprovação humana.
