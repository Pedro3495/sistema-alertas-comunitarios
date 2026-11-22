# SDD — RF005: acesso do morador (login por CPF, primeira troca e bloqueio)

Fontes: RF005 regras 1, 2, 4, 5 e "Alteração"; RS001, RS002, RS009 (limite abaixo); RF001 (desativação bloqueia acesso). Word aprovado apenas lido. Responsável humano: Pedro; executor: IA na branch `Pedro3495/rf005-acesso-moradores`, base `feat/rf003-base-areas` (PR #16 aberto). Reaproveita o padrão do acesso de representantes ([RF005-representantes](RF005-representantes.md)) e o cadastro com senha temporária simulada ([RF005-credenciais-simuladas](RF005-credenciais-simuladas.md)).

## Regras atendidas

| Regra (fonte) | Implementação |
|---|---|
| Morador entra com CPF e senha (RF005 r1) | Chain própria `/morador/**`: `GET/POST /morador/entrar` (campos `cpf`, `senha`, CSRF). Pontos/traço do CPF digitado são ignorados. |
| Temporária do cadastro, troca obrigatória no 1º acesso (RF005 r2, Alteração; RS002) | Temporária correta → autoridade `MORADOR_TROCAR_SENHA`, só `/morador/trocar-senha`. Troca grava hash PBKDF2, desmarca a troca, encerra a sessão e pede login com a nova senha. |
| Mínimo 8 caracteres, sem composição; temporária vale 24 h (RF005 r4) | Mesmo validador do representante (`RepresentanteService.validarSenha`, agora público). Vencida quando `agora >= expiração`; não conta como falha. A senha definitiva não vence. |
| 5 falhas consecutivas por conta → bloqueio de 15 min (RF005 r5, RS001) | `BloqueioLogin` (`@Embeddable` comum a morador, representante e administrador). Inclui senha correta durante o bloqueio; tentativas no bloqueio não estendem o prazo; no limite exato volta a aceitar; sucesso zera. Falha confirmada em transação própria. |
| Conta desativada não acessa (RF005 r5, RF001) | Login recusado; sessão aberta é revogada na próxima requisição (`SessaoValidadaFilter`). |
| Senha não consultável (RS002) | Sessão guarda `AcessoMorador(id, versão, troca)` sem senha/hash/CPF/telefone. Mensagem de erro genérica não revela se o CPF existe. |

Revogação de sessão por versão (controle técnico, não campo do Word): reenvio de temporária, desativação (reativação não restaura) e troca de CPF.

## Persistência

`V11__controle_acesso_moradores.sql`: `troca_senha_obrigatoria` (padrão `true`), `falhas_login` (0–5), `bloqueado_ate`, `versao_credencial` (≥ 0). V1–V10 preservadas. `database/schema.sql` reexportado sem dados.

## Fora do recorte (pendente)

- Recuperação de acesso por código (RF007/RS003) — o texto aprovado manda a redefinição do morador seguir o RF007.
- Temporária **entregue** com sucesso que vence sem uso: o reenvio manual (RF005 r6) só cobre falha de envio; as fontes não definem nova emissão nesse caso. Hoje o morador não consegue entrar até existir o RF007 ou decisão da dupla.
- Funções do morador após o login (RF006 morador, RF008 etc.): `/morador` é só a página de acesso.
- RS009 (HTTPS) é de implantação; testes usam HTTP em loopback.

## Evidências locais — 22/11/2026

- `./scripts/verificar.sh` com JDK 21 em PostgreSQL 17.10 isolado (`alertas-rf006-test`, loopback 55440), base nova exclusiva `alertas_rf005_mor_1795378055`: **255 testes, 0 falhas, 0 erros**, `BUILD SUCCESS`, `git diff --check` limpo. Execução anterior em outra base nova (`alertas_rf005_mor_1795377998`) teve os mesmos 255 testes verdes, mas o `git diff --check` falhou por linha em branco final no `schema.sql` exportado — corrigido.
- Flyway aplicou V10 e V11 com sucesso; após a suíte, 0 linhas restantes em `moradores`, `representantes`, `residencias` e `areas_risco`.
- Novos: `AutenticacaoMoradorServiceTests` (5: temporária → troca → definitiva, mínimo 8, definitiva não vence; 24 h no limite exato sem contar falha; 5 falhas, bloqueio 15 min no limite, prazo não estendido, sucesso zera; desativada/CPF inexistente/sem senha; reenvio, desativação+reativação e troca de CPF revogam sessão) e 2 em `MoradoresHttpTests` (HTTP real com CSRF: cadastro pelo representante → senha capturada do envio simulado → login com CPF formatado → 403 em `/morador`, `/moradores`, `/representante` durante a troca → troca → sessão encerrada → temporária recusada → definitiva → sair → desativação revoga sessão; 5 falhas persistidas bloqueiam a senha correta com mensagem genérica que preserva o CPF e não devolve senha). Testes de representante e administrador continuam verdes com o `BloqueioLogin` comum.
- Word: SHA-256 `f47da7f8a689957ed9041011230cf887175911f832caaab8b017662e85b70658` conferido sem regravar. Revisão de IA não equivale a aprovação humana.
