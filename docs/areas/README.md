# Base de áreas de risco — tarefa #14

Este texto registra o recorte inicial. A entrega seguinte acrescenta [gestão web, autorização, auditoria e vínculo com residências](../residencias/README.md). As limitações abaixo são as da base original.

Primeiro recorte do RF003: domínio, cadastro, alteração e pesquisa com persistência. O requisito completo permanece pendente de interface, autorização, auditoria e exclusão condicionada aos vínculos reais de residências e ocorrências.

## Como o código se organiza

- `AreaRisco`: objeto que guarda nome e descrição da localização/limites. Os campos são privados; a criação e a alteração exigem conteúdo. Se um campo da alteração for inválido, nenhum dos dois muda.
- `AreaRiscoRepository`: contrato Spring Data JPA para persistir e pesquisar.
- `AreaRiscoService`: coordena cadastro e alteração em transações; pesquisa em transação de leitura. A alteração mantém o identificador, sem criar outra área.
- `V2__criar_areas_risco.sql`: cria a tabela; `database/schema.sql` contém a exportação de estrutura para entrega.

O identificador numérico é técnico, gerado no banco. Os campos de conteúdo usam `TEXT`: o requisito não define limite de caracteres ou nome exclusivo, então esses limites não foram acrescentados. A edição não redistribui residências; esse módulo ainda não implementa vínculos com residências.

## Pesquisa

Escolha técnica deste recorte: correspondência por trecho do nome, sem distinguir maiúsculas/minúsculas. `%` e `_` digitados são texto literal, não coringas. O contrato do serviço exige um termo preenchido e retorna lista vazia quando não encontra correspondência. Isso não cria novos campos ou regras de unicidade no cadastro.

## Verificar e explicar

```sh
docker compose up -d --wait db
./scripts/verificar.sh
```

Os testes de domínio verificam os campos obrigatórios e a alteração atômica. Os testes com PostgreSQL verificam cadastro, recuperação, edição com a mesma identidade, nomes repetidos permitidos e pesquisa, inclusive caracteres especiais. A edição é testada sem transação externa do teste: o próprio serviço precisa confirmar a gravação. Os dados criados pelo teste são desfeitos ou removidos ao final.

Na apresentação, explique por que não existem setters públicos e por que ambos os campos são validados antes da atribuição. Explique também a diferença entre o objeto de domínio, o serviço que coordena a operação e o repositório que acessa o banco.

## Integração da dupla

Integrar V2 antes da V3 reservada à base de representantes (#15). Depois da integração de V2, o colega atualiza sua branch e repete os testes antes de integrar V3. Não mudar o Flyway para aceitar versões fora de ordem.

Não foi criado controller ou liberada rota HTTP para áreas. Não há exclusão provisória que ignore vínculos, autorização simulada ou auditoria fictícia. Esses itens continuam no RF003 e serão integrados nos próximos recortes.
