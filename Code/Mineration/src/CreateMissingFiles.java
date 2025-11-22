import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Utilitário para criar arquivos issues.json e pulls.json a partir dos dados existentes
 */
public class CreateMissingFiles {
    
    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get("data");
        Path repoDir = dataDir.resolve("spring-projects").resolve("spring-boot");
        
        System.out.println("Criando arquivos faltantes para: " + repoDir);
        
        createIssuesFile(repoDir);
        createPullsFile(repoDir);
        
        System.out.println("Arquivos criados com sucesso!");
    }
    
    private static void createIssuesFile(Path repoDir) throws IOException {
        System.out.println("Criando issues.json...");
        
        Set<Integer> issueNumbers = new HashSet<>();
        Map<Integer, String> issueAuthors = new HashMap<>();
        Map<Integer, Boolean> issueIsPR = new HashMap<>();
        
        // Extrair issues de issue-comments.json
        Path issueCommentsPath = repoDir.resolve("issue-comments.json");
        if (Files.exists(issueCommentsPath)) {
            String content = Files.readString(issueCommentsPath, StandardCharsets.UTF_8);
            extractIssuesFromComments(content, issueNumbers, issueAuthors);
        }
        
        // Extrair issues de issue-events.json  
        Path issueEventsPath = repoDir.resolve("issue-events.json");
        if (Files.exists(issueEventsPath)) {
            String content = Files.readString(issueEventsPath, StandardCharsets.UTF_8);
            extractIssuesFromEvents(content, issueNumbers, issueAuthors, issueIsPR);
        }
        
        // Criar o JSON sintético
        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        for (Integer number : new TreeSet<>(issueNumbers)) {
            if (!first) json.append(",\n");
            first = false;
            
            String author = issueAuthors.getOrDefault(number, "unknown");
            Boolean isPR = issueIsPR.get(number);
            
            json.append("  {\n");
            json.append("    \"number\": ").append(number).append(",\n");
            json.append("    \"user\": {\"login\": \"").append(author).append("\"}");
            if (Boolean.TRUE.equals(isPR)) {
                json.append(",\n    \"pull_request\": {}");
            }
            json.append("\n  }");
        }
        json.append("\n]");
        
        Files.writeString(repoDir.resolve("issues.json"), json.toString(), StandardCharsets.UTF_8);
        System.out.println("  Created issues.json with " + issueNumbers.size() + " issues");
    }
    
    private static void createPullsFile(Path repoDir) throws IOException {
        System.out.println("Criando pulls.json...");
        
        Set<Integer> prNumbers = new HashSet<>();
        Map<Integer, String> prAuthors = new HashMap<>();
        
        // Extrair PRs de pr-review-comments.json
        Path prCommentsPath = repoDir.resolve("pr-review-comments.json");
        if (Files.exists(prCommentsPath)) {
            String content = Files.readString(prCommentsPath, StandardCharsets.UTF_8);
            extractPRsFromComments(content, prNumbers, prAuthors);
        }
        
        // Criar o JSON sintético
        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        for (Integer number : new TreeSet<>(prNumbers)) {
            if (!first) json.append(",\n");
            first = false;
            
            String author = prAuthors.getOrDefault(number, "unknown");
            
            json.append("  {\n");
            json.append("    \"number\": ").append(number).append(",\n");
            json.append("    \"user\": {\"login\": \"").append(author).append("\"}\n");
            json.append("  }");
        }
        json.append("\n]");
        
        Files.writeString(repoDir.resolve("pulls.json"), json.toString(), StandardCharsets.UTF_8);
        System.out.println("  Created pulls.json with " + prNumbers.size() + " PRs");
    }
    
    private static void extractIssuesFromComments(String content, Set<Integer> numbers, Map<Integer, String> authors) {
        // Extrair URLs: "issue_url":"https://api.github.com/repos/spring-projects/spring-boot/issues/110"
        Pattern issueUrlPattern = Pattern.compile("\"issue_url\":\"[^\"]*issues/(\\d+)\"");
        
        Matcher urlMatcher = issueUrlPattern.matcher(content);
        int lastPos = 0;
        
        while (urlMatcher.find(lastPos)) {
            try {
                int issueNumber = Integer.parseInt(urlMatcher.group(1));
                numbers.add(issueNumber);
                lastPos = urlMatcher.end();
                
            } catch (NumberFormatException e) {
                lastPos = urlMatcher.end();
            }
        }
    }
    
    private static void extractIssuesFromEvents(String content, Set<Integer> numbers, Map<Integer, String> authors, Map<Integer, Boolean> isPR) {
        // Extrair de issue events: "url":"https://api.github.com/repos/spring-projects/spring-boot/issues/events/..."
        Pattern issuePattern = Pattern.compile("\"issue\":\\s*\\{[^}]*\"number\":\\s*(\\d+)");
        Pattern userPattern = Pattern.compile("\"user\":\\s*\\{[^}]*\"login\":\\s*\"([^\"]+)\"");
        Pattern prPattern = Pattern.compile("\"pull_request\":");
        
        String[] objects = content.split("\\}\\s*,\\s*\\{");
        
        for (String obj : objects) {
            Matcher issueMatcher = issuePattern.matcher(obj);
            if (issueMatcher.find()) {
                try {
                    int issueNumber = Integer.parseInt(issueMatcher.group(1));
                    numbers.add(issueNumber);
                    
                    Matcher userMatcher = userPattern.matcher(obj);
                    if (userMatcher.find()) {
                        authors.put(issueNumber, userMatcher.group(1));
                    }
                    
                    isPR.put(issueNumber, prPattern.matcher(obj).find());
                    
                } catch (NumberFormatException e) {
                    // Ignorar
                }
            }
        }
    }
    
    private static void extractPRsFromComments(String content, Set<Integer> numbers, Map<Integer, String> authors) {
        // Extrair de PR review comments: "pull_request_url":"https://api.github.com/repos/spring-projects/spring-boot/pulls/138"
        Pattern prUrlPattern = Pattern.compile("\"pull_request_url\":\"[^\"]*pulls/(\\d+)\"");
        
        Matcher matcher = prUrlPattern.matcher(content);
        while (matcher.find()) {
            try {
                int prNumber = Integer.parseInt(matcher.group(1));
                numbers.add(prNumber);
            } catch (NumberFormatException e) {
                // Ignorar
            }
        }
    }
}