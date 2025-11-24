 
 import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * JsonReducer: Reduz arquivos JSON grandes mantendo representatividade dos dados
 * para evitar problemas com GitHub file size limits.
 */
public class JsonReducer {
    private static final long MAX_SIZE_MB = 100;
    private static final long MAX_SIZE_BYTES = MAX_SIZE_MB * 1024 * 1024;
    
    public static void main(String[] args) throws Exception {
        Path dataDir = Paths.get("data");
        
        // Arquivos a serem processados
        String[] files = {
            "1-comentarios-issues.json",
            "2-fechamentos-issues.json", 
            "3-comentarios-prs.json",
            "4-interacoes-prs.json"
        };
        
        for (String fileName : files) {
            Path filePath = dataDir.resolve(fileName);
            if (!Files.exists(filePath)) {
                System.out.println("Arquivo não encontrado: " + fileName);
                continue;
            }
            
            long currentSize = Files.size(filePath);
            System.out.printf("\n=== Processando %s ===\n", fileName);
            System.out.printf("Tamanho atual: %.2f MB\n", currentSize / 1024.0 / 1024.0);
            
            if (currentSize <= MAX_SIZE_BYTES) {
                System.out.println("Arquivo já está dentro do limite, pulando...");
                continue;
            }
            
            // Criar backup
            Path backupPath = dataDir.resolve(fileName + ".backup");
            if (!Files.exists(backupPath)) {
                Files.copy(filePath, backupPath);
                System.out.println("Backup criado: " + backupPath.getFileName());
            }
            
            // Reduzir arquivo
            reduceJsonArray(filePath, MAX_SIZE_BYTES);
        }
        
        System.out.println("\n✓ Processamento concluído!");
    }
    
    /**
     * Reduz um arquivo JSON array mantendo uma amostra representativa
     */
    private static void reduceJsonArray(Path inputFile, long maxSizeBytes) throws IOException {
        System.out.println("Analisando estrutura do arquivo...");
        
        // Ler arquivo e extrair objetos JSON
        List<String> jsonObjects = new ArrayList<>();
        StringBuilder currentObject = new StringBuilder();
        int braceCount = 0;
        boolean inString = false;
        char prevChar = 0;
        
        try (BufferedReader reader = Files.newBufferedReader(inputFile, StandardCharsets.UTF_8)) {
            int ch;
            boolean foundStart = false;
            
            while ((ch = reader.read()) != -1) {
                char c = (char) ch;
                
                // Detectar início do array
                if (!foundStart && c == '[') {
                    foundStart = true;
                    continue;
                }
                if (!foundStart) continue;
                
                // Detectar strings (ignorar chaves dentro de strings)
                if (c == '"' && prevChar != '\\') {
                    inString = !inString;
                }
                
                if (!inString) {
                    if (c == '{') {
                        if (braceCount == 0) {
                            currentObject = new StringBuilder();
                        }
                        braceCount++;
                        currentObject.append(c);
                    } else if (c == '}') {
                        currentObject.append(c);
                        braceCount--;
                        if (braceCount == 0) {
                            // Objeto completo encontrado
                            jsonObjects.add(currentObject.toString().trim());
                            if (jsonObjects.size() % 10000 == 0) {
                                System.out.printf("Objetos extraídos: %d\n", jsonObjects.size());
                            }
                        }
                    } else if (braceCount > 0) {
                        currentObject.append(c);
                    }
                } else if (braceCount > 0) {
                    currentObject.append(c);
                }
                
                prevChar = c;
            }
        }
        
        System.out.printf("Total de objetos encontrados: %d\n", jsonObjects.size());
        
        if (jsonObjects.isEmpty()) {
            System.out.println("Nenhum objeto JSON encontrado!");
            return;
        }
        
        // Calcular quantos objetos manter para ficar sob o limite
        long avgObjectSize = calculateAverageSize(jsonObjects);
        long maxObjects = (maxSizeBytes - 100) / avgObjectSize; // margem de segurança
        
        System.out.printf("Tamanho médio por objeto: %d bytes\n", avgObjectSize);
        System.out.printf("Objetos a manter: %d (%.1f%% do total)\n", 
            maxObjects, (maxObjects * 100.0 / jsonObjects.size()));
        
        if (maxObjects >= jsonObjects.size()) {
            System.out.println("Todos os objetos já cabem no limite!");
            return;
        }
        
        // Selecionar amostra representativa (distribuída uniformemente)
        List<String> selectedObjects = selectRepresentativeSample(jsonObjects, (int) maxObjects);
        
        // Escrever arquivo reduzido
        Path reducedFile = inputFile;
        try (BufferedWriter writer = Files.newBufferedWriter(reducedFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            
            writer.write('[');
            for (int i = 0; i < selectedObjects.size(); i++) {
                if (i > 0) writer.write(',');
                writer.write(selectedObjects.get(i));
            }
            writer.write(']');
        }
        
        long newSize = Files.size(reducedFile);
        System.out.printf("Novo tamanho: %.2f MB (redução de %.1f%%)\n", 
            newSize / 1024.0 / 1024.0,
            (1.0 - (double)newSize / Files.size(inputFile.resolveSibling(inputFile.getFileName() + ".backup"))) * 100);
    }
    
    /**
     * Calcula tamanho médio dos objetos JSON
     */
    private static long calculateAverageSize(List<String> objects) {
        if (objects.isEmpty()) return 0;
        
        // Calcular em uma amostra para ser mais rápido
        int sampleSize = Math.min(1000, objects.size());
        long totalSize = 0;
        
        for (int i = 0; i < sampleSize; i++) {
            totalSize += objects.get(i).getBytes(StandardCharsets.UTF_8).length;
        }
        
        return totalSize / sampleSize + 5; // +5 para vírgulas e espaços
    }
    
    /**
     * Seleciona uma amostra representativa distribuída uniformemente
     */
    private static List<String> selectRepresentativeSample(List<String> objects, int targetSize) {
        if (targetSize >= objects.size()) {
            return new ArrayList<>(objects);
        }
        
        List<String> selected = new ArrayList<>();
        double step = (double) objects.size() / targetSize;
        
        for (int i = 0; i < targetSize; i++) {
            int index = (int) Math.round(i * step);
            if (index >= objects.size()) index = objects.size() - 1;
            selected.add(objects.get(index));
        }
        
        return selected;
    }
}