package estrutura;

import java.util.ArrayList;
import java.util.List;

public class AdjacencyMatrixGraph extends AbstractGraph {
    private Double[][] matrix; // Null indica sem aresta, valor indica peso

    public AdjacencyMatrixGraph(int numVertices) {
        super(numVertices);
        this.matrix = new Double[numVertices][numVertices];
    }

    @Override
    public boolean hasEdge(int u, int v) {
        validateVertex(u);
        validateVertex(v);
        return matrix[u][v] != null;
    }

    @Override
    public void addEdge(int u, int v) {
        validateVertex(u);
        validateVertex(v);
        
        if (u == v) return; // Grafo simples: sem laços
        if (hasEdge(u, v)) return; // Grafo simples: sem múltiplas arestas (idempotente)

        matrix[u][v] = 1.0; // Peso padrão
        numEdges++;
    }

    @Override
    public void removeEdge(int u, int v) {
        validateVertex(u);
        validateVertex(v);
        
        if (hasEdge(u, v)) {
            matrix[u][v] = null;
            numEdges--;
        }
    }

    @Override
    public void setEdgeWeight(int u, int v, double w) {
        validateVertex(u);
        validateVertex(v);
        
        // Se a aresta não existe, cria
        if (!hasEdge(u, v)) {
            if (u == v) return;
            numEdges++;
        }
        matrix[u][v] = w;
    }

    @Override
    public double getEdgeWeight(int u, int v) {
        validateVertex(u);
        validateVertex(v);
        if (!hasEdge(u, v)) {
            throw new RuntimeException("Aresta não existe: " + u + " -> " + v);
        }
        return matrix[u][v];
    }

    @Override
    public int getVertexInDegree(int u) {
        validateVertex(u);
        int degree = 0;
        for (int i = 0; i < numVertices; i++) {
            if (matrix[i][u] != null) {
                degree++;
            }
        }
        return degree;
    }

    @Override
    public int getVertexOutDegree(int u) {
        validateVertex(u);
        int degree = 0;
        for (int i = 0; i < numVertices; i++) {
            if (matrix[u][i] != null) {
                degree++;
            }
        }
        return degree;
    }

    @Override
    public List<Integer> getSuccessors(int v) {
        validateVertex(v);
        List<Integer> succ = new ArrayList<>();
        for (int i = 0; i < numVertices; i++) {
            if (matrix[v][i] != null) succ.add(i);
        }
        return succ;
    }

    @Override
    public List<Integer> getPredecessors(int v) {
        validateVertex(v);
        List<Integer> pred = new ArrayList<>();
        for (int i = 0; i < numVertices; i++) {
            if (matrix[i][v] != null) pred.add(i);
        }
        return pred;
    }
}