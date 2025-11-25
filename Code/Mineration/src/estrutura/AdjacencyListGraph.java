package estrutura;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdjacencyListGraph extends AbstractGraph {
    private List<Map<Integer, Double>> adjOut;
    private List<Map<Integer, Double>> adjIn;

    public AdjacencyListGraph(int numVertices) {
        super(numVertices);
        adjOut = new ArrayList<>(numVertices);
        adjIn = new ArrayList<>(numVertices);
        for (int i = 0; i < numVertices; i++) {
            adjOut.add(new HashMap<>());
            adjIn.add(new HashMap<>());
        }
    }

    @Override
    public boolean hasEdge(int u, int v) {
        validateVertex(u); validateVertex(v);
        return adjOut.get(u).containsKey(v);
    }

    @Override
    public void addEdge(int u, int v) {
        validateVertex(u); validateVertex(v);
        if (u == v || hasEdge(u, v)) return;
        adjOut.get(u).put(v, 1.0);
        adjIn.get(v).put(u, 1.0);
        numEdges++;
    }

    @Override
    public void removeEdge(int u, int v) {
        validateVertex(u); validateVertex(v);
        if (hasEdge(u, v)) {
            adjOut.get(u).remove(v);
            adjIn.get(v).remove(u);
            numEdges--;
        }
    }

    @Override
    public void setEdgeWeight(int u, int v, double w) {
        validateVertex(u); validateVertex(v);
        if (u == v) return;
        if (!hasEdge(u, v)) numEdges++;
        adjOut.get(u).put(v, w);
        adjIn.get(v).put(u, w);
    }

    @Override
    public double getEdgeWeight(int u, int v) {
        validateVertex(u); validateVertex(v);
        if (!hasEdge(u, v)) throw new RuntimeException("Aresta não existe.");
        return adjOut.get(u).get(v);
    }

    @Override
    public int getVertexInDegree(int u) {
        validateVertex(u);
        return adjIn.get(u).size();
    }

    @Override
    public int getVertexOutDegree(int u) {
        validateVertex(u);
        return adjOut.get(u).size();
    }

    @Override
    public List<Integer> getSuccessors(int v) {
        validateVertex(v);
        return new ArrayList<>(adjOut.get(v).keySet());
    }

    @Override
    public List<Integer> getPredecessors(int v) {
        validateVertex(v);
        return new ArrayList<>(adjIn.get(v).keySet());
    }
}