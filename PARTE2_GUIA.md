# 🚀 Guia da Parte 2 - Implementação da Estrutura de Grafos

## 📋 Visão Geral

A Parte 1 (mineração) já está **100% completa**. Agora vocês precisam implementar as classes de grafos em Java e carregar os dados dos CSVs gerados.

---

## 📁 Arquivos de Entrada (Já Prontos!)

Os grafos minerados estão em: `Code/Mineration/data/spring-projects/spring-boot/graphs/`

### Grafos Disponíveis:

| Arquivo | Descrição | Nós | Arestas |
|---------|-----------|-----|---------|
| `graph1_comments.csv` | Comentários em issues/PRs | 80 | 97 |
| `graph2_issue_closures.csv` | Fechamentos de issues | 38 | 40 |
| `graph3_pr_interactions.csv` | Reviews/Aprovações/Merges | 283 | 389 |
| `graph_integrated.csv` | **GRAFO PRINCIPAL** | 373 | 515 |

### Formato dos CSVs:

```csv
source,target,weight,count,tags
snicoll,nosan,68.0,17,pr_review:16;pr_approved:1
wilkinsona,onobc,164.0,41,pr_review:39;pr_changes_requested:2
dsyer,isopov,9.0,2,pr_merged:1;pr_comment:1
```

**Campos:**
- `source`: usuário de origem (String)
- `target`: usuário de destino (String)
- `weight`: peso da aresta (double) - **JÁ calculado!**
- `count`: quantidade de interações
- `tags`: tipos de interação (informativo)

---

## 🎯 O Que Vocês Precisam Fazer

### 1. Criar a Estrutura de Classes

```
src/
├── AbstractGraph.java           ← Classe abstrata base
├── AdjacencyMatrixGraph.java    ← Implementação com matriz
├── AdjacencyListGraph.java      ← Implementação com listas
└── GraphLoader.java             ← SUGESTÃO: Classe para carregar CSVs
```

### 2. Implementar a API Obrigatória

Todos os métodos listados no enunciado (getVertexCount, addEdge, etc.)

### 3. Carregar os Dados dos CSVs

**IMPORTANTE:** Os CSVs usam **nomes de usuários (Strings)**, mas a API usa **índices numéricos (int)**. 

Vocês precisarão criar um **mapeamento**:
```java
Map<String, Integer> userToIndex = new HashMap<>();
Map<Integer, String> indexToUser = new HashMap<>();
```

---

## 💡 Sugestão de Implementação - GraphLoader

Criei um exemplo de como carregar os CSVs:

```java
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class GraphLoader {
    
    /**
     * Carrega um grafo a partir de um CSV gerado na Parte 1
     * 
     * @param csvPath Caminho do arquivo CSV
     * @param graphType "matrix" ou "list"
     * @return Grafo carregado + mapeamentos
     */
    public static GraphData loadFromCSV(String csvPath, String graphType) throws IOException {
        // 1. Primeira passada: descobrir todos os usuários únicos
        Set<String> users = new HashSet<>();
        List<Edge> edges = new ArrayList<>();
        
        try (BufferedReader br = Files.newBufferedReader(Paths.get(csvPath))) {
            String line = br.readLine(); // Pular cabeçalho
            
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 3) continue;
                
                String source = parts[0].trim();
                String target = parts[1].trim();
                double weight = Double.parseDouble(parts[2].trim());
                
                users.add(source);
                users.add(target);
                edges.add(new Edge(source, target, weight));
            }
        }
        
        // 2. Criar mapeamentos
        Map<String, Integer> userToIndex = new HashMap<>();
        Map<Integer, String> indexToUser = new HashMap<>();
        int index = 0;
        for (String user : users) {
            userToIndex.put(user, index);
            indexToUser.put(index, user);
            index++;
        }
        
        // 3. Criar grafo do tipo escolhido
        int numVertices = users.size();
        AbstractGraph graph;
        
        if ("matrix".equalsIgnoreCase(graphType)) {
            graph = new AdjacencyMatrixGraph(numVertices);
        } else {
            graph = new AdjacencyListGraph(numVertices);
        }
        
        // 4. Adicionar arestas
        for (Edge e : edges) {
            int u = userToIndex.get(e.source);
            int v = userToIndex.get(e.target);
            graph.addEdge(u, v);
            graph.setEdgeWeight(u, v, e.weight);
        }
        
        return new GraphData(graph, userToIndex, indexToUser);
    }
    
    // Classes auxiliares
    static class Edge {
        String source, target;
        double weight;
        Edge(String s, String t, double w) {
            source = s; target = t; weight = w;
        }
    }
    
    public static class GraphData {
        public AbstractGraph graph;
        public Map<String, Integer> userToIndex;
        public Map<Integer, String> indexToUser;
        
        GraphData(AbstractGraph g, Map<String, Integer> u2i, Map<Integer, String> i2u) {
            graph = g;
            userToIndex = u2i;
            indexToUser = i2u;
        }
    }
}
```

