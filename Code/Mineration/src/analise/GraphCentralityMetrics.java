package analise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Stack;

import estrutura.AbstractGraph;

public class GraphCentralityMetrics {
    // --- 1. Centralidade de Grau (Normalizada) ---
    public static Map<Integer, Double> calculateDegreeCentrality(AbstractGraph graph) {
        Map<Integer, Double> centrality = new HashMap<>();
        int n = graph.getVertexCount();
        if (n <= 1)
            return centrality;

        for (int i = 0; i < n; i++) {
            double degree = graph.getVertexInDegree(i) + graph.getVertexOutDegree(i);
            centrality.put(i, degree / (n - 1));
        }
        return centrality;
    }

    // --- 2. Centralidade de Proximidade (Closeness) - Via BFS ---
    public static Map<Integer, Double> calculateClosenessCentrality(AbstractGraph graph) {
        Map<Integer, Double> closeness = new HashMap<>();
        int n = graph.getVertexCount();

        for (int s = 0; s < n; s++) {
            double totalDist = 0;
            int reachable = 0;

            int[] dist = new int[n];
            Arrays.fill(dist, -1);
            Queue<Integer> queue = new LinkedList<>();

            queue.add(s);
            dist[s] = 0;

            while (!queue.isEmpty()) {
                int u = queue.poll();
                for (int v : graph.getSuccessors(u)) {
                    if (dist[v] < 0) {
                        dist[v] = dist[u] + 1;
                        totalDist += dist[v];
                        reachable++;
                        queue.add(v);
                    }
                }
            }

            if (totalDist > 0) {
                double val = (double) reachable / totalDist;
                val *= (double) reachable / (n - 1);
                closeness.put(s, val);
            } else {
                closeness.put(s, 0.0);
            }
        }
        return closeness;
    }

    // --- 3. PageRank (Método Iterativo) ---
    public static Map<Integer, Double> calculatePageRank(AbstractGraph graph) {
        return calculatePageRank(graph, 0.85, 20, 1e-6);
    }

    public static Map<Integer, Double> calculatePageRank(AbstractGraph graph, double damping, int maxIter, double tol) {
        int n = graph.getVertexCount();
        Map<Integer, Double> pr = new HashMap<>();
        if (n == 0)
            return pr;

        for (int i = 0; i < n; i++)
            pr.put(i, 1.0 / n);

        double[] outWeightSum = new double[n];
        for (int i = 0; i < n; i++) {
            for (int v : graph.getSuccessors(i)) {
                outWeightSum[i] += graph.getEdgeWeight(i, v);
            }
        }

        for (int iter = 0; iter < maxIter; iter++) {
            Map<Integer, Double> newPr = new HashMap<>();
            double baseVal = (1.0 - damping) / n;
            for (int i = 0; i < n; i++)
                newPr.put(i, baseVal);

            for (int i = 0; i < n; i++) {
                double currentPr = pr.get(i);

                if (outWeightSum[i] == 0) {
                    double share = (damping * currentPr) / n;
                    for (int j = 0; j < n; j++)
                        newPr.put(j, newPr.get(j) + share);
                } else {
                    for (int v : graph.getSuccessors(i)) {
                        double weight = graph.getEdgeWeight(i, v);
                        double share = (damping * currentPr * weight) / outWeightSum[i];
                        newPr.put(v, newPr.get(v) + share);
                    }
                }
            }

            double err = 0;
            for (int i = 0; i < n; i++)
                err += Math.abs(newPr.get(i) - pr.get(i));
            pr = newPr;
            if (err < tol)
                break;
        }
        return pr;
    }

    // --- 4. Intermediação (Betweenness) - Algoritmo de Brandes ---
    public static Map<Integer, Double> calculateBetweennessCentrality(AbstractGraph graph) {
        int n = graph.getVertexCount();
        double[] cb = new double[n];

        for (int s = 0; s < n; s++) {
            Stack<Integer> stack = new Stack<>();
            List<List<Integer>> P = new ArrayList<>();
            for (int i = 0; i < n; i++)
                P.add(new ArrayList<>());

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

                for (int w : graph.getSuccessors(v)) {
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
                    delta[v] += (sigma[v] / sigma[w]) * (1.0 + delta[w]);
                }
                if (w != s) {
                    cb[w] += delta[w];
                }
            }
        }

        Map<Integer, Double> result = new HashMap<>();
        for (int i = 0; i < n; i++)
            result.put(i, cb[i]);
        return result;
    }
}