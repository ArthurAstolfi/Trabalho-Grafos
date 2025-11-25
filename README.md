# Análise de Redes de Colaboração em Repositórios GitHub

Este projeto é uma ferramenta desenvolvida em **Java** para minerar, modelar e analisar as interações entre colaboradores de repositórios Open Source no GitHub (com foco no `spring-projects/spring-boot`).

O sistema utiliza **Teoria dos Grafos** para identificar influenciadores, detectar comunidades e analisar a estrutura de comunicação do projeto.

---

## Arquitetura do Projeto

O projeto foi desenvolvido em três etapas distintas, cada uma responsável por uma camada do processamento de dados:

### 1. Mineração de Dados (`src/mineracao`)
Coleta dados brutos da API do GitHub e os armazena em arquivos JSON.
- **Funcionalidades:**
  - Coleta de Issues (Abertura, Fechamento, Comentários).
  - Coleta de Pull Requests (Reviews, Merges, Comentários).
  - Gerenciamento automático de Paginação e Rate Limit da API.
  - Consolidação de dados incrementais com checkpoints.

### 2. Construção dos Grafos (`src/mineracao`)
Processa os arquivos JSON minerados e transforma as interações em modelos de grafos ponderados.
- **Pesos das Interações:**
  - Comentários: Peso 2.0
  - Abertura de Issue: Peso 3.0
  - Review/Aprovação: Peso 4.0
  - Merge: Peso 5.0
- **Saída:** Arquivos `.csv` (lista de arestas) prontos para carregamento.

### 3. Análise e Métricas (`src/app`, `src/analise`)
Carrega o grafo em memória usando estruturas de dados próprias e executa algoritmos complexos.
- **Estruturas de Dados:** Implementação própria de `AdjacencyListGraph` e `AdjacencyMatrixGraph`.
- **Algoritmos Implementados:**
  - **Centralidade:** PageRank, Betweenness (Brandes), Closeness e Grau.
  - **Estrutura:** Densidade, Assortatividade e Coeficiente de Aglomeração.
  - **Comunidades:** Detecção via Girvan-Newman e identificação de Bridging Ties (Laços de Ponte).
- **Exportação:** Gera arquivos `.gexf` para visualização no **Gephi**.

---

## Estrutura de Pacotes

```text
src/
├── mineracao/           # Etapa 1 e 2: Coleta e Construção
│   ├── App.java         # Executor da Mineração
│   ├── BuildGraphs.java # Executor da Construção dos CSVs
│   ├── Mineracao.java   # Cliente HTTP e Lógica de API
│   └── ...
├── estrutura/           # Core: Estruturas de Dados do Grafo
│   ├── AbstractGraph.java
│   ├── AdjacencyListGraph.java
│   └── AdjacencyMatrixGraph.java
├── analise/             # Algoritmos de Redes Complexas
│   ├── GraphMetrics.java           # PageRank, Betweenness, Closeness
│   ├── GraphStructureMetrics.java  # Densidade, Clustering, Assortatividade
│   └── GraphCommunityMetrics.java  # Girvan-Newman, Bridging Ties
├── io/                  # Entrada/Saída
│   └── GraphLoader.java # Carregador de CSV para Grafo
└── app/                 # Etapa 3: Aplicação Principal
    ├── Main.java        # Ponto de Entrada da Análise
    └── AnaliseService.java # Orquestrador das Métricas

## Pré-requisitos

* **Java 8+**
* **Token do GitHub** (para a etapa de mineração)
* **Gephi** (opcional, para visualizar o arquivo `.gexf` gerado)
---
