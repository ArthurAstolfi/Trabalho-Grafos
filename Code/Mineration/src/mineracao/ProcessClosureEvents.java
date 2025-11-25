package mineracao;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.*;

public class ProcessClosureEvents {
	public static void main(String[] args) throws IOException {
		Path input = Paths.get("C:\\Users\\Arthur\\Faculdade\\Grafos\\Trabalho-Grafos\\spring-boot\\2-fechamentos-issues.json");
		Path output = Paths.get("C:\\Users\\Arthur\\Faculdade\\Grafos\\Trabalho-Grafos\\Code\\Mineration\\data\\spring-projects\\spring-boot\\issue-events.json");
		
		System.out.println("Processando eventos de fechamento...");
		System.out.println("Lendo arquivo (pode demorar, arquivo grande)...");
		
		int closedCount = 0;
		int totalObjects = 0;
		StringBuilder outputContent = new StringBuilder();
		
		try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
			String line;
			int lineNum = 0;
			while ((line = reader.readLine()) != null) {
				lineNum++;
				System.out.println("Processando linha " + lineNum + " (tamanho: " + line.length() + " chars)...");
				int[] counts = extractClosedEventsWithCount(line, outputContent);
				totalObjects += counts[0];
				closedCount += counts[1];
				System.out.println("  -> Objetos processados: " + counts[0] + ", eventos closed: " + counts[1]);
			}
		}
		
		// Salvar arquivo
		Files.createDirectories(output.getParent());
		Files.writeString(output, outputContent.toString(), StandardCharsets.UTF_8);
		
		System.out.println("\n=== RESUMO ===");
		System.out.println("Total de objetos JSON processados: " + totalObjects);
		System.out.println("Eventos de fechamento extraídos: " + closedCount);
		System.out.println("Arquivo salvo: " + output);
	}
	
	private static int[] extractClosedEventsWithCount(String jsonArray, StringBuilder output) {
		int totalCount = 0;
		int closedCount = 0;
		
		// Padrão para encontrar objetos com "event":"closed"
		// Vamos procurar por cada objeto individualmente usando contagem de chaves
		int depth = 0;
		int startIdx = -1;
		boolean inString = false;
		boolean escape = false;
		
		for (int i = 0; i < jsonArray.length(); i++) {
			char c = jsonArray.charAt(i);
			
			if (escape) {
				escape = false;
				continue;
			}
			
			if (c == '\\') {
				escape = true;
				continue;
			}
			
			if (c == '"') {
				inString = !inString;
				continue;
			}
			
			if (inString) continue;
			
			if (c == '{') {
				if (depth == 0) {
					startIdx = i;
				}
				depth++;
			} else if (c == '}') {
				depth--;
				if (depth == 0 && startIdx != -1) {
					// Temos um objeto completo
					totalCount++;
					String obj = jsonArray.substring(startIdx, i + 1);
					if (isClosedEvent(obj)) {
						// Extrair dados necessários usando métodos mais robustos
						String actor = extractNestedValue(obj, "actor", "login");
						String issueNumber = extractDirectValue(obj, "number");
						String issueUser = extractNestedValue(obj, "issue", "user", "login");
						
						if (actor != null && issueNumber != null) {
							// Formato simples esperado pelo GraphBuilder
							output.append("{\"event\":\"closed\",\"actor\":{\"login\":\"")
							      .append(escapeJson(actor))
							      .append("\"},\"issue\":{\"number\":")
							      .append(issueNumber);
							
							if (issueUser != null) {
								output.append(",\"user\":{\"login\":\"")
								      .append(escapeJson(issueUser))
								      .append("\"}");
							}
							
							output.append("}}\n");
							closedCount++;
						}
					}
					startIdx = -1;
					
					// Debug a cada 100 objetos
					if (totalCount % 100 == 0) {
						System.out.print(".");
						if (totalCount % 1000 == 0) {
							System.out.println(" " + totalCount);
						}
					}
				}
			}
		}
		
		return new int[]{totalCount, closedCount};
	}
	
	private static boolean isClosedEvent(String jsonObj) {
		return jsonObj.contains("\"event\":\"closed\"") || jsonObj.contains("\"event\": \"closed\"");
	}
	
	private static String extractDirectValue(String json, String key) {
		// Procura por "key": valor (número ou string simples)
		Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
		Matcher m = p.matcher(json);
		return m.find() ? m.group(1) : null;
	}
	
	private static String extractNestedValue(String json, String... keys) {
		// Navega por objetos aninhados para encontrar valor
		// Ex: extractNestedValue(json, "actor", "login") -> procura actor.login
		if (keys.length == 0) return null;
		
		// Primeiro, encontra o objeto pai
		String current = json;
		for (int i = 0; i < keys.length - 1; i++) {
			String key = keys[i];
			Pattern objPattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\\{");
			Matcher m = objPattern.matcher(current);
			if (!m.find()) return null;
			
			// Extrai o objeto aninhado completo
			int start = m.end() - 1; // posição do {
			current = extractObject(current, start);
			if (current == null) return null;
		}
		
		// Agora extrai o valor final
		String finalKey = keys[keys.length - 1];
		Pattern valuePattern = Pattern.compile("\"" + finalKey + "\"\\s*:\\s*\"([^\"]+)\"");
		Matcher m = valuePattern.matcher(current);
		return m.find() ? m.group(1) : null;
	}
	
	private static String extractObject(String json, int startPos) {
		// Extrai um objeto JSON completo começando em startPos (que deve ser um '{')
		int depth = 0;
		boolean inString = false;
		boolean escape = false;
		
		for (int i = startPos; i < json.length(); i++) {
			char c = json.charAt(i);
			
			if (escape) {
				escape = false;
				continue;
			}
			if (c == '\\') {
				escape = true;
				continue;
			}
			if (c == '"') {
				inString = !inString;
				continue;
			}
			if (inString) continue;
			
			if (c == '{') depth++;
			else if (c == '}') {
				depth--;
				if (depth == 0) {
					return json.substring(startPos, i + 1);
				}
			}
		}
		return null;
	}
	
	private static String escapeJson(String s) {
		if (s == null) return null;
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
	
	private static String extractValue(String json, String regexPattern) {
		Pattern pattern = Pattern.compile(regexPattern);
		Matcher matcher = pattern.matcher(json);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}
}
