import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mineracao: Minera TODO o repositório spring-projects/spring-boot e organiza automaticamente
 * os dados em 4 categorias:
 * 1. Comentários em issues
 * 2. Fechamento de issues
 * 3. Comentários em pull requests
 * 4. Abertura, revisão, aprovação e merge de pull requests
 * 
 * Lê o token GITHUB_TOKEN do arquivo .env automaticamente.
 */
public class Mineracao {
	private static final String BASE_URL = "https://api.github.com";
	private static final String USER_AGENT = "MineracaoGrafos/1.0 (+https://github.com/)";

	private final HttpClient http;
	private final String token;

	public Mineracao(String token) {
		this.http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(20))
				.build();
		// Se token não foi passado, tenta ler do .env
		if (token == null || token.isBlank()) {
			token = loadTokenFromEnv();
		}
		this.token = (token == null || token.isBlank()) ? null : token.trim();
		if (this.token != null) {
			log("✓ Token configurado com sucesso (primeiros 10 chars: %s...)", 
				this.token.substring(0, Math.min(10, this.token.length())));
		} else {
			log("⚠ AVISO: Nenhum token configurado. Rate limit será muito limitado (60 req/hora).");
		}
	}

	/**
	 * Carrega o GITHUB_TOKEN do arquivo .env na raiz do projeto.
	 */
	private static String loadTokenFromEnv() {
		try {
			Path envFile = Path.of(".env");
			if (!Files.exists(envFile)) {
				return null;
			}
			for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
				line = line.trim();
				if (line.startsWith("GITHUB_TOKEN=")) {
					return line.substring("GITHUB_TOKEN=".length()).trim();
				}
			}
		} catch (IOException e) {
			log("Erro ao ler .env: %s", e.getMessage());
		}
		return null;
	}

	public record RepoId(String owner, String name) {
		public String toPath() { return owner + "/" + name; }
	}

	/**
	 * Minera TODO o repositório sem limites e organiza automaticamente em 4 categorias:
	 * 1. Comentários em issues
	 * 2. Fechamento de issues
	 * 3. Comentários em pull requests
	 * 4. Abertura, revisão, aprovação e merge de pull requests
	 */
	public void mineRepository(RepoId repo, Path outRoot) throws IOException, InterruptedException {
		Objects.requireNonNull(repo, "repo");
		Objects.requireNonNull(outRoot, "outRoot");

		Path outDir = outRoot;
		Files.createDirectories(outDir);

		log("=".repeat(70));
		log("Iniciando mineração COMPLETA de %s", repo.toPath());
		log("Diretório de saída: %s", outDir.toAbsolutePath());
		log("=".repeat(70));

		// Passo 1: Minerar comentários em issues
		Path issueCommentsFile = outDir.resolve("1-comentarios-issues.json");
		if (Files.exists(issueCommentsFile) && Files.size(issueCommentsFile) > 1000) {
			log("\n[1/4] Comentários em issues → ✓ JÁ EXISTE (%.1f MB), pulando...", 
				Files.size(issueCommentsFile) / 1024.0 / 1024.0);
		} else {
			log("\n[1/4] Minerando COMENTÁRIOS EM ISSUES...");
			fetchAndConsolidateAll(
				BASE_URL + "/repos/" + repo.toPath() + "/issues/comments?per_page=100",
				issueCommentsFile,
				"comentários em issues"
			);
		}

		// Passo 2: Minerar eventos de issues e filtrar fechamentos
		Path closedIssuesFile = outDir.resolve("2-fechamentos-issues.json");
		if (Files.exists(closedIssuesFile) && Files.size(closedIssuesFile) > 100) {
			log("\n[2/4] Fechamentos de issues → ✓ JÁ EXISTE (%.1f MB), pulando...", 
				Files.size(closedIssuesFile) / 1024.0 / 1024.0);
		} else {
			log("\n[2/4] Minerando FECHAMENTOS DE ISSUES...");
			Path issueEventsTemp = outDir.resolve("_temp_issue-events.json");
			
			// Só minerar se não tiver checkpoint completo
			if (!Files.exists(issueEventsTemp) || Files.size(issueEventsTemp) < 1000) {
				fetchAndConsolidateAll(
					BASE_URL + "/repos/" + repo.toPath() + "/issues/events?per_page=100",
					issueEventsTemp,
					"eventos de issues"
				);
			} else {
				log("  → Usando arquivo existente (já minerado)");
			}
			
			// Filtrar apenas eventos de fechamento
			if (!Files.exists(closedIssuesFile) || Files.size(closedIssuesFile) < 100) {
				filterClosedEvents(issueEventsTemp, closedIssuesFile);
			} else {
				log("  → Arquivo de fechamentos já existe, pulando filtro");
			}
			Files.deleteIfExists(issueEventsTemp); // Limpar arquivo temporário
		}

		// Passo 3: Minerar comentários em pull requests
		Path prCommentsFile = outDir.resolve("3-comentarios-prs.json");
		if (Files.exists(prCommentsFile) && Files.size(prCommentsFile) > 1000) {
			log("\n[3/4] Comentários em PRs → ✓ JÁ EXISTE (%.1f MB), pulando...", 
				Files.size(prCommentsFile) / 1024.0 / 1024.0);
		} else {
			log("\n[3/4] Minerando COMENTÁRIOS EM PULL REQUESTS...");
			fetchAndConsolidateAll(
				BASE_URL + "/repos/" + repo.toPath() + "/pulls/comments?per_page=100",
				prCommentsFile,
				"comentários em PRs"
			);
		}

		// Passo 4: Minerar PRs completas (abertura, merge, etc) e suas reviews
		log("\n[4/4] Minerando INTERAÇÕES EM PULL REQUESTS (abertura, revisão, aprovação, merge)...");
		Path prInteractionsFile = outDir.resolve("4-interacoes-prs.json");
		fetchPullRequestsWithReviews(repo, prInteractionsFile);

		log("\n" + "=".repeat(70));
		log("✓ MINERAÇÃO COMPLETA CONCLUÍDA!");
		log("=".repeat(70));
		log("\nArquivos gerados:");
		log("  1. %s (%.1f MB) - Comentários em issues",
			issueCommentsFile.getFileName(), Files.size(issueCommentsFile) / 1024.0 / 1024.0);
		log("  2. %s (%.1f MB) - Fechamentos de issues",
			closedIssuesFile.getFileName(), Files.size(closedIssuesFile) / 1024.0 / 1024.0);
		log("  3. %s (%.1f MB) - Comentários em PRs",
			prCommentsFile.getFileName(), Files.size(prCommentsFile) / 1024.0 / 1024.0);
		log("  4. %s (%.1f MB) - Interações em PRs",
			prInteractionsFile.getFileName(), Files.size(prInteractionsFile) / 1024.0 / 1024.0);
		log("\nDiretório: %s\n", outDir.toAbsolutePath());
	}

	/**
	 * Minera TODAS as páginas de um endpoint e consolida diretamente em um único arquivo.
	 * SALVA INCREMENTALMENTE a cada página e pode RETOMAR de onde parou.
	 */
	private void fetchAndConsolidateAll(String firstUrl, Path outputFile, String description) 
			throws IOException, InterruptedException {
		log("  Minerando todas as páginas de %s...", description);
		log("  Salvando em: %s", outputFile.getFileName());
		
		// Diretório para checkpoints
		Path checkpointDir = outputFile.getParent().resolve(".checkpoints");
		Files.createDirectories(checkpointDir);
		Path checkpointFile = checkpointDir.resolve(outputFile.getFileName() + ".checkpoint");
		
		// Verificar se existe checkpoint anterior
		int startPage = 1;
		String resumeUrl = firstUrl;
		boolean isResume = false;
		
		if (Files.exists(checkpointFile) && Files.exists(outputFile)) {
			try {
				String checkpoint = Files.readString(checkpointFile, StandardCharsets.UTF_8).trim();
				String[] parts = checkpoint.split("\\|");
				if (parts.length == 2) {
					startPage = Integer.parseInt(parts[0]);
					resumeUrl = parts[1];
					isResume = true;
					log("  → Retomando da página %d (checkpoint encontrado)", startPage);
				}
			} catch (IOException | NumberFormatException e) {
				log("  ⚠ Erro ao ler checkpoint, iniciando do zero");
				isResume = false;
			}
		}
		
		int page = startPage;
		String url = resumeUrl;
		int totalItems = 0;

		// Abrir arquivo para escrita (APPEND se for retomada, CREATE caso contrário)
		try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8,
				isResume ? StandardOpenOption.APPEND : StandardOpenOption.CREATE, 
				StandardOpenOption.TRUNCATE_EXISTING)) {
			
			if (!isResume) {
				writer.write('['); // Abrir array JSON apenas se for novo
			}
			
			while (url != null) {
				log("    Página %d...", page);
				
				try {
					HttpRequest req = baseRequest(URI.create(url)).GET().build();
					HttpResponse<String> resp = send(req);
					ensureSuccess(resp);
					
					String content = resp.body().trim();
					if (!content.isEmpty()) {
						// Extrair conteúdo do array
						int openBracket = content.indexOf('[');
						int closeBracket = content.lastIndexOf(']');
						if (openBracket >= 0 && closeBracket > openBracket) {
							String inside = content.substring(openBracket + 1, closeBracket).trim();
							if (!inside.isEmpty()) {
								// Adicionar vírgula se não for primeira página
								if (page > 1) {
									writer.write(',');
								}
								writer.write(inside);
								writer.flush(); // Forçar escrita no disco
								
								// Contar itens
								int itemsInPage = countJsonObjects(inside);
								totalItems += itemsInPage;
								log("      → Salvos %d itens (total: ~%d)", itemsInPage, totalItems);
							}
						}
					}
					
					rateLimitLog(resp.headers());
					String nextUrl = parseNextLink(resp.headers()).orElse(null);
					
					// Salvar checkpoint ANTES de avançar
					if (nextUrl != null) {
						Files.writeString(checkpointFile, (page + 1) + "|" + nextUrl, 
							StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
					}
					
					url = nextUrl;
					page++;
					
					// Pequena pausa para evitar rate limiting
					if (url != null) {
						safeWait(100);
					}
					
				} catch (IOException | InterruptedException e) {
					log("  ✗ Erro na página %d: %s", page, e.getMessage());
					log("  → Checkpoint salvo, você pode retomar com: java -cp bin App");
					if (e instanceof InterruptedException) {
						Thread.currentThread().interrupt();
					}
					throw new RuntimeException(e);
				}
			}
			
			writer.write(']'); // Fechar array JSON
			
			// Limpar checkpoint ao concluir com sucesso
			Files.deleteIfExists(checkpointFile);
		}

		log("  ✓ Concluído: %d páginas, ~%d itens salvos em %s", 
			page - 1, totalItems, outputFile.getFileName());
	}
	
	/**
	 * Conta objetos JSON em uma string de forma mais precisa.
	 */
	private int countJsonObjects(String json) {
		int count = 0;
		int depth = 0;
		boolean inString = false;
		char prev = 0;
		
		for (char c : json.toCharArray()) {
			if (c == '"' && prev != '\\') {
				inString = !inString;
			}
			if (!inString) {
				if (c == '{') {
					if (depth == 0) count++;
					depth++;
				} else if (c == '}') {
					depth--;
				}
			}
			prev = c;
		}
		return count;
	}

	/**
	 * Filtra apenas eventos de fechamento ("closed") do arquivo de eventos de issues.
	 */
	private void filterClosedEvents(Path inputFile, Path outputFile) throws IOException {
		log("  Filtrando eventos de fechamento...");
		
		String content = Files.readString(inputFile, StandardCharsets.UTF_8);
		List<String> closedEvents = new ArrayList<>();
		
		// Procura por objetos JSON que contêm "event":"closed"
		// Usa uma estratégia mais robusta: encontra objetos completos
		int depth = 0;
		int start = -1;
		boolean inString = false;
		char prevChar = 0;
		
		for (int i = 0; i < content.length(); i++) {
			char c = content.charAt(i);
			
			// Detectar strings (ignorar chaves dentro de strings)
			if (c == '"' && prevChar != '\\') {
				inString = !inString;
			}
			
			if (!inString) {
				if (c == '{') {
					if (depth == 0) {
						start = i;
					}
					depth++;
				} else if (c == '}') {
					depth--;
					if (depth == 0 && start >= 0) {
						// Objeto completo encontrado
						String obj = content.substring(start, i + 1);
						if (obj.contains("\"event\"") && obj.contains("\"closed\"")) {
							closedEvents.add(obj);
						}
						start = -1;
					}
				}
			}
			prevChar = c;
		}

		// Escrever arquivo com fechamentos
		try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			writer.write('[');
			for (int i = 0; i < closedEvents.size(); i++) {
				if (i > 0) writer.write(',');
				writer.write(closedEvents.get(i));
			}
			writer.write(']');
		}

		log("  ✓ Encontrados %d eventos de fechamento", closedEvents.size());
	}

	/**
	 * Minera todas as PRs com seus detalhes completos (merge, reviews, aprovações).
	 * BUSCA DETALHES INDIVIDUAIS de cada PR para obter dados de merge.
	 * SALVA INCREMENTALMENTE com checkpoint para retomar.
	 */
	private void fetchPullRequestsWithReviews(RepoId repo, Path outputFile) 
			throws IOException, InterruptedException {
		log("  Minerando lista de pull requests...");
		
		// Checkpoint para PRs processadas
		Path checkpointDir = outputFile.getParent().resolve(".checkpoints");
		Files.createDirectories(checkpointDir);
		Path prCheckpoint = checkpointDir.resolve("pr-details.checkpoint");
		
		// Primeiro, obter lista básica de todas as PRs (apenas para pegar os números)
		List<Integer> prNumbers = new ArrayList<>();
		String url = BASE_URL + "/repos/" + repo.toPath() + "/pulls?state=all&per_page=100";
		int page = 1;

		while (url != null) {
			log("    Página %d de PRs...", page);
			
			HttpRequest req = baseRequest(URI.create(url)).GET().build();
			HttpResponse<String> resp = send(req);
			ensureSuccess(resp);
			
			String content = resp.body().trim();
			if (!content.isEmpty()) {
				// Extrair números das PRs
				Pattern numPattern = Pattern.compile("\"number\"\\s*:\\s*(\\d+)");
				Matcher numMatcher = numPattern.matcher(content);
				while (numMatcher.find()) {
					int num = Integer.parseInt(numMatcher.group(1));
					if (!prNumbers.contains(num)) {
						prNumbers.add(num);
					}
				}
			}
			
			rateLimitLog(resp.headers());
			url = parseNextLink(resp.headers()).orElse(null);
			page++;
			if (url != null) {
				safeWait(100);
			}
		}

		log("  ✓ Encontradas %d pull requests", prNumbers.size());
		
		// Verificar checkpoint de PRs processadas
		int startFrom = 0;
		boolean isResume = false;
		if (Files.exists(prCheckpoint) && Files.exists(outputFile)) {
			try {
				String lastProcessed = Files.readString(prCheckpoint, StandardCharsets.UTF_8).trim();
				startFrom = Integer.parseInt(lastProcessed);
				isResume = true;
				log("  → Retomando da PR %d/%d (checkpoint encontrado)", startFrom + 1, prNumbers.size());
			} catch (IOException | NumberFormatException e) {
				log("  ⚠ Erro ao ler checkpoint, iniciando do zero");
			}
		}
		
		log("  Minerando DETALHES COMPLETOS + reviews de cada PR (inclui dados de merge)...");
		log("  ⚠ Isso faz 2 requests por PR e pode demorar bastante!");

		// Abrir arquivo para escrita incremental
		StandardOpenOption[] openOptions = isResume 
			? new StandardOpenOption[]{StandardOpenOption.APPEND}
			: new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
		
		try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8, openOptions)) {
			
			if (!isResume) {
				writer.write('[');
			}
			
			// Processar cada PR individualmente (detalhes + reviews)
			for (int i = startFrom; i < prNumbers.size(); i++) {
				Integer prNum = prNumbers.get(i);
				
				if ((i + 1) % 50 == 0 || (i + 1) == prNumbers.size()) {
					log("    Progresso: %d/%d PRs processadas...", i + 1, prNumbers.size());
				}
				
				try {
					// 1. BUSCAR DETALHES COMPLETOS DA PR (inclui merged, merged_by, merged_at)
					String prDetailsUrl = BASE_URL + "/repos/" + repo.toPath() + "/pulls/" + prNum;
					HttpRequest detailsReq = baseRequest(URI.create(prDetailsUrl)).GET().build();
					HttpResponse<String> detailsResp = send(detailsReq);
					
					if (detailsResp.statusCode() == 404) {
						continue; // PR não encontrada
					}
					ensureSuccess(detailsResp);
					
					String prDetails = detailsResp.body().trim();
					
					// 2. BUSCAR REVIEWS DESTA PR
					String reviewsUrl = BASE_URL + "/repos/" + repo.toPath() + "/pulls/" + prNum + "/reviews?per_page=100";
					List<String> reviews = new ArrayList<>();
					
					String revUrl = reviewsUrl;
					while (revUrl != null) {
						HttpRequest req = baseRequest(URI.create(revUrl)).GET().build();
						HttpResponse<String> resp = send(req);
						
						if (resp.statusCode() == 404) {
							break; // PR sem reviews
						}
						ensureSuccess(resp);
						
						String revContent = resp.body().trim();
						if (!revContent.isEmpty()) {
							int openBracket = revContent.indexOf('[');
							int closeBracket = revContent.lastIndexOf(']');
							if (openBracket >= 0 && closeBracket > openBracket) {
								String inside = revContent.substring(openBracket + 1, closeBracket).trim();
								if (!inside.isEmpty()) {
									reviews.add(inside);
								}
							}
						}
						
						revUrl = parseNextLink(resp.headers()).orElse(null);
						if (revUrl != null) {
							safeWait(50);
						}
					}
					
					// 3. CONSOLIDAR: PR completa + reviews
					if (i > 0) writer.write(',');
					
					String reviewsJson = reviews.isEmpty() ? "" : String.join(",", reviews);
					writer.write(String.format("{\"pr_details\":%s,\"reviews\":[%s]}", prDetails, reviewsJson));
					writer.flush();
					
					// Atualizar checkpoint a cada 50 PRs
					if ((i + 1) % 50 == 0) {
						Files.writeString(prCheckpoint, String.valueOf(i + 1), StandardCharsets.UTF_8);
					}
					
					safeWait(50); // Evitar rate limit
					
				} catch (IOException | InterruptedException e) {
					// Se for erro de rate limit, salvar checkpoint e re-lançar
					if (e.getMessage() != null && e.getMessage().contains("403")) {
						Files.writeString(prCheckpoint, String.valueOf(i), StandardCharsets.UTF_8);
						log("  ✗ Rate limit atingido na PR %d/%d", i + 1, prNumbers.size());
						log("  → Checkpoint salvo. Execute novamente após reset do rate limit.");
						throw e;
					}
					// Outros erros: ignorar esta PR específica e continuar
					log("  ⚠ Erro ao processar PR %d: %s", prNum, e.getMessage());
				}
			}
			
			writer.write(']');
			
			// Limpar checkpoint ao concluir
			Files.deleteIfExists(prCheckpoint);
		}

		log("  ✓ Consolidadas %d PRs com detalhes completos + reviews", prNumbers.size());
	}

	private HttpRequest.Builder baseRequest(URI uri) {
		HttpRequest.Builder b = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(60))
				.header("Accept", "application/vnd.github+json")
				.header("User-Agent", USER_AGENT);
		if (token != null) {
			b.header("Authorization", "token " + token);
		}
		return b;
	}

	private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
		try {
			return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw e;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
		}
	}

	private static void ensureSuccess(HttpResponse<?> resp) {
		int code = resp.statusCode();
		if (code >= 200 && code < 300) return;
		String msg = "Falha HTTP " + code + ": corpo=\n" + safeBody(resp);
		throw new RuntimeException(msg);
	}

	private static String safeBody(HttpResponse<?> resp) {
		Object b = resp.body();
		return b == null ? "<sem corpo>" : String.valueOf(b);
	}

	private static Optional<String> parseNextLink(HttpHeaders headers) {
		Optional<List<String>> values = headers.map().entrySet().stream()
				.filter(e -> e.getKey().equalsIgnoreCase("link"))
				.map(Map.Entry::getValue)
				.findFirst();
		if (values.isEmpty()) return Optional.empty();
		for (String full : values.get()) {
			String[] parts = full.split(",");
			for (String p : parts) {
				String s = p.trim();
				int start = s.indexOf('<');
				int end = s.indexOf('>');
				int rel = s.indexOf("rel=");
				if (start >= 0 && end > start && rel > end) {
					String url = s.substring(start + 1, end);
					String relVal = s.substring(rel).toLowerCase(Locale.ROOT);
					if (relVal.contains("\"next\"")) {
						return Optional.of(url);
					}
				}
			}
		}
		return Optional.empty();
	}

	private static void rateLimitLog(HttpHeaders headers) {
		String remaining = headerValue(headers, "x-ratelimit-remaining");
		String limit = headerValue(headers, "x-ratelimit-limit");
		String reset = headerValue(headers, "x-ratelimit-reset");
		if (remaining != null && limit != null) {
			log("    Rate limit: %s/%s restante(s)%s",
					remaining, limit,
					reset != null ? (" (reset: " + reset + ")") : "");
		}
	}

	private static String headerValue(HttpHeaders headers, String name) {
		Optional<List<String>> v = headers.map().entrySet().stream()
				.filter(e -> e.getKey().equalsIgnoreCase(name))
				.map(Map.Entry::getValue)
				.findFirst();
		return v.isPresent() && !v.get().isEmpty() ? v.get().get(0) : null;
	}

	/**
	 * Método seguro para pausas que lida apropriadamente com InterruptedException.
	 */
	private static void safeWait(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Thread foi interrompida durante espera", e);
		}
	}

	private static void log(String fmt, Object... args) {
		String msg = String.format(Locale.ROOT, fmt, args);
		System.out.println("[Mineracao] " + msg);
	}
}
