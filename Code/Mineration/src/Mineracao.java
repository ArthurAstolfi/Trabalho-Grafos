import java.io.IOException;
import java.io.UncheckedIOException;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mineracao: utilitário simples para minerar dados públicos do GitHub.
 *
 * Etapa 1 (assumida): coletar e persistir em disco os dados brutos (JSON) de um repositório
 * para uso posterior. Este código usa somente a API REST pública do GitHub e salva as páginas
 * paginadas em arquivos na pasta "data/<owner>/<repo>/".
 *
 * Observações:
 * - Token (opcional): defina via variável de ambiente GITHUB_TOKEN para aumentar o limite de rate.
 * - Não dependemos de bibliotecas externas; apenas Java 11+ (HttpClient).
 * - Não executa nenhuma transformação de grafo nesta etapa; apenas coleta e guarda JSON.
 */
public class Mineracao {
	private static final String BASE_URL = "https://api.github.com";
	private static final String USER_AGENT = "MineracaoGrafos/1.0 (+https://github.com/)";

	private final HttpClient http;
	private final String token; // pode ser null

	public Mineracao(String token) {
		this.http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(20))
				.build();
		this.token = (token == null || token.isBlank()) ? null : token.trim();
	}

	public record RepoId(String owner, String name) {
		public String toPath() { return owner + "/" + name; }
	}

	/**
	 * Minera um conjunto padrão de endpoints de um repositório e salva em disco.
	 *
	 * Endpoints coletados:
	 * - repo (metadados)
	 * - commits (paginado)
	 * - contributors (paginado)
	 * - issues?state=all (paginado)
	 * - pulls?state=all (paginado)
	 * - branches (paginado)
	 * - tags (paginado)
	 */
	public void mineRepository(RepoId repo, Path outRoot) throws IOException, InterruptedException {
		Objects.requireNonNull(repo, "repo");
		Objects.requireNonNull(outRoot, "outRoot");

		Path outDir = outRoot.resolve(repo.owner()).resolve(repo.name());
		Files.createDirectories(outDir);

		log("Iniciando mineração de %s...", repo.toPath());

		// Repo metadata (não paginado)
		fetchSingle(BASE_URL + "/repos/" + repo.toPath(), outDir.resolve("repo.json"));

	// Paginated collections (geral)
	fetchPaginated(BASE_URL + "/repos/" + repo.toPath() + "/commits?per_page=100", outDir, "commits");
	fetchPaginated(BASE_URL + "/repos/" + repo.toPath() + "/contributors?per_page=100&anon=1", outDir, "contributors");
	fetchPaginated(BASE_URL + "/repos/" + repo.toPath() + "/issues?state=all&per_page=100", outDir, "issues");
	fetchPaginated(BASE_URL + "/repos/" + repo.toPath() + "/pulls?state=all&per_page=100", outDir, "pulls");
	fetchPaginated(BASE_URL + "/repos/" + repo.toPath() + "/branches?per_page=100", outDir, "branches");
	fetchPaginated(BASE_URL + "/repos/" + repo.toPath() + "/tags?per_page=100", outDir, "tags");

	// Consolidação em arquivos únicos (JSON arrays)
	consolidatePages(outDir, "commits", "commits.json");
	consolidatePages(outDir, "contributors", "contributors.json");
	consolidatePages(outDir, "issues", "issues.json");
	consolidatePages(outDir, "pulls", "pulls.json");
	consolidatePages(outDir, "branches", "branches.json");
	consolidatePages(outDir, "tags", "tags.json");

	// Foco nas interações usuário-usuário
	fetchPaginated(BASE_URL + "/repos/" + repo.toPath() + "/issues/comments?per_page=100", outDir, "issue-comments");
	fetchPaginated(BASE_URL + "/repos/" + repo.toPath() + "/issues/events?per_page=100", outDir, "issue-events");
	fetchPaginated(BASE_URL + "/repos/" + repo.toPath() + "/pulls/comments?per_page=100", outDir, "pr-review-comments");

	consolidatePages(outDir, "issue-comments", "issue-comments.json");
	consolidatePages(outDir, "issue-events", "issue-events.json");
	consolidatePages(outDir, "pr-review-comments", "pr-review-comments.json");

	// Detalhes por PR (inclui merged_by) + reviews por PR
	fetchPerPullDetailsAndReviews(repo, outDir);

	// Consolida pull.json e reviews.json individuais em arrays maiores
	consolidatePerPull(outDir);

		log("Mineração concluída para %s. Arquivos em: %s", repo.toPath(), outDir.toAbsolutePath());
	}

	/**
	 * Lê as páginas já baixadas de "pulls-*.json", extrai os números das PRs e
	 * busca os detalhes e as reviews de cada PR individualmente.
	 */
	private void fetchPerPullDetailsAndReviews(RepoId repo, Path outDir) throws IOException, InterruptedException {
		Path pullsDir = outDir; // arquivos pulls-*.json estão no próprio outDir
		List<Integer> prNumbers = new ArrayList<>();

		// Listar arquivos pulls-*.json
		try (java.util.stream.Stream<Path> stream = Files.list(pullsDir)) {
			stream.filter(p -> p.getFileName().toString().startsWith("pulls-") && p.getFileName().toString().endsWith(".json"))
				  .sorted()
				  .forEach(p -> {
					  try {
						  String json = Files.readString(p, StandardCharsets.UTF_8);
						  prNumbers.addAll(extractPrNumbers(json));
					  } catch (IOException e) {
						  throw new UncheckedIOException(e);
					  }
				  });
		}

		if (prNumbers.isEmpty()) {
			log("Nenhum número de PR encontrado para detalhes/reviews.");
			return;
		}

		Path perPullRoot = outDir.resolve("pulls");
		Files.createDirectories(perPullRoot);

		for (Integer n : prNumbers) {
			Path prDir = perPullRoot.resolve(String.valueOf(n));
			Files.createDirectories(prDir);

			// Detalhe da PR (inclui merged_by)
			String prUrl = BASE_URL + "/repos/" + repo.toPath() + "/pulls/" + n;
			Path prDetail = prDir.resolve("pull.json");
			if (!Files.exists(prDetail)) {
				// Alguns PRs muito antigos podem não existir mais ou retornarem 404 — ignore nesses casos
				if (!fetchSingleAllow404(prUrl, prDetail)) {
					log("PR %d: detalhe 404 (ignorado)", n);
					// Sem detalhe, ainda podemos tentar reviews? Em geral 404 também.
				}
			} else {
				log("PR %d: detalhe já existe, pulando.", n);
			}

			// Reviews da PR (para aprovações/reviews)
			String reviewsUrl = BASE_URL + "/repos/" + repo.toPath() + "/pulls/" + n + "/reviews?per_page=100";
			try {
				fetchPaginated(reviewsUrl, prDir, "reviews");
				consolidatePages(prDir, "reviews", "reviews.json");
			} catch (RuntimeException ex) {
				String msg = ex.getMessage();
				if (msg != null && msg.contains("Falha HTTP 404")) {
					log("PR %d: reviews 404 (ignorado)", n);
				} else {
					throw ex;
				}
			}
		}
	}

	/**
	 * Consolida todos os pull.json e reviews.json em arquivos únicos:
	 *  - pull-details.json : array de objetos de cada PR (pull.json)
	 *  - pull-reviews.json : array concatenando todos os arrays de reviews.json
	 */
	private void consolidatePerPull(Path outDir) {
		Path pullsRoot = outDir.resolve("pulls");
		if (!Files.isDirectory(pullsRoot)) return;
		List<String> pullDetails = new ArrayList<>();
		List<String> allReviews = new ArrayList<>();
		try (java.util.stream.Stream<Path> stream = Files.walk(pullsRoot, 1)) {
			stream.filter(Files::isDirectory).forEach(prDir -> {
				if (prDir.equals(pullsRoot)) return; // raiz
				Path pullJson = prDir.resolve("pull.json");
				if (Files.exists(pullJson)) {
					try { pullDetails.add(Files.readString(pullJson, StandardCharsets.UTF_8).trim()); } catch (IOException e) { throw new UncheckedIOException(e); }
				}
				Path reviewsJson = prDir.resolve("reviews.json");
				if (Files.exists(reviewsJson)) {
					try {
						String raw = Files.readString(reviewsJson, StandardCharsets.UTF_8).trim();
						// remover colchetes externos e separar elementos
						int iOpen = raw.indexOf('['); int iClose = raw.lastIndexOf(']');
						String inside = (iOpen >=0 && iClose > iOpen) ? raw.substring(iOpen+1, iClose).trim() : raw;
						if (!inside.isEmpty()) allReviews.add(inside);
					} catch (IOException e) { throw new UncheckedIOException(e); }
				}
			});
		} catch (IOException e) { throw new UncheckedIOException(e); }

		// Escreve pull-details.json
		if (!pullDetails.isEmpty()) {
			Path outDetails = outDir.resolve("pull-details.json");
			try (java.io.BufferedWriter w = Files.newBufferedWriter(outDetails, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				w.write('[');
				boolean first = true;
				for (String obj : pullDetails) {
					String s = obj.trim();
					if (s.isEmpty()) continue;
					if (!first) w.write(',');
					w.write(s);
					first = false;
				}
				w.write(']');
			} catch (IOException e) { throw new UncheckedIOException(e); }
			log("Consolidado pull-details.json (%d PRs)", pullDetails.size());
		}
		// Escreve pull-reviews.json
		if (!allReviews.isEmpty()) {
			Path outReviews = outDir.resolve("pull-reviews.json");
			try (java.io.BufferedWriter w = Files.newBufferedWriter(outReviews, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				w.write('[');
				boolean first = true;
				for (String chunk : allReviews) {
					String s = chunk.trim();
					if (s.isEmpty()) continue;
					// Cada chunk já é lista de objetos separados por vírgula
					// Se não for primeiro, garantir vírgula
					if (!first) w.write(',');
					w.write(s);
					first = false;
				}
				w.write(']');
			} catch (IOException e) { throw new UncheckedIOException(e); }
			log("Consolidado pull-reviews.json (%d blocos)", allReviews.size());
		}
	}

	/**
	 * Variante de fetchSingle que ignora 404 e retorna false nesses casos.
	 */
	private boolean fetchSingleAllow404(String url, Path outFile) throws IOException, InterruptedException {
		HttpRequest request = baseRequest(URI.create(url)).GET().build();
		HttpResponse<String> resp = send(request);
		int code = resp.statusCode();
		if (code == 404) {
			return false;
		}
		ensureSuccess(resp);
		write(outFile, resp.body());
		rateLimitLog(resp.headers());
		return true;
	}

	/**
	 * Extração simples (best-effort) dos números de PRs a partir do JSON de lista de PRs.
	 * Evita dependências externas. A ordem e formato do JSON do GitHub costumam trazer o
	 * campo "number" no nível superior de cada objeto PR.
	 */
	private static List<Integer> extractPrNumbers(String pullsListJson) {
		List<Integer> nums = new ArrayList<>();
		// Regex simples: "number" : 123,
	String regex = "\\\"number\\\"\\s*:\\s*(\\d+)";
		java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
		java.util.regex.Matcher m = p.matcher(pullsListJson);
		while (m.find()) {
			try {
				int n = Integer.parseInt(m.group(1));
				// Evitar capturar números de objetos aninhados extremamente raros (heurística):
				// vamos aceitar e deduplicar abaixo.
				nums.add(n);
			} catch (NumberFormatException ignored) {}
		}
		// Deduplicar mantendo ordem de primeira ocorrência
		List<Integer> dedup = new ArrayList<>();
		for (Integer n : nums) {
			if (!dedup.contains(n)) dedup.add(n);
		}
		return dedup;
	}

	/**
	 * Consolida páginas baseName-*.json em um único arquivo JSON array (outputFileName).
	 * Não faz parsing completo: concatena os elementos dos arrays de cada página, preservando ordem por número da página.
	 */
	private static void consolidatePages(Path dir, String baseName, String outputFileName) {
		List<Path> pages;
		try { pages = listPageFiles(dir, baseName); } catch (IOException e) { throw new UncheckedIOException(e); }
		if (pages.isEmpty()) return;
		Path out = dir.resolve(outputFileName);
		try (java.io.BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			w.write('[');
			boolean first = true;
			for (Path p : pages) {
				String s = Files.readString(p, StandardCharsets.UTF_8).trim();
				if (s.isEmpty()) continue;
				int iOpen = s.indexOf('[');
				int iClose = s.lastIndexOf(']');
				String inside;
				if (iOpen >= 0 && iClose > iOpen) {
					inside = s.substring(iOpen + 1, iClose).trim();
				} else {
					inside = s;
				}
				if (inside.isEmpty()) continue;
				if (!first) w.write(',');
				w.write(inside);
				first = false;
			}
			w.write(']');
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		log("Consolidado %s -> %s", baseName, out.getFileName());
	}

	private static List<Path> listPageFiles(Path dir, String baseName) throws IOException {
		List<Path> files = new ArrayList<>();
		try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
			stream.filter(p -> {
						String n = p.getFileName().toString();
						return n.startsWith(baseName + "-") && n.endsWith(".json");
					})
				  .forEach(files::add);
		}
		files.sort(Comparator.comparingInt(p -> extractPageNumber(p.getFileName().toString(), baseName)));
		return files;
	}

	private static int extractPageNumber(String fileName, String baseName) {
		// Ex.: baseName-12.json -> 12
		try {
			String withoutPrefix = fileName.substring((baseName + "-").length());
			int dot = withoutPrefix.indexOf('.');
			String numStr = dot >= 0 ? withoutPrefix.substring(0, dot) : withoutPrefix;
			return Integer.parseInt(numStr);
		} catch (Exception ignored) {
			return Integer.MAX_VALUE; // arquivos sem número vão para o fim
		}
	}

	private void fetchSingle(String url, Path outFile) throws IOException, InterruptedException {
		HttpRequest request = baseRequest(URI.create(url)).GET().build();
		HttpResponse<String> resp = send(request);
		ensureSuccess(resp);
		write(outFile, resp.body());
		rateLimitLog(resp.headers());
	}

	private void fetchPaginated(String firstUrl, Path outDir, String baseName) throws IOException, InterruptedException {
		int page = 1;
		String url = firstUrl;

		// Resume: se já existirem páginas, começa da próxima
		int maxExisting = maxExistingPage(outDir, baseName);
		if (maxExisting > 0) {
			page = maxExisting + 1;
			url = setPageParam(firstUrl, page);
			log("Resuming %s a partir da página %d...", baseName, page);
		}
		while (url != null) {
			log("Baixando %s (página %d)...", baseName, page);
			HttpRequest req = baseRequest(URI.create(url)).GET().build();
			HttpResponse<String> resp = send(req);
			ensureSuccess(resp);

			Path outFile = outDir.resolve(baseName + "-" + page + ".json");
			write(outFile, resp.body());
			rateLimitLog(resp.headers());

			url = parseNextLink(resp.headers()).orElse(null);
			page++;
		}
	}

	private static int maxExistingPage(Path dir, String baseName) {
		try {
			List<Path> pages = listPageFiles(dir, baseName);
			if (pages.isEmpty()) return 0;
			Path last = pages.get(pages.size() - 1);
			return extractPageNumber(last.getFileName().toString(), baseName);
		} catch (IOException e) {
			return 0;
		}
	}

	private static String setPageParam(String url, int page) {
		if (url.contains("page=")) {
			return url.replaceAll("([?&])page=\\d+", "$1page=" + page);
		}
		String sep = url.contains("?") ? "&" : "?";
		return url + sep + "page=" + page;
	}

	private HttpRequest.Builder baseRequest(URI uri) {
		HttpRequest.Builder b = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(60))
				.header("Accept", "application/vnd.github+json")
				.header("User-Agent", USER_AGENT);
		if (token != null) {
			// GitHub PATs tradicionais usam prefixo 'token'. Fine-grained também aceitam 'Bearer', mas 'token' garante compatibilidade.
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

	private static void write(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Optional<String> parseNextLink(HttpHeaders headers) {
		Optional<List<String>> values = headers.map().entrySet().stream()
				.filter(e -> e.getKey().equalsIgnoreCase("link"))
				.map(Map.Entry::getValue)
				.findFirst();
		if (values.isEmpty()) return Optional.empty();
		for (String full : values.get()) {
			// Formato: <https://api.github.com/...&page=2>; rel="next", <...>; rel="last"
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
			log("Rate limit: %s/%s restante(s)%s",
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

	private static void log(String fmt, Object... args) {
		String msg = String.format(Locale.ROOT, fmt, args);
		System.out.println("[Mineracao] " + msg);
	}
}
