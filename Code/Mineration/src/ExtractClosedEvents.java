import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.*;

public class ExtractClosedEvents {
    
    public static void main(String[] args) throws Exception {
        String inputFile = "C:\\Users\\Arthur\\Faculdade\\Grafos\\Trabalho-Grafos\\spring-boot\\2-fechamentos-issues.json";
        String outputFile = "C:\\Users\\Arthur\\Faculdade\\Grafos\\Trabalho-Grafos\\Code\\Mineration\\data\\spring-projects\\spring-boot\\issue-events.json";
        
        System.out.println("Extraindo eventos de fechamento...");
        
        // Lê todo o conteúdo
        String content = new String(Files.readAllBytes(Paths.get(inputFile)), StandardCharsets.UTF_8);
        
        // Extrai objetos JSON que contêm "event":"closed"
        Pattern eventPattern = Pattern.compile("\\{[^}]*\"event\"\\s*:\\s*\"closed\"[^}]*\\}");
        Matcher matcher = eventPattern.matcher(content);
        
        int count = 0;
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(
                new FileOutputStream(outputFile), 
                StandardCharsets.UTF_8))) {
            
            while (matcher.find()) {
                String event = matcher.group();
                writer.write(event);
                writer.newLine();
                count++;
            }
        }
        
        System.out.println("Eventos de fechamento extraídos: " + count);
        System.out.println("Arquivo salvo: " + outputFile);
    }
}
