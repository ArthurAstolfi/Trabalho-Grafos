# Mineration (Etapa 1)

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

## Endpoints coletados
 - `GET /repos/{owner}/{repo}/issues/comments` (paginado) — comentários em issues
 - `GET /repos/{owner}/{repo}/issues/events` (paginado) — eventos (inclui fechamento de issues)
 - `GET /repos/{owner}/{repo}/pulls/comments` (paginado) — comentários de review em PR
 - `GET /repos/{owner}/{repo}/pulls/{number}` — detalhe da PR (inclui `merged_by`)
 - `GET /repos/{owner}/{repo}/pulls/{number}/reviews` (paginado) — reviews/aprovações
Arquivos serão salvos em `data/<owner>/<repo>/` (ou na pasta que você indicar), com:
- `repo.json`
- arquivos paginados (ex.: `issues-1.json`, `issues-2.json`, ...)
- arquivos consolidados por categoria (ex.: `issues.json`, `issue-comments.json`, `pulls.json`, ...)

Para PRs individuais, há uma pasta `pulls/<numero>/` com `pull.json` e `reviews-*.json`, além de `reviews.json` consolidado.

## Observações
- Este projeto realiza apenas a coleta (Etapa 1). Transformações, construção de grafos e análises ficam para as próximas etapas.
- Se receber HTTP 403 com mensagem de rate limit, espere o tempo indicado no cabeçalho `X-RateLimit-Reset` ou use/troque o `GITHUB_TOKEN`.
