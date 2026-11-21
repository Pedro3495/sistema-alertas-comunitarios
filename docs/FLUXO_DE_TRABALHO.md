# Trabalhar em dupla com IA

**Atualização de 21/11/2026:** o colega não continuará. O fluxo atual é [SDD com coordenação de Pedro](SDD.md), com subagentes em worktrees separados e revisão técnica independente. As instruções abaixo documentam a organização inicial da dupla; a continuidade não depende de aguardar o colega ausente. Merge depende da autorização de Pedro e das proteções remotas, sem alegar que uma IA forneceu aprovação humana.

## Regra principal

Cada pessoa tem seu clone, seu PostgreSQL e sua branch. A IA do Pedro não edita a pasta do colega e vice-versa. O GitHub compartilha código por commits e PRs; não é uma pasta em edição simultânea.

## Liberar o acesso do colega

O responsável decidiu informar o usuário GitHub depois. No repositório, abrir Settings → Collaborators → Add people e convidar o usuário do colega com permissão de escrita (Write). O colega precisa aceitar o convite antes de conseguir publicar branches. Ler/clonar o código público não concede acesso de escrita.

## Uma tarefa do começo ao fim

1. Escolher issue/cartão e registrar responsável, revisor, RF e escopo. O colega não inicia a mesma tarefa.
2. Atualizar a base e criar a branch:

```sh
git switch main
git pull --ff-only
git switch -c feat/rf003-areas
```

Se houver arquivos modificados, finalizar ou guardar conscientemente esse trabalho antes de trocar de branch. Nunca descartar alterações automaticamente.

3. Dar à IA o prompt em `PROMPT_PARA_IA.md` com a spec e o escopo. Pedir pequenos passos e explicar cada regra antes de aceitar o código.
4. Rodar testes, conferir o diff e salvar apenas os arquivos da tarefa:

```sh
./scripts/verificar.sh
git diff --check
git status
git add caminho/do/arquivo
git commit -m "feat(RF003): implementar cadastro de áreas"
git push -u origin feat/rf003-areas
```

No Windows, trocar o comando de verificação por `./scripts/verificar.ps1`. Os nomes e mensagem acima são um exemplo; não anunciar a funcionalidade antes de implementá-la.

5. Abrir PR para `main`; o colega lê o diff, testa o fluxo, verifica a spec e pede correções ou aprova. CI deve estar verde. Um integrante faz o merge após a revisão do outro.
6. Atualizar a `main` no próprio computador. A próxima tarefa sempre começa da versão integrada.

## Como dividir

Dividir por fluxo de requisito, incluindo domínio, persistência, tela e testes. Evitar “um só faz telas e outro só banco”: os dois precisam compreender POO e o fluxo completo.

Trabalhos em `pom.xml`, segurança, objetos compartilhados e migrações devem ser combinados. Se duas tarefas dependem do mesmo contrato, integrar primeiro um PR pequeno com o contrato e depois trabalhar em paralelo. Não sincronizar copias manuais de arquivos.

## Várias IAs na mesma máquina

Preferir uma IA executora por vez para cada pessoa. Se realmente precisarem de execução paralela, usar um `git worktree` e branch separados por tarefa; cada cópia que execute a aplicação deve ter `.env`, portas e banco/volume próprios. Não assumir que trocar de chat isola arquivos.

## Em caso de conflito

Interromper a edição automática e identificar quais regras cada mudança pretendia atender. Combinar a resolução com o autor da outra tarefa, resolver o conflito e repetir os testes. Não usar `ours/theirs` indiscriminadamente nem `push --force`.

## Convenções

- Branches: `feat/rf006-registro`, `fix/rf007-codigo`, `docs/guia`.
- Commits: `feat(RF006): ...`, `fix(RF007): ...`, `docs: ...`.
- Uma issue pode ter mais de um PR pequeno; marcar o RF concluído somente quando o fluxo inteiro estiver integrado e revisado.
- Trello pode continuar como quadro de acompanhamento; incluir nele o link da issue/PR. Não manter regras diferentes em cartões e specs.

## Configurar a proteção da main

No repositório remoto, exigir PR, uma aprovação do colega e o check `Compilar e testar com PostgreSQL`; impedir force push e exclusão da `main`. A proteção deve ser ativada e conferida no GitHub, não apenas descrita aqui. Se a dupla ainda não tem o segundo acesso liberado, manter isso como pendência explícita.
