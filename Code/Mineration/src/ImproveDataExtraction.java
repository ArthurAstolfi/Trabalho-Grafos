import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Versão melhorada para extrair mais autores dos dados existentes
 */
public class ImproveDataExtraction {
    
    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get("data");
        Path repoDir = dataDir.resolve("spring-projects").resolve("spring-boot");
        
        System.out.println("Melhorando extração de dados de: " + repoDir);
        
        createBetterIssuesFile(repoDir);
        createBetterPullsFile(repoDir);
        
        System.out.println("Extração melhorada concluída!");
    }
    
    private static void createBetterIssuesFile(Path repoDir) throws IOException {
        System.out.println("Criando issues.json melhorado...");
        
        Map<Integer, String> issueAuthors = new HashMap<>();
        Map<Integer, Boolean> issueIsPR = new HashMap<>();
        Set<Integer> allIssues = new HashSet<>();
        
        // 1. Extrair de issue-events.json (melhor fonte para autores)
        extractFromIssueEvents(repoDir, issueAuthors, issueIsPR, allIssues);
        
        // 2. Extrair números adicionais de issue-comments.json
        extractIssueNumbersFromComments(repoDir, allIssues);
        
        // 3. Tentar extrair alguns autores de pull-details.json se existir
        extractAuthorsFromPullDetails(repoDir, issueAuthors, issueIsPR, allIssues);
        
        // Criar JSON melhorado
        createIssuesJson(repoDir, allIssues, issueAuthors, issueIsPR);
    }
    
    private static void createBetterPullsFile(Path repoDir) throws IOException {
        System.out.println("Criando pulls.json melhorado...");
        
        Map<Integer, String> prAuthors = new HashMap<>();
        Set<Integer> allPRs = new HashSet<>();
        
        // 1. Extrair de pull-details.json se existir (melhor fonte)
        extractPRsFromPullDetails(repoDir, prAuthors, allPRs);
        
        // 2. Extrair números adicionais de pr-review-comments.json
        extractPRNumbersFromComments(repoDir, allPRs);
        
        // Criar JSON
        createPullsJson(repoDir, allPRs, prAuthors);
    }
    
    private static void extractFromIssueEvents(Path repoDir, Map<Integer, String> authors, 
                                             Map<Integer, Boolean> isPR, Set<Integer> numbers) throws IOException {
        Path eventsFile = repoDir.resolve("issue-events.json");
        if (!Files.exists(eventsFile)) return;
        
        System.out.println("  Extraindo de issue-events.json...");
        String content = Files.readString(eventsFile, StandardCharsets.UTF_8);
        
        // Buscar por eventos de abertura que têm o autor original
        Pattern openEventPattern = Pattern.compile(
            "\\{[^}]*\"event\"\\s*:\\s*\"opened\"[^}]*\"issue\"\\s*:\\s*\\{[^}]*\"number\"\\s*:\\s*(\\d+)[^}]*\\}[^}]*\"actor\"\\s*:\\s*\\{[^}]*\"login\"\\s*:\\s*\"([^\"]+)\""
        );
        
        Matcher matcher = openEventPattern.matcher(content);
        while (matcher.find()) {
            try {
                int issueNum = Integer.parseInt(matcher.group(1));
                String author = matcher.group(2);
                authors.put(issueNum, author);
                numbers.add(issueNum);
            } catch (NumberFormatException e) {
                // Ignorar
            }
        }
        
        // Buscar todos os números de issues/PRs
        Pattern issueRefPattern = Pattern.compile("\"issue\"\\s*:\\s*\\{[^}]*\"number\"\\s*:\\s*(\\d+)");
        matcher = issueRefPattern.matcher(content);
        while (matcher.find()) {
            try {
                int issueNum = Integer.parseInt(matcher.group(1));
                numbers.add(issueNum);
                
                // Verificar se é PR procurando por "pull_request" próximo
                int start = Math.max(0, matcher.start() - 200);
                int end = Math.min(content.length(), matcher.end() + 200);
                String context = content.substring(start, end);
                if (context.contains("\"pull_request\"")) {
                    isPR.put(issueNum, true);
                }
            } catch (NumberFormatException e) {
                // Ignorar
            }
        }
        
        System.out.println("    Encontrados " + numbers.size() + " issues, " + authors.size() + " com autores conhecidos");
    }
    
    private static void extractIssueNumbersFromComments(Path repoDir, Set<Integer> numbers) throws IOException {
        Path commentsFile = repoDir.resolve("issue-comments.json");
        if (!Files.exists(commentsFile)) return;
        
        System.out.println("  Extraindo números de issue-comments.json...");
        String content = Files.readString(commentsFile, StandardCharsets.UTF_8);
        
        Pattern pattern = Pattern.compile("\"issue_url\"\\s*:\\s*\"[^\"]*issues/(\\d+)\"");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            try {
                numbers.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException e) {
                // Ignorar
            }
        }
        
        System.out.println("    Total de issues únicos: " + numbers.size());
    }
    
    private static void extractAuthorsFromPullDetails(Path repoDir, Map<Integer, String> authors, 
                                                    Map<Integer, Boolean> isPR, Set<Integer> numbers) throws IOException {
        Path pullDetailsFile = repoDir.resolve("pull-details.json");
        if (!Files.exists(pullDetailsFile)) return;
        
        System.out.println("  Extraindo de pull-details.json...");
        String content = Files.readString(pullDetailsFile, StandardCharsets.UTF_8);
        
        // Buscar PRs com autores
        Pattern prPattern = Pattern.compile(
            "\\{[^}]*\"number\"\\s*:\\s*(\\d+)[^}]*\"user\"\\s*:\\s*\\{[^}]*\"login\"\\s*:\\s*\"([^\"]+)\""
        );
        
        Matcher matcher = prPattern.matcher(content);
        while (matcher.find()) {
            try {
                int prNum = Integer.parseInt(matcher.group(1));
                String author = matcher.group(2);
                authors.put(prNum, author);
                isPR.put(prNum, true);
                numbers.add(prNum);
            } catch (NumberFormatException e) {
                // Ignorar
            }
        }
        
        System.out.println("    Encontrados " + authors.size() + " PRs com autores em pull-details.json");
    }
    
    private static void extractPRsFromPullDetails(Path repoDir, Map<Integer, String> authors, Set<Integer> numbers) throws IOException {
        Path pullDetailsFile = repoDir.resolve("pull-details.json");
        if (!Files.exists(pullDetailsFile)) return;
        
        System.out.println("  Extraindo PRs de pull-details.json...");
        String content = Files.readString(pullDetailsFile, StandardCharsets.UTF_8);
        
        Pattern prPattern = Pattern.compile(
            "\\{[^}]*\"number\"\\s*:\\s*(\\d+)[^}]*\"user\"\\s*:\\s*\\{[^}]*\"login\"\\s*:\\s*\"([^\"]+)\""
        );
        
        Matcher matcher = prPattern.matcher(content);
        while (matcher.find()) {
            try {
                int prNum = Integer.parseInt(matcher.group(1));
                String author = matcher.group(2);
                authors.put(prNum, author);
                numbers.add(prNum);
            } catch (NumberFormatException e) {
                // Ignorar
            }
        }
        
        System.out.println("    Encontrados " + numbers.size() + " PRs, " + authors.size() + " com autores");
    }
    
    private static void extractPRNumbersFromComments(Path repoDir, Set<Integer> numbers) throws IOException {
        Path commentsFile = repoDir.resolve("pr-review-comments.json");
        if (!Files.exists(commentsFile)) return;
        
        System.out.println("  Extraindo números de pr-review-comments.json...");
        String content = Files.readString(commentsFile, StandardCharsets.UTF_8);
        
        Pattern pattern = Pattern.compile("\"pull_request_url\"\\s*:\\s*\"[^\"]*pulls/(\\d+)\"");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            try {
                numbers.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException e) {
                // Ignorar
            }
        }
        
        System.out.println("    Total de PRs únicos: " + numbers.size());
    }
    
    private static void createIssuesJson(Path repoDir, Set<Integer> numbers, 
                                       Map<Integer, String> authors, Map<Integer, Boolean> isPR) throws IOException {
        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        
        for (Integer number : new TreeSet<>(numbers)) {
            if (!first) json.append(",\n");
            first = false;
            
            String author = authors.getOrDefault(number, "unknown");
            Boolean isAPR = isPR.get(number);
            
            json.append("  {\n");
            json.append("    \"number\": ").append(number).append(",\n");
            json.append("    \"user\": {\"login\": \"").append(author).append("\"}");
            if (Boolean.TRUE.equals(isAPR)) {
                json.append(",\n    \"pull_request\": {}");
            }
            json.append("\n  }");
        }
        json.append("\n]");
        
        Files.writeString(repoDir.resolve("issues.json"), json.toString(), StandardCharsets.UTF_8);
        
        long knownAuthors = authors.entrySet().stream().filter(e -> !"unknown".equals(e.getValue())).count();
        System.out.println("  Created issues.json: " + numbers.size() + " issues (" + knownAuthors + " com autores conhecidos)");
    }
    
    private static void createPullsJson(Path repoDir, Set<Integer> numbers, Map<Integer, String> authors) throws IOException {
        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        
        for (Integer number : new TreeSet<>(numbers)) {
            if (!first) json.append(",\n");
            first = false;
            
            String author = authors.getOrDefault(number, "unknown");
            
            json.append("  {\n");
            json.append("    \"number\": ").append(number).append(",\n");
            json.append("    \"user\": {\"login\": \"").append(author).append("\"}\n");
            json.append("  }");
        }
        json.append("\n]");
        
        Files.writeString(repoDir.resolve("pulls.json"), json.toString(), StandardCharsets.UTF_8);
        
        long knownAuthors = authors.entrySet().stream().filter(e -> !"unknown".equals(e.getValue())).count();
        System.out.println("  Created pulls.json: " + numbers.size() + " PRs (" + knownAuthors + " com autores conhecidos)");
    }
}