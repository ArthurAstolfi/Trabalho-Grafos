# Mineration – Etapas 1 e 2

Coletor simples para minerar dados brutos (JSON) de um repositório GitHub usando a API REST.

## Pré-requisitos
- Java 11+ (utiliza HttpClient nativo)
- Opcional: variável de ambiente `GITHUB_TOKEN` (recomendado para limites de rate maiores)

## Como usar (PowerShell no Windows)

1) Compile:

```
pushd "c:\Users\Arthur\Documents\Faculdade\Trabalho Grafos\Code\Mineration"
if (!(Test-Path bin)) { New-Item -ItemType Directory -Path bin | Out-Null }
javac -d bin src\App.java src\Mineracao.java
popd
```

2) Execute, informando `owner/repo` e opcionalmente a pasta de saída (padrão `data`). Exemplo:

```
# opcional: definir token
$env:GITHUB_TOKEN = "seu_token_aqui"

# executar (salva em ./data)
java -cp "c:\Users\Arthur\Documents\Faculdade\Trabalho Grafos\Code\Mineration\bin" App octocat/Hello-World

# ou definindo outra pasta de saída
java -cp "c:\Users\Arthur\Documents\Faculdade\Trabalho Grafos\Code\Mineration\bin" App octocat/Hello-World "C:\\tmp\\saida"
```

Arquivos serão salvos em `data/<owner>/<repo>/` (ou na pasta que você indicar), com um arquivo `repo.json` e arquivos paginados para coleções, como `commits-1.json`, `issues-1.json`, etc.

## Endpoints coletados (Etapa 1)
 - `GET /repos/{owner}/{repo}/issues/comments` (paginado) — comentários em issues
 - `GET /repos/{owner}/{repo}/issues/events` (paginado) — eventos (inclui fechamento de issues)
 - `GET /repos/{owner}/{repo}/pulls/comments` (paginado) — comentários de review em PR
 - `GET /repos/{owner}/{repo}/pulls/{number}` — detalhe da PR (inclui `merged_by`)
 - `GET /repos/{owner}/{repo}/pulls/{number}/reviews` (paginado) — reviews/aprovações
Arquivos serão salvos em `data/<owner>/<repo>/` (ou na pasta que você indicar), com:
- `repo.json`
- arquivos paginados (ex.: `issues-1.json`, `issues-2.json`, ...)
- arquivos consolidados por categoria (ex.: `issues.json`, `issue-comments.json`, `pulls.json`, ...)

Para PRs individuais, há uma pasta `pulls/<numero>/` com `pull.json` e `reviews-*.json`, além de `reviews.json` consolidado. Após a coleta, geramos ainda:

- `pull-details.json` (array contendo todos os `pull.json`)
- `pull-reviews.json` (array único concatenando todas as reviews de todas as PRs)

Esses dois arquivos facilitam análise agregada das revisões e merges sem iterar diretórios.

## Arquivos consolidados principais

| Arquivo | Conteúdo | Uso nos Grafos |
|---------|----------|----------------|
| `issues.json` | Lista de issues e PRs (GitHub trata PR como issue com campo `pull_request`) | Identificar autor e distinguir issue vs PR |
| `issue-comments.json` | Comentários em issues | Grafo 1 (comentários) |
| `issue-events.json` | Eventos de issues (inclui `closed`) | Grafo 2 (fechamentos) |
| `pulls.json` | Lista de PRs (autor) | Mapear autor de cada PR |
| `pr-review-comments.json` | Comentários de review em PR (linha de código) | Grafo 1 (comentários em PR) |
| `pull-details.json` | Detalhes de cada PR (`merged_by`, `merged`) | Grafo 3 (merges) |
| `pull-reviews.json` | Todas as reviews (estado: approved / changes_requested / comment) | Grafo 3 (revisões/aprovações) |

## Etapa 2 – Construção dos Grafos

Após a mineração, execute:

```
javac -d bin src\GraphModel.java src\GraphBuilder.java src\BuildGraphs.java
java -cp bin BuildGraphs owner/repo
```

Saída: diretório `data/owner/repo/graphs/` com:

- `graph1_comments.csv`
- `graph2_issue_closures.csv`
- `graph3_pr_interactions.csv`
- `graph_integrated.csv`

Formato das linhas CSV: `source,target,weight,count,tags` onde `tags` traz cada tipo de interação com sua contagem (ex: `issue_comment:5;pr_review_comment:2`).

## Pesos e Modelagem

- Comentário em issue: 3
- Comentário em PR / review comment: 2
- Fechamento de issue por outro usuário: 3
- Review/aprovação de PR: 4
- Merge de PR: 5

O grafo integrado soma pesos e mantém tags combinadas.

## Por que não versionar páginas brutas?

Os arquivos paginados (`*-<n>.json`) podem exceder gigabytes para repositórios grandes. Versionamos apenas os consolidados para:
1. Reduzir tamanho do repositório.
2. Facilitar análise direta.
3. Evitar poluição do histórico com milhares de pequenas mudanças.

Para reconstruir:

```
java -cp bin App owner/repo
java -cp bin BuildGraphs owner/repo
```

Certifique-se de ter `GITHUB_TOKEN` definido para evitar limites baixos de rate.


## Observações
- Este projeto realiza apenas a coleta (Etapa 1). Transformações, construção de grafos e análises ficam para as próximas etapas.
- Se receber HTTP 403 com mensagem de rate limit, espere o tempo indicado no cabeçalho `X-RateLimit-Reset` ou use/troque o `GITHUB_TOKEN`.
