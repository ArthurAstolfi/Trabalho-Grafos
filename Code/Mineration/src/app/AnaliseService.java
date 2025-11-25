package app;

import estrutura.AbstractGraph;
import analise.GraphCentralityMetrics;
import analise.GraphStructureMetrics;
import analise.GraphCommunityMetrics;
import io.GraphLoader;

import java.util.List;
import java.util.Map;

public class AnaliseService {

    public void executarAnaliseCompleta(AbstractGraph grafo, GraphLoader.GraphData data) {
        System.out.println("\n========================================");
        System.out.println("   INICIANDO ANÁLISE DE REDES COMPLEXAS");
        System.out.println("========================================");

        executarMetricasCentralidade(grafo, data);
        executarMetricasEstrutura(grafo);
        executarAnaliseComunidades(grafo, data);
        
        System.out.println("\n========================================");
        System.out.println("          ANÁLISE CONCLUÍDA");
        System.out.println("========================================");
    }

    private void executarMetricasCentralidade(AbstractGraph grafo, GraphLoader.GraphData data) {
        System.out.println("\n[1] MÉTRICAS DE CENTRALIDADE");
        System.out.println("----------------------------");

        // PageRank
        Map<Integer, Double> pr = GraphCentralityMetrics.calculatePageRank(grafo);
        System.out.println("• Top 5 Influenciadores (PageRank):");
        printTop5(pr, data);

        // Closeness
        Map<Integer, Double> closeness = GraphCentralityMetrics.calculateClosenessCentrality(grafo);
        System.out.println("\n• Top 5 Agilidade (Closeness):");
        printTop5(closeness, data);
        
        // Betweenness
        System.out.println("\n• Calculando Betweenness (pode demorar)...");
        Map<Integer, Double> betweenness = GraphCentralityMetrics.calculateBetweennessCentrality(grafo);
        System.out.println("• Top 5 Pontes (Betweenness):");
        printTop5(betweenness, data);
    }

    private void executarMetricasEstrutura(AbstractGraph grafo) {
        System.out.println("\n[2] ESTRUTURA E COESÃO");
        System.out.println("----------------------");
        
        double densidade = GraphStructureMetrics.calculateDensity(grafo);
        System.out.printf("• Densidade da Rede: %.6f\n", densidade);

        double clustering = GraphStructureMetrics.calculateAverageClusteringCoefficient(grafo);
        System.out.printf("• Coeficiente de Aglomeração Médio: %.6f\n", clustering);

        double assortatividade = GraphStructureMetrics.calculateAssortativity(grafo);
        System.out.printf("• Assortatividade: %.6f\n", assortatividade);
        
        if (assortatividade > 0) System.out.println("  -> Padrão Assortativo (Hubs conectam com Hubs)");
        else System.out.println("  -> Padrão Disassortativo (Hubs conectam com Periferia)");
    }

    private void executarAnaliseComunidades(AbstractGraph grafo, GraphLoader.GraphData data) {
        System.out.println("\n[3] DETECÇÃO DE COMUNIDADES");
        System.out.println("---------------------------");
        
        int cortes = 10;
        System.out.println("• Executando Girvan-Newman (Max Splits: " + cortes + ")...");
        
        List<List<Integer>> comunidades = GraphCommunityMetrics.detectCommunitiesGirvanNewman(grafo, cortes);
        System.out.println("• Comunidades Detectadas: " + comunidades.size());
        
        for (int i = 0; i < Math.min(3, comunidades.size()); i++) {
            System.out.println("  -> Grupo " + (i+1) + ": " + comunidades.get(i).size() + " membros");
        }

        System.out.println("\n• Analisando 'Bridging Ties' (Laços de Ponte)...");
        List<String> bridges = GraphCommunityMetrics.findBridgingTies(grafo, comunidades);
        System.out.println("• Total de Pontes encontradas: " + bridges.size());
        
        if (!bridges.isEmpty()) {
            System.out.println("• Exemplos de conexões entre grupos:");
            bridges.stream().limit(5).forEach(b -> {
                try {
                    String[] parts = b.split(" ");
                    int u = Integer.parseInt(parts[0]);
                    int v = Integer.parseInt(parts[2]);
                    System.out.println("  -> " + data.indexToUser.get(u) + " conecta com " + data.indexToUser.get(v));
                } catch (Exception e) {
                    System.out.println("  -> " + b);
                }
            });
        }
    }

    // Helper para exibir top 5 formatado
    private void printTop5(Map<Integer, Double> metrics, GraphLoader.GraphData data) {
        metrics.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
            .limit(5)
            .forEach(e -> {
                String name = data.indexToUser.get(e.getKey());
                System.out.printf("   %s: %.5f\n", name, e.getValue());
            });
    }
}