---

## 📝 Exemplo de Uso

```java
public class Main {
    public static void main(String[] args) throws Exception {
        // Carregar o grafo integrado (recomendado!)
        String csvPath = "Code/Mineration/data/spring-projects/spring-boot/graphs/graph_integrated.csv";
        
        GraphLoader.GraphData data = GraphLoader.loadFromCSV(csvPath, "list");
        AbstractGraph graph = data.graph;
        
        // Agora você tem o grafo pronto para usar!
        System.out.println("Vértices: " + graph.getVertexCount());
        System.out.println("Arestas: " + graph.getEdgeCount());
        System.out.println("Grafo conexo? " + graph.isConnected());
        
        // Exemplo: encontrar grau de um usuário específico
        String user = "wilkinsona";
        if (data.userToIndex.containsKey(user)) {
            int index = data.userToIndex.get(user);
            System.out.println("Grau de saída de " + user + ": " + 
                               graph.getVertexOutDegree(index));
        }
        
        // Exportar para GEPHI
        graph.exportToGEPHI("output/grafo.gexf");
    }
}
```

---

## 🎨 Exportação para GEPHI

O método `exportToGEPHI()` deve gerar um arquivo no formato **GEXF** ou **GraphML**.

### Exemplo de formato GEXF:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<gexf xmlns="http://www.gexf.net/1.2draft" version="1.2">
  <graph mode="static" defaultedgetype="directed">
    <nodes>
      <node id="0" label="wilkinsona"/>
      <node id="1" label="snicoll"/>
    </nodes>
    <edges>
      <edge id="0" source="0" target="1" weight="164.0"/>
    </edges>
  </graph>
</gexf>
```

**Dica:** Use os mapas `indexToUser` para incluir os nomes reais dos usuários como labels!

---

## ⚠️ Pontos de Atenção

### 1. Grafo Direcionado
Os grafos da Parte 1 são **direcionados**:
- `addEdge(u, v)` cria aresta de u → v
- `addEdge(v, u)` seria necessário para criar aresta v → u (anti-paralela)

### 2. Grafo Simples
Os CSVs já garantem:
- ✅ Sem laços (source ≠ target)
- ✅ Sem multi-arestas (uma linha por par source→target)

### 3. Pesos
- **Arestas:** peso já calculado no campo `weight`
- **Vértices:** podem inicializar com 1.0 ou calcular baseado no grau

### 4. Conexidade
**Atenção:** O grafo integrado pode **NÃO ser conexo**! Existem componentes isoladas.

---

## 📊 Estatísticas dos Dados

### Grafo Integrado (`graph_integrated.csv`):
- **373 nós** (usuários únicos)
- **515 arestas** (interações direcionadas)
- **Peso mínimo:** 2.0 (1 comentário em PR)
- **Peso máximo:** 164.0 (wilkinsona → onobc: 41 interações!)
- **Tipos de interação:**
  - Comentários: peso 2.0
  - Issue comments: peso 3.0
  - Reviews/Aprovações: peso 4.0
  - Merges: peso 5.0

### Top 5 Arestas (mais colaboração):
```
wilkinsona → onobc: 164.0 (41 interações)
snicoll → izeye: 148.0 (37 interações)
wilkinsona → quaff: 96.0 (24 interações)
snicoll → onobc: 88.0 (22 interações)
snicoll → nosan: 68.0 (17 interações)
```

---

## 🔍 Testando Sua Implementação

### Casos de Teste Sugeridos:

```java
// 1. Teste básico
graph.addEdge(0, 1);
assert graph.hasEdge(0, 1) == true;
assert graph.getEdgeCount() == 1;

