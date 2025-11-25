package app;

import estrutura.AbstractGraph;
import io.GraphLoader;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            String path = "Code/Mineration/data/spring-projects/spring-boot/graphs/graph_integrated.csv";
            String saidaGephi = "resultado_final.gexf";
            
            System.out.println("Carregando grafo de: " + path);
            
            GraphLoader.GraphData data = GraphLoader.loadGraph(path, false);
            AbstractGraph grafo = data.graph;

            System.out.println("Grafo carregado com sucesso!");
            System.out.println("- Vértices: " + grafo.getVertexCount());
            System.out.println("- Arestas: " + grafo.getEdgeCount());
            System.out.println("- Conexo? " + (grafo.isConnected() ? "Sim" : "Não"));

            AnaliseService analisador = new AnaliseService();
            analisador.executarAnaliseCompleta(grafo, data);

            System.out.println("\n[4] EXPORTAÇÃO");
            grafo.exportToGEPHI(saidaGephi);
            System.out.println("Arquivo para Gephi gerado: " + saidaGephi);

        } catch (IOException e) {
            System.err.println("Erro de E/S (Arquivo não encontrado ou erro de leitura): " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Erro durante a execução:");
            e.printStackTrace();
        }
    }
}