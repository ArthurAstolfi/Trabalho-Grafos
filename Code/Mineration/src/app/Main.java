package app;

import estrutura.AbstractGraph;
import io.GraphLoader;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            // Ajuste o caminho para onde o seu CSV realmente está
            String path = "Code/Mineration/data/spring-projects/spring-boot/graphs/graph_integrated.csv";
            
            System.out.println("Carregando grafo...");
            
            GraphLoader.GraphData data = GraphLoader.loadGraph(path, false); // false = Lista de Adjacência
            AbstractGraph g = data.graph;

            System.out.println("=== Sucesso! ===");
            System.out.println("Vértices: " + g.getVertexCount());
            System.out.println("Arestas: " + g.getEdgeCount());
            System.out.println("Conexo? " + g.isConnected());

            String userExemplo = "wilkinsona";
            if (data.userToIndex.containsKey(userExemplo)) {
                int id = data.userToIndex.get(userExemplo);
                System.out.println("Grau de saída de " + userExemplo + ": " + g.getVertexOutDegree(id));
            }

            g.exportToGEPHI("saida_grafo.gexf");
            System.out.println("Exportado para saida_grafo.gexf");

        } catch (IOException e) {
            System.err.println("Erro ao ler arquivo: " + e.getMessage());
        }
    }
}