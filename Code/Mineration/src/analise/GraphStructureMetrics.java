package analise;

import estrutura.AbstractGraph;
import java.util.*;

public class GraphStructureMetrics {

    /**
     * 1. Densidade da Rede
     * Fórmula: |E| / (|V| * (|V| - 1)) para grafos direcionados.
     * Indica o quão conectada a rede é em relação ao máximo possível.
     */
    public static double calculateDensity(AbstractGraph graph) {
        int n = graph.getVertexCount();
        int e = graph.getEdgeCount();

        if (n <= 1)
            return 0.0;

        double maxEdges = (double) n * (n - 1);
        return e / maxEdges;
    }

    /**
     * 2. Coeficiente de Aglomeração Médio (Average Clustering Coefficient)
     * Mede a tendência de formação de triângulos (amigos de amigos são amigos).
     * Para grafos direcionados, consideramos a vizinhança total (entrada + saída).
     */
    public static double calculateAverageClusteringCoefficient(AbstractGraph graph) {
        int n = graph.getVertexCount();
        if (n == 0)
            return 0.0;

        double totalClustering = 0.0;

        for (int i = 0; i < n; i++) {
            totalClustering += calculateLocalClustering(graph, i);
        }

        return totalClustering / n;
    }

    private static double calculateLocalClustering(AbstractGraph graph, int v) {
        Set<Integer> neighbors = new HashSet<>();
        neighbors.addAll(graph.getSuccessors(v));
        neighbors.addAll(graph.getPredecessors(v));

        neighbors.remove(v);

        int k = neighbors.size();
        if (k < 2)
            return 0.0;

        int linksBetweenNeighbors = 0;
        for (int neighborA : neighbors) {
            for (int neighborB : neighbors) {
                if (neighborA == neighborB)
                    continue;

                if (graph.hasEdge(neighborA, neighborB)) {
                    linksBetweenNeighbors++;
                }
            }
        }

        double possibleLinks = (double) k * (k - 1);

        return linksBetweenNeighbors / possibleLinks;
    }

    /**
     * 3. Assortatividade de Grau (Degree Assortativity)
     * Mede a correlação de Pearson entre os graus dos nós conectados.
     * r > 0: Redes assortativas (Hubs se conectam com Hubs).
     * r < 0: Redes disassortativas (Hubs se conectam com nós pequenos).
     */
    public static double calculateAssortativity(AbstractGraph graph) {
        List<Integer> xDegrees = new ArrayList<>();
        List<Integer> yDegrees = new ArrayList<>();

        int n = graph.getVertexCount();

        for (int u = 0; u < n; u++) {
            for (int v : graph.getSuccessors(u)) {
                int degU = graph.getVertexInDegree(u) + graph.getVertexOutDegree(u);
                int degV = graph.getVertexInDegree(v) + graph.getVertexOutDegree(v);

                xDegrees.add(degU);
                yDegrees.add(degV);
            }
        }

        return calculatePearsonCorrelation(xDegrees, yDegrees);
    }

    private static double calculatePearsonCorrelation(List<Integer> x, List<Integer> y) {
        int count = x.size();
        if (count == 0)
            return 0.0;

        double sumX = 0.0, sumY = 0.0, sumXY = 0.0;
        double sumX2 = 0.0, sumY2 = 0.0;

        for (int i = 0; i < count; i++) {
            double xi = x.get(i);
            double yi = y.get(i);

            sumX += xi;
            sumY += yi;
            sumXY += xi * yi;
            sumX2 += xi * xi;
            sumY2 += yi * yi;
        }

        double numerator = (count * sumXY) - (sumX * sumY);
        double denominator = Math.sqrt((count * sumX2 - sumX * sumX) * (count * sumY2 - sumY * sumY));

        if (denominator == 0)
            return 0.0;
        return numerator / denominator;
    }
}