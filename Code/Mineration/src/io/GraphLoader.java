package io;

import estrutura.AbstractGraph;
import estrutura.AdjacencyListGraph;
import estrutura.AdjacencyMatrixGraph;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class GraphLoader {
    
    public static class GraphData {
        public AbstractGraph graph;
        public Map<String, Integer> userToIndex;
        public Map<Integer, String> indexToUser;

        public GraphData(AbstractGraph graph, Map<String, Integer> userToIndex, Map<Integer, String> indexToUser) {
            this.graph = graph;
            this.userToIndex = userToIndex;
            this.indexToUser = indexToUser;
        }
    }

    private static class EdgeTemp {
        String src; String target; double weight;
        EdgeTemp(String s, String t, double w) { src = s; target = t; weight = w; }
    }

    public static GraphData loadGraph(String csvPath, boolean useMatrix) throws IOException {
        Path path = Path.of(csvPath);
        if (!Files.exists(path)) throw new IOException("Arquivo não encontrado: " + csvPath);

        Set<String> users = new HashSet<>();
        List<EdgeTemp> tempEdges = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line = br.readLine(); 
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 3) continue;

                String u = parts[0].trim();
                String v = parts[1].trim();
                double w = Double.parseDouble(parts[2].trim());

                users.add(u);
                users.add(v);
                tempEdges.add(new EdgeTemp(u, v, w));
            }
        }

        Map<String, Integer> userToIndex = new HashMap<>();
        Map<Integer, String> indexToUser = new HashMap<>();
        int idx = 0;
        List<String> sortedUsers = new ArrayList<>(users);
        Collections.sort(sortedUsers); 
        
        for (String user : sortedUsers) {
            userToIndex.put(user, idx);
            indexToUser.put(idx, user);
            idx++;
        }

        AbstractGraph graph;
        if (useMatrix) {
            graph = new AdjacencyMatrixGraph(users.size());
        } else {
            graph = new AdjacencyListGraph(users.size());
        }

        for (EdgeTemp e : tempEdges) {
            int uIndex = userToIndex.get(e.src);
            int vIndex = userToIndex.get(e.target);
            graph.addEdge(uIndex, vIndex); 
            graph.setEdgeWeight(uIndex, vIndex, e.weight);
        }

        return new GraphData(graph, userToIndex, indexToUser);
    }
}