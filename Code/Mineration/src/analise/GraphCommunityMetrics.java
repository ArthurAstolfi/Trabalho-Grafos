package analise;

import estrutura.AbstractGraph;
import java.util.*;

public class GraphCommunityMetrics {

    private static class EdgeKey {
        final int u, v;
        public EdgeKey(int u, int v) {
            this.u = Math.min(u, v);
            this.v = Math.max(u, v);
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EdgeKey)) return false;
            EdgeKey edgeKey = (EdgeKey) o;
            return u == edgeKey.u && v == edgeKey.v;
        }
        @Override
        public int hashCode() { return Objects.hash(u, v); }
    }

    /**
     * 1. Algoritmo de Girvan-Newman para Detecção de Comunidades.
     * Remove iterativamente a aresta com maior Betweenness Centrality para quebrar o grafo em clusters.
     * * @param originalGraph O grafo original
     * @param maxSplits Número de cortes (divisões) a realizar
     * @return Lista de Comunidades (onde cada comunidade é uma Lista de IDs de vértices)
     */
    public static List<List<Integer>> detectCommunitiesGirvanNewman(AbstractGraph originalGraph, int maxSplits) {
        int n = originalGraph.getVertexCount();

        List<Set<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) adj.add(new HashSet<>());

        for (int u = 0; u < n; u++) {
            for (int v : originalGraph.getSuccessors(u)) {
                adj.get(u).add(v);
                adj.get(v).add(u);
            }
        }

        for (int split = 0; split < maxSplits; split++) {
            Map<EdgeKey, Double> edgeBetweenness = calculateEdgeBetweenness(adj, n);

            if (edgeBetweenness.isEmpty()) break;

            EdgeKey maxEdge = null;
            double maxVal = -1.0;

            for (Map.Entry<EdgeKey, Double> entry : edgeBetweenness.entrySet()) {
                if (entry.getValue() > maxVal) {
                    maxVal = entry.getValue();
                    maxEdge = entry.getKey();
                }
            }

            if (maxEdge == null) break;

            adj.get(maxEdge.u).remove(maxEdge.v);
            adj.get(maxEdge.v).remove(maxEdge.u);
        }

        return getConnectedComponents(adj, n);
    }

    /**
     * 2. Identificação de Bridging Ties (Laços de Ponte).
     * Identifica arestas do grafo ORIGINAL que conectam nós de comunidades diferentes.
     */
    public static List<String> findBridgingTies(AbstractGraph graph, List<List<Integer>> communities) {
        List<String> bridges = new ArrayList<>();
        
        int[] nodeCommunityMap = new int[graph.getVertexCount()];
        Arrays.fill(nodeCommunityMap, -1);

        for (int commId = 0; commId < communities.size(); commId++) {
            for (int node : communities.get(commId)) {
                nodeCommunityMap[node] = commId;
            }
        }

        int n = graph.getVertexCount();
        for (int u = 0; u < n; u++) {
            int commU = nodeCommunityMap[u];
            if (commU == -1) continue;

            for (int v : graph.getSuccessors(u)) {
                int commV = nodeCommunityMap[v];

                if (commV != -1 && commU != commV) {
                    bridges.add(u + " -> " + v + " (Comunidade " + commU + " para " + commV + ")");
                }
            }
        }
        return bridges;
    }

    private static Map<EdgeKey, Double> calculateEdgeBetweenness(List<Set<Integer>> adj, int n) {
        Map<EdgeKey, Double> edgeScores = new HashMap<>();

        for (int s = 0; s < n; s++) {
            Stack<Integer> stack = new Stack<>();
            List<List<Integer>> P = new ArrayList<>();
            for (int i = 0; i < n; i++) P.add(new ArrayList<>());
            
            double[] sigma = new double[n];
            int[] dist = new int[n];
            Arrays.fill(dist, -1);
            
            sigma[s] = 1.0;
            dist[s] = 0;
            Queue<Integer> queue = new LinkedList<>();
            queue.add(s);

            while (!queue.isEmpty()) {
                int v = queue.poll();
                stack.push(v);

                for (int w : adj.get(v)) {
                    if (dist[w] < 0) {
                        dist[w] = dist[v] + 1;
                        queue.add(w);
                    }
                    if (dist[w] == dist[v] + 1) {
                        sigma[w] += sigma[v];
                        P.get(w).add(v);
                    }
                }
            }

            double[] delta = new double[n];
            while (!stack.isEmpty()) {
                int w = stack.pop();
                for (int v : P.get(w)) {
                    double c = (sigma[v] / sigma[w]) * (1.0 + delta[w]);
                    EdgeKey edge = new EdgeKey(v, w);
                    edgeScores.put(edge, edgeScores.getOrDefault(edge, 0.0) + c);
                    delta[v] += c;
                }
            }
        }
        return edgeScores;
    }

    private static List<List<Integer>> getConnectedComponents(List<Set<Integer>> adj, int n) {
        List<List<Integer>> components = new ArrayList<>();
        boolean[] visited = new boolean[n];

        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                List<Integer> component = new ArrayList<>();
                Queue<Integer> q = new LinkedList<>();
                q.add(i);
                visited[i] = true;
                component.add(i);

                while (!q.isEmpty()) {
                    int u = q.poll();
                    for (int v : adj.get(u)) {
                        if (!visited[v]) {
                            visited[v] = true;
                            component.add(v);
                            q.add(v);
                        }
                    }
                }
                components.add(component);
            }
        }
        return components;
    }
}