// 2. Teste de peso
graph.setEdgeWeight(0, 1, 5.0);
assert graph.getEdgeWeight(0, 1) == 5.0;

// 3. Teste de grafo direcionado
assert graph.isSucessor(0, 1) == true;
assert graph.isPredessor(1, 0) == true;

// 4. Teste de idempotência
graph.addEdge(0, 1); // Adicionar novamente
assert graph.getEdgeCount() == 1; // Não deve duplicar!

// 5. Teste com dados reais
GraphLoader.GraphData data = GraphLoader.loadFromCSV("graph_integrated.csv", "matrix");
assert data.graph.getVertexCount() == 373;
assert data.graph.getEdgeCount() == 515;
```

---

## 📚 Recursos Adicionais

### Documentação da Parte 1:
- `Code/Mineration/README.md` - Como os dados foram minerados
- `Code/Mineration/CORRECAO_IMPLEMENTADA.md` - Detalhes técnicos

### Visualização dos Dados:
1. Abra qualquer CSV no Excel/LibreOffice
2. Ordene por `weight` (decrescente) para ver principais colaborações
3. Filtre por `tags` para ver tipos específicos de interação

### GEPHI:
- Download: https://gephi.org/
- Tutorial: https://gephi.org/users/quick-start/
- Formato GEXF: https://gexf.net/format/

---

## ✅ Checklist da Implementação

- [ ] Classe `AbstractGraph` com API completa
- [ ] Classe `AdjacencyMatrixGraph` implementada
- [ ] Classe `AdjacencyListGraph` implementada
- [ ] Validação de índices (lançar exceções)
- [ ] Grafo simples (sem laços, sem multi-arestas)
- [ ] `addEdge()` idempotente
- [ ] Suporte a pesos de vértices e arestas
- [ ] Método `exportToGEPHI()` funcional
- [ ] Classe para carregar CSVs
- [ ] Testes com dados reais da Parte 1
- [ ] Código versionado no GitHub

---

## 💬 Dúvidas Frequentes

**Q: Qual grafo usar?**  
**A:** Recomendo o `graph_integrated.csv` - é o mais completo e tem todas as interações.

**Q: Como mapear usuários para índices?**  
**A:** Use dois `HashMap`: um String→Integer e outro Integer→String. Veja o exemplo no `GraphLoader`.

**Q: O grafo é conexo?**  
**A:** Provavelmente NÃO. Teste com `isConnected()`. Pode ter componentes isoladas.

**Q: Preciso implementar algoritmos (DFS, BFS, etc)?**  
**A:** Sim, provavelmente `isConnected()` precisará de DFS/BFS.

**Q: E se eu quiser testar com um grafo menor?**  
**A:** Use `graph1_comments.csv` (80 nós) ou `graph2_issue_closures.csv` (38 nós).

---

## 🎯 Próximos Passos

1. **Criar estrutura base:** AbstractGraph, AdjacencyMatrix, AdjacencyList
2. **Implementar API obrigatória:** Um método por vez, testando cada um
3. **Criar GraphLoader:** Para carregar os CSVs
4. **Testar com dados reais:** Carregar graph_integrated.csv
5. **Implementar exportToGEPHI:** Gerar arquivo GEXF
6. **Visualizar no GEPHI:** Ver se ficou correto
7. **Commitar no GitHub:** Versionar o código

---

**BOA SORTE! 🚀**

Os dados da Parte 1 estão prontos e validados. Agora é com vocês! 💪
