package estrutura;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractGraph {
    protected int numVertices;
    protected int numEdges;
    protected double[] vertexWeights;

    public AbstractGraph(int numVertices) {
        if (numVertices < 0) {
            throw new IllegalArgumentException("Número de vértices não pode ser negativo.");
        }
        this.numVertices = numVertices;
        this.numEdges = 0;
        this.vertexWeights = new double[numVertices];
        
        for (int i = 0; i < numVertices; i++) {
            this.vertexWeights[i] = 1.0;
        }
    }

    protected void validateVertex(int v) {
        if (v < 0 || v >= numVertices) {
            throw new IndexOutOfBoundsException("Vértice inválido: " + v + ". Deve estar entre 0 e " + (numVertices - 1));
        }
    }

    public int getVertexCount() {
        return numVertices;
    }

    public int getEdgeCount() {
        return numEdges;
    }

    public abstract boolean hasEdge(int u, int v);
    public abstract void addEdge(int u, int v);
    public abstract void removeEdge(int u, int v);
    public abstract void setEdgeWeight(int u, int v, double w);
    public abstract double getEdgeWeight(int u, int v);
    public abstract int getVertexInDegree(int u);
    public abstract int getVertexOutDegree(int u);
    public abstract List<Integer> getSuccessors(int v);
    public abstract List<Integer> getPredecessors(int v);

    public boolean isSucessor(int u, int v) {
        validateVertex(u);
        validateVertex(v);
        return hasEdge(u, v);
    }

    public boolean isPredecessor(int u, int v) {
        validateVertex(u);
        validateVertex(v);
        return hasEdge(v, u);
    }
    

    public boolean isDivergent(int u1, int v1, int u2, int v2) {
        validateVertex(u1); validateVertex(v1);
        validateVertex(u2); validateVertex(v2);
        // Divergente: Mesma origem (u1 == u2), destinos diferentes
        return (u1 == u2) && (v1 != v2) && hasEdge(u1, v1) && hasEdge(u2, v2);
    }

    public boolean isConvergent(int u1, int v1, int u2, int v2) {
        validateVertex(u1); validateVertex(v1);
        validateVertex(u2); validateVertex(v2);
        // Convergente: Mesmos destinos (v1 == v2), origens diferentes
        return (v1 == v2) && (u1 != u2) && hasEdge(u1, v1) && hasEdge(u2, v2);
    }

    public boolean isIncident(int u, int v, int x) {
        validateVertex(u); validateVertex(v); validateVertex(x);
        if (!hasEdge(u, v)) return false;
        return (x == u || x == v);
    }

    public void setVertexWeight(int v, double w) {
        validateVertex(v);
        this.vertexWeights[v] = w;
    }

    public double getVertexWeight(int v) {
        validateVertex(v);
        return this.vertexWeights[v];
    }

    public boolean isEmptyGraph() {
        return numEdges == 0;
    }

    public boolean isCompleteGraph() {
        // Num arestas deve ser n*(n-1) para grafo direcionado sem laços
        return numEdges == (numVertices * (numVertices - 1));
    }

    public boolean isConnected() {
        if (numVertices == 0) return true;
        
        boolean[] visited = new boolean[numVertices];
        List<Integer> queue = new ArrayList<>();
        
        // Começa do vértice 0
        visited[0] = true;
        queue.add(0);
        int visitedCount = 0;
        
        while (!queue.isEmpty()) {
            int u = queue.remove(0);
            visitedCount++;
            
            // Verifica sucessores (arestas saindo)
            for (int v : getSuccessors(u)) {
                if (!visited[v]) {
                    visited[v] = true;
                    queue.add(v);
                }
            }
            
            // Verifica antecessores (arestas chegando - para conectividade fraca)
            for (int v : getPredecessors(u)) {
                if (!visited[v]) {
                    visited[v] = true;
                    queue.add(v);
                }
            }
        }
        
        return visitedCount == numVertices;
    }

    public void exportToGEPHI(String path) {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<gexf xmlns=\"http://www.gexf.net/1.2draft\" version=\"1.2\">\n");
            writer.write("  <graph defaultedgetype=\"directed\">\n");
            
            // Nós
            writer.write("    <nodes>\n");
            for (int i = 0; i < numVertices; i++) {
                writer.write(String.format("      <node id=\"%d\" label=\"Vértice %d\" />\n", i, i));
            }
            writer.write("    </nodes>\n");
            
            // Arestas
            writer.write("    <edges>\n");
            int edgeId = 0;
            for (int u = 0; u < numVertices; u++) {
                List<Integer> successors = getSuccessors(u);
                for (int v : successors) {
                    double w = getEdgeWeight(u, v);
                    writer.write(String.format("      <edge id=\"%d\" source=\"%d\" target=\"%d\" weight=\"%.2f\" />\n", 
                        edgeId++, u, v, w).replace(',', '.'));
                }
            }
            writer.write("    </edges>\n");
            
            writer.write("  </graph>\n");
            writer.write("</gexf>\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}