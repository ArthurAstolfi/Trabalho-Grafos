param(
    [string]$OwnerRepo = "$env:OWNER/$env:REPO_NAME",
    [string]$Token = $env:GITHUB_TOKEN
)

if (![string]::IsNullOrWhiteSpace($args[0])) { $OwnerRepo = $args[0] }
if ([string]::IsNullOrWhiteSpace($OwnerRepo) -or $OwnerRepo -notmatch "/") {
    Write-Host "Uso: ./scripts/regenerar.ps1 owner/repo" -ForegroundColor Yellow
    exit 1
}

Write-Host "[regenerar] Recriando dados para $OwnerRepo" -ForegroundColor Cyan

# Compila fontes (Etapa 1 e 2)
$src = Resolve-Path ./src
if (!(Test-Path ./bin)) { New-Item -ItemType Directory -Path ./bin | Out-Null }

javac -d bin `
    $src/App.java `
    $src/Mineracao.java `
    $src/GraphModel.java `
    $src/GraphBuilder.java `
    $src/BuildGraphs.java
if ($LASTEXITCODE -ne 0) { Write-Error "Falha ao compilar"; exit 1 }

# Limpa páginas antigas mantendo consolidados (se existirem)
$owner,$repo = $OwnerRepo.Split('/')
$dataRoot = Join-Path -Path (Resolve-Path ./data) -ChildPath "$owner/$repo"
if (Test-Path $dataRoot) {
    Write-Host "[regenerar] Limpando páginas brutas em $dataRoot" -ForegroundColor DarkGray
    Get-ChildItem -Path $dataRoot -File -Filter "*-*.json" | Remove-Item -Force -ErrorAction SilentlyContinue
    if (Test-Path (Join-Path $dataRoot 'pulls')) {
        Get-ChildItem -Path (Join-Path $dataRoot 'pulls') -Recurse -File | Remove-Item -Force -ErrorAction SilentlyContinue
        Remove-Item -Force -Recurse (Join-Path $dataRoot 'pulls') -ErrorAction SilentlyContinue
    }
}

# Define token se passado
if (![string]::IsNullOrWhiteSpace($Token)) { $env:GITHUB_TOKEN = $Token }

# Mineração
java -cp bin App $OwnerRepo
if ($LASTEXITCODE -ne 0) { Write-Error "Falha na mineração"; exit 1 }

# Construção dos grafos
java -cp bin BuildGraphs $OwnerRepo
if ($LASTEXITCODE -ne 0) { Write-Error "Falha na construção de grafos"; exit 1 }

Write-Host "[regenerar] Concluído. Consolidados e CSVs atualizados." -ForegroundColor Green
