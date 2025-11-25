package mineracao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Construtor de grafos (Etapa 2) a partir dos JSONs minerados na Etapa 1.
 * Não usa bibliotecas externas de JSON; faz parsing leve via regex dos campos necessários.
 */
public class GraphBuilder {
	private final Path repoDir; // caminho: <data>/<owner>/<repo>

	// Pesos padrão sugeridos
	public static class Weights {
		public double commentGeneric = 2.0;  // comentário em PR
		public double issueComment = 3.0;    // comentário em issue (relacionado à abertura)
		public double reviewOrApproval = 4.0; // review/aprovação de PR
		public double merge = 5.0;            // merge de PR
		public double issueClosed = 3.0;      // (assumido) fechamento de issue por outro usuário
	}

	private final Weights W = new Weights();

	public GraphBuilder(Path repoDir) {
		this.repoDir = Objects.requireNonNull(repoDir);
	}

	public static class RepoMaps {
		public final Map<Integer,String> issueAuthor = new HashMap<>();
		public final Map<Integer,Boolean> issueIsPR = new HashMap<>();
		public final Map<Integer,String> prAuthor = new HashMap<>();
		public final Map<Integer,String> prMergedBy = new HashMap<>();
	}

	public RepoMaps loadRepoMaps() {
		RepoMaps maps = new RepoMaps();
		String issues = readIfExists(repoDir.resolve("issues.json"));
		if (issues != null) parseIssuesList(issues, maps);
		String pulls = readIfExists(repoDir.resolve("pulls.json"));
		if (pulls != null) parsePullsList(pulls, maps);
		System.out.println("[GraphBuilder] Mapas carregados: " + maps.prAuthor.size() + " PRs com author, " + maps.prMergedBy.size() + " PRs com merged_by, " + maps.issueAuthor.size() + " issues");
		return maps;
	}

	public GraphModel buildGraph1_Comments(RepoMaps maps) {
		GraphModel g = new GraphModel();
		// Issue comments
		String issueComments = readIfExists(repoDir.resolve("issue-comments.json"));
		if (issueComments != null) {
			List<CommentRef> list = parseIssueComments(issueComments);
			for (CommentRef c : list) {
				String opener = maps.issueAuthor.get(c.number);
				if (opener == null) continue;
				boolean isPR = maps.issueIsPR.getOrDefault(c.number, false);
				double w = isPR ? W.commentGeneric : W.issueComment;
				g.addEdge(c.user, opener, w, isPR ? "pr_comment" : "issue_comment");
			}
		}
		// PR review comments
		String prReviewComments = readIfExists(repoDir.resolve("pr-review-comments.json"));
		if (prReviewComments != null) {
			List<CommentRef> list = parsePrReviewComments(prReviewComments);
			for (CommentRef c : list) {
				String opener = maps.prAuthor.get(c.number);
				if (opener == null) continue;
				g.addEdge(c.user, opener, W.commentGeneric, "pr_review_comment");
			}
		}
		return g;
	}

	public GraphModel buildGraph2_IssueClosures(RepoMaps maps) {
		GraphModel g = new GraphModel();
		
		// Ler do arquivo processado
		Path closuresPath = repoDir.resolve("issue-events.json");
		if (!Files.isRegularFile(closuresPath)) {
			System.out.println("Aviso: arquivo " + closuresPath + " não encontrado. Graph 2 vazio.");
			return g;
		}

		String eventsJson;
		try {
			eventsJson = Files.readString(closuresPath, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		int count = processIssueClosureEvents(eventsJson, maps, g);
		System.out.println("Eventos de fechamento processados: " + count);
		return g;
	}

	public GraphModel buildGraph3_PRInteractions(RepoMaps maps) {
		GraphModel g = new GraphModel();
		boolean hadPRData = false;
		try {
			Path pullsDir = repoDir.resolve("pulls");
			if (Files.isDirectory(pullsDir)) {
				hadPRData = true;
				try (DirectoryStream<Path> ds = Files.newDirectoryStream(pullsDir)) {
					int reviewEdges = 0;
					int mergeEdges = 0;
					for (Path prDir : ds) {
						if (!Files.isDirectory(prDir)) continue;
						int number;
						try { number = Integer.parseInt(prDir.getFileName().toString()); }
						catch (NumberFormatException ex) { continue; }
						String author = maps.prAuthor.get(number);
						if (author == null) {
							String prDetail = readIfExists(prDir.resolve("pull.json"));
							if (prDetail != null) {
								String a = extractFirst(prDetail, USER_LOGIN_RE);
								if (a != null) author = a;
							}
						}
						if (author == null) continue;

						String reviews = readIfExists(prDir.resolve("reviews.json"));
						if (reviews != null) {
							for (ReviewRef r : parseReviews(reviews)) {
								if (r.user == null || r.user.isBlank()) continue;
								String tag;
								if ("approved".equalsIgnoreCase(r.state)) tag = "pr_approved";
								else if ("changes_requested".equalsIgnoreCase(r.state)) tag = "pr_changes_requested";
								else tag = "pr_review";
								g.addEdge(r.user, author, W.reviewOrApproval, tag);
								reviewEdges++;
							}
						}

						String prDetail = readIfExists(prDir.resolve("pull.json"));
						if (prDetail != null) {
							String mergedFlag = extractFirst(prDetail, MERGED_FLAG_RE);
							if ("true".equalsIgnoreCase(mergedFlag)) {
								String merger = extractFirst(prDetail, MERGED_BY_LOGIN_RE);
								if (merger != null && !merger.isBlank() && !merger.equals(author)) {
									g.addEdge(merger, author, W.merge, "pr_merged");
									mergeEdges++;
								}
							}
						}
					}
					System.out.println("[GraphBuilder] Grafo3 (modo diretórios) reviewEdges="+reviewEdges+" mergeEdges="+mergeEdges);
				}
			} else {
				// Streaming parse do arquivo gigante pull-details.json (NOVO FORMATO: pr_details + reviews)
				Path pullDetailsPath = repoDir.resolve("pull-details.json");
				if (Files.exists(pullDetailsPath)) {
					hadPRData = true;
					int[] reviewEdges = {0};
					int[] approvalEdges = {0};
					int[] mergeEdges = {0};
					int[] totalObjs = {0};
					long startTime = System.currentTimeMillis();
					
					streamTopLevelObjects(pullDetailsPath, obj -> {
						try {
						totalObjs[0]++;
						if (totalObjs[0] % 500 == 0) {
							System.out.print("\r[GraphBuilder] Grafo3: " + totalObjs[0] + " PRs, reviews=" + reviewEdges[0] + " aprovações=" + approvalEdges[0] + " merges=" + mergeEdges[0]);
						}
						
						// NOVO FORMATO: {"pr_details":{...},"reviews":[...]}
						// Extrair pr_details
						int prDetailsStart = obj.indexOf("\"pr_details\"");
						if (prDetailsStart < 0) return; // Formato antigo, tentar processar direto
						
						int detailsObjStart = obj.indexOf('{', prDetailsStart);
						if (detailsObjStart < 0) return;
						int detailsObjEnd = nextObjectEnd(obj, detailsObjStart);
						if (detailsObjEnd <= detailsObjStart) return;
						String prDetails = obj.substring(detailsObjStart, detailsObjEnd);
						
						// Extrair number do pr_details
						String numStr = extractFirst(prDetails, PR_NUMBER_FIELD_RE);
						if (numStr == null) return;
						int number;
						try { number = Integer.parseInt(numStr); } catch (NumberFormatException ex) { return; }
						
						// Extrair author do pr_details
						String author = extractFirst(prDetails, USER_LOGIN_RE);
						if (author == null) {
							author = maps.prAuthor.get(number);
						} else {
							maps.prAuthor.put(number, author);
						}
						if (author == null) return;
						
						// EXTRAIR DADOS DE MERGE do pr_details
						String mergedStr = extractFirst(prDetails, MERGED_FLAG_RE);
						if ("true".equalsIgnoreCase(mergedStr)) {
							String mergedBy = extractFirst(prDetails, MERGED_BY_LOGIN_RE);
							if (mergedBy != null && !mergedBy.equalsIgnoreCase(author)) {
								maps.prMergedBy.put(number, mergedBy);
								g.addEdge(mergedBy, author, W.merge, "pr_merged");
								mergeEdges[0]++;
							}
						}
						
						// Extrair reviews array
						int reviewsStart = obj.indexOf("\"reviews\"", detailsObjEnd);
						if (reviewsStart < 0) return;
						int reviewsArrayStart = obj.indexOf('[', reviewsStart);
						if (reviewsArrayStart < 0) return;
						int reviewsArrayEnd = nextArrayEnd(obj, reviewsArrayStart);
						if (reviewsArrayEnd <= reviewsArrayStart) return;
						String reviewsSection = obj.substring(reviewsArrayStart, reviewsArrayEnd);
						
						// Buscar reviews (submitted_at indica uma review submetida)
						Pattern submittedPattern = Pattern.compile("\"submitted_at\"\\s*:\\s*\"[^\"]+\"");
						Pattern userPattern = Pattern.compile("\"user\"\\s*:\\s*\\{[^}]*?\"login\"\\s*:\\s*\"([^\"]+)\"");
						Pattern statePattern = Pattern.compile("\"state\"\\s*:\\s*\"([A-Z_]+)\"");
						
						Matcher submittedMatcher = submittedPattern.matcher(reviewsSection);
						while (submittedMatcher.find()) {
							// Buscar user e state próximos a este submitted_at
							int reviewStart = Math.max(0, submittedMatcher.start() - 1000);
							int reviewEnd = Math.min(reviewsSection.length(), submittedMatcher.end() + 500);
							String reviewPart = reviewsSection.substring(reviewStart, reviewEnd);
							
							Matcher userMatcher = userPattern.matcher(reviewPart);
							Matcher stateMatcher = statePattern.matcher(reviewPart);
							
							String reviewer = null;
							String state = null;
							
							if (userMatcher.find()) reviewer = userMatcher.group(1);
							if (stateMatcher.find()) state = stateMatcher.group(1);
							
							if (reviewer != null && state != null && !reviewer.equalsIgnoreCase(author)) {
								String tag;
								if ("APPROVED".equals(state)) {
									tag = "pr_approved";
									approvalEdges[0]++;
								} else if ("CHANGES_REQUESTED".equals(state)) {
									tag = "pr_changes_requested";
									reviewEdges[0]++;
								} else if ("COMMENTED".equals(state)) {
									tag = "pr_review";
									reviewEdges[0]++;
								} else {
									tag = "pr_review";
									reviewEdges[0]++;
								}
								g.addEdge(reviewer, author, W.reviewOrApproval, tag);
							}
						}
						} catch (Exception e) {
							// silenciar erros de parse individual
						}
					});
					long elapsed = (System.currentTimeMillis() - startTime) / 1000;
					System.out.println("\n[GraphBuilder] Grafo3 concluído: reviews=" + reviewEdges[0] + " aprovações=" + approvalEdges[0] + " merges=" + mergeEdges[0] + " tempo=" + elapsed + "s");
				}
			}
			// Não precisamos mais chamar addMergeEdges separadamente, já processamos merges acima
			if (!hadPRData) System.out.println("Nenhum dado de PR (pulls/ ou pull-details.json) encontrado.");
		} catch (Throwable t) {
			System.err.println("[GraphBuilder] ERRO ao construir Grafo 3: "+t.getClass().getName()+" - "+t.getMessage());
			t.printStackTrace();
		}
		return g;
	}

	public GraphModel buildIntegrated(RepoMaps maps) {
		GraphModel g = new GraphModel();
		// Reaproveitar construção dos três grafos e somar
		mergeInto(g, buildGraph1_Comments(maps));
		mergeInto(g, buildGraph2_IssueClosures(maps));
		mergeInto(g, buildGraph3_PRInteractions(maps));
		return g;
	}

	private static void mergeInto(GraphModel target, GraphModel source) {
		for (GraphModel.Edge e : source.getEdges()) {
			// usa o peso e soma as tags
			target.addEdge(e.from, e.to, e.weight, joinTagNames(e.tagCounts.keySet()));
		}
	}

	private static String joinTagNames(Collection<String> tags) {
		if (tags == null || tags.isEmpty()) return null;
		return String.join("+", new TreeSet<>(tags));
	}

	// ----------------------- Parsing helpers -----------------------

	private static String readIfExists(Path p) {
		try {
			if (p == null || !Files.exists(p)) return null;
			return Files.readString(p, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static final Pattern NUMBER_RE = Pattern.compile("\"number\"\s*:\s*(\\d+)");
	private static final Pattern USER_LOGIN_RE = Pattern.compile("\"user\"\s*:\s*\\{[^}]*\"login\"\s*:\s*\"([^\"]+)\"");
	private static final Pattern PULL_REQUEST_FIELD_RE = Pattern.compile("\"pull_request\"\s*:\s*\\{");
	private static final Pattern ISSUE_COMMENT_ISSUE_URL_RE = Pattern.compile("\"issue_url\"\s*:\s*\"[^\"]*/issues/(\\d+)\"");
	private static final Pattern PR_COMMENT_PR_URL_RE = Pattern.compile("\"pull_request_url\"\s*:\s*\"[^\"]*/pulls/(\\d+)\"");
	private static final Pattern ACTOR_LOGIN_RE = Pattern.compile("\"actor\"\s*:\s*\\{[^}]*\"login\"\s*:\s*\"([^\"]+)\"");
	private static final Pattern CLOSED_EVENT_RE = Pattern.compile("\"event\"\s*:\s*\"closed\"");
	private static final Pattern ISSUE_NUMBER_IN_EVENT = Pattern.compile("\"issue\"\s*:\s*\\{[^}]*\"number\"\s*:\s*(\\d+)");
	private static final Pattern PR_NUMBER_FIELD_RE = Pattern.compile("\"(?:pr_)?number\"\s*:\s*(\\d+)");
	private static final Pattern REVIEW_STATE_RE = Pattern.compile("\"state\"\s*:\s*\"([^\"]+)\"");
	private static final Pattern MERGED_FLAG_RE = Pattern.compile("\"merged\"\s*:\s*(true|false)");
	private static final Pattern MERGED_BY_LOGIN_RE = Pattern.compile("\"merged_by\"\s*:\s*\\{[^}]*\"login\"\s*:\s*\"([^\"]+)\"");
	private static final Pattern ISSUE_USER_LOGIN_RE = Pattern.compile("\"issue\"\s*:\s*\\{[^}]*\"user\"\s*:\s*\\{[^}]*\"login\"\s*:\s*\"([^\"]+)\"");

	private void parseIssuesList(String json, RepoMaps maps) {
		// Iterate roughly by objects using a matcher for top-level occurrences of "number"
		Matcher m = NUMBER_RE.matcher(json);
		int lastIdx = 0;
		while (m.find(lastIdx)) {
			int num;
			try { num = Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { lastIdx = m.end(); continue; }
			// extract small window around to find user.login and pull_request presence
			int objStart = Math.max(0, json.lastIndexOf('{', m.start()));
			int objEnd = nextObjectEnd(json, objStart);
			if (objEnd <= objStart) { lastIdx = m.end(); continue; }
			String obj = json.substring(objStart, objEnd);
			String author = extractFirst(obj, USER_LOGIN_RE);
			boolean isPR = contains(obj, PULL_REQUEST_FIELD_RE);
			if (author != null) maps.issueAuthor.put(num, author);
			maps.issueIsPR.put(num, isPR);
			lastIdx = objEnd;
		}
	}

	private void parsePullsList(String json, RepoMaps maps) {
		Matcher m = NUMBER_RE.matcher(json);
		int lastIdx = 0;
		while (m.find(lastIdx)) {
			int num;
			try { num = Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { lastIdx = m.end(); continue; }
			int objStart = Math.max(0, json.lastIndexOf('{', m.start()));
			int objEnd = nextObjectEnd(json, objStart);
			if (objEnd <= objStart) { lastIdx = m.end(); continue; }
			String obj = json.substring(objStart, objEnd);
			String author = extractFirst(obj, USER_LOGIN_RE);
			if (author != null) maps.prAuthor.put(num, author);
			String merged = extractFirst(obj, MERGED_BY_LOGIN_RE);
			if (merged != null) maps.prMergedBy.put(num, merged);
			lastIdx = objEnd;
		}
	}

	private static class CommentRef { String user; int number; }

	private List<CommentRef> parseIssueComments(String json) {
		List<CommentRef> list = new ArrayList<>();
		Matcher mIssue = ISSUE_COMMENT_ISSUE_URL_RE.matcher(json);
		int lastIdx = 0;
		while (mIssue.find(lastIdx)) {
			int num;
			try { num = Integer.parseInt(mIssue.group(1)); } catch (NumberFormatException e) { lastIdx = mIssue.end(); continue; }
			int objStart = Math.max(0, json.lastIndexOf('{', mIssue.start()));
			int objEnd = nextObjectEnd(json, objStart);
			String obj = objEnd > objStart ? json.substring(objStart, objEnd) : null;
			String user = obj != null ? extractFirst(obj, USER_LOGIN_RE) : null;
			if (user != null) {
				CommentRef c = new CommentRef();
				c.user = user; c.number = num;
				list.add(c);
			}
			lastIdx = mIssue.end();
		}
		return list;
	}

	private List<CommentRef> parsePrReviewComments(String json) {
		List<CommentRef> list = new ArrayList<>();
		Matcher mPr = PR_COMMENT_PR_URL_RE.matcher(json);
		int lastIdx = 0;
		while (mPr.find(lastIdx)) {
			int num;
			try { num = Integer.parseInt(mPr.group(1)); } catch (NumberFormatException e) { lastIdx = mPr.end(); continue; }
			int objStart = Math.max(0, json.lastIndexOf('{', mPr.start()));
			int objEnd = nextObjectEnd(json, objStart);
			String obj = objEnd > objStart ? json.substring(objStart, objEnd) : null;
			String user = obj != null ? extractFirst(obj, USER_LOGIN_RE) : null;
			if (user != null) {
				CommentRef c = new CommentRef();
				c.user = user; c.number = num;
				list.add(c);
			}
			lastIdx = mPr.end();
		}
		return list;
	}

	// parseIssueEvents removido - agora fazemos streaming line-by-line no buildGraph2_IssueClosures

	private static class ReviewRef { String user; String state; }
	private List<ReviewRef> parseReviews(String json) {
		List<ReviewRef> list = new ArrayList<>();
		Matcher m = REVIEW_STATE_RE.matcher(json);
		int lastIdx = 0;
		while (m.find(lastIdx)) {
			int objStart = Math.max(0, json.lastIndexOf('{', m.start()));
			int objEnd = nextObjectEnd(json, objStart);
			if (objEnd <= objStart) { lastIdx = m.end(); continue; }
			String obj = json.substring(objStart, objEnd);
			String user = extractFirst(obj, USER_LOGIN_RE);
			String state = extractFirst(obj, REVIEW_STATE_RE);
			if (user != null && state != null) {
				ReviewRef r = new ReviewRef();
				r.user = user; r.state = state;
				list.add(r);
			}
			lastIdx = objEnd;
		}
		return list;
	}

	private int processIssueClosureEvents(String json, RepoMaps maps, GraphModel g) {
		if (json == null || json.isBlank()) return 0;
		int[] count = {0};
		processTopLevelJsonObjects(json, obj -> count[0] += processIssueEventObject(obj, maps, g));
		return count[0];
	}

	private int processIssueEventObject(String obj, RepoMaps maps, GraphModel g) {
		if (obj == null || !CLOSED_EVENT_RE.matcher(obj).find()) return 0;
		String actor = extractFirst(obj, ACTOR_LOGIN_RE);
		String numStr = extractFirst(obj, ISSUE_NUMBER_IN_EVENT);
		String issueOpener = extractFirst(obj, ISSUE_USER_LOGIN_RE);
		if (actor == null || numStr == null) return 0;
		int number;
		try { number = Integer.parseInt(numStr); }
		catch (NumberFormatException e) {
			System.out.println("Número inválido encontrado: " + numStr);
			return 0;
		}
		String opener = issueOpener != null ? issueOpener : maps.issueAuthor.get(number);
		if (opener != null && !actor.equalsIgnoreCase(opener)) {
			g.addEdge(actor, opener, W.issueClosed, "issue_closed");
			return 1;
		}
		return 0;
	}

	private static void processTopLevelJsonObjects(String json, java.util.function.Consumer<String> consumer) {
		boolean inObject = false;
		boolean inString = false;
		boolean escape = false;
		int depth = 0;
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < json.length(); i++) {
			char c = json.charAt(i);
			if (!inObject) {
				if (c == '{') {
					inObject = true;
					depth = 1;
					buffer.setLength(0);
					buffer.append(c);
					inString = false;
					escape = false;
				}
				continue;
			}
			buffer.append(c);
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
			if (!inString) {
				if (c == '{') depth++;
				else if (c == '}') {
					depth--;
					if (depth == 0) {
						consumer.accept(buffer.toString());
						inObject = false;
					}
				}
			}
		}
	}

	private void streamTopLevelObjects(Path path, java.util.function.Consumer<String> consumer) {
		try (java.io.BufferedReader reader = new java.io.BufferedReader(
				Files.newBufferedReader(path, StandardCharsets.UTF_8), 128*1024)) {
			StringBuilder buffer = new StringBuilder(8192);
			boolean inObject = false;
			boolean inString = false;
			boolean escape = false;
			int depth = 0;
			int ch;
			while ((ch = reader.read()) != -1) {
				char c = (char) ch;
				if (!inObject) {
					if (c == '{') {
						inObject = true;
						depth = 1;
						buffer.setLength(0);
						buffer.append(c);
						inString = false;
						escape = false;
					}
					continue;
				}
				buffer.append(c);
				if (escape) { escape = false; continue; }
				if (c == '\\') { escape = true; continue; }
				if (c == '"') { inString = !inString; continue; }
				if (!inString) {
					if (c == '{') depth++;
					else if (c == '}') {
						depth--;
						if (depth == 0) {
							consumer.accept(buffer.toString());
							inObject = false;
						}
					}
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}


	private int addMergeEdges(RepoMaps maps, GraphModel g) {
		int count = 0;
		for (Map.Entry<Integer,String> entry : maps.prMergedBy.entrySet()) {
			String author = maps.prAuthor.get(entry.getKey());
			String merger = entry.getValue();
			if (author == null || merger == null) continue;
			if (!merger.equalsIgnoreCase(author)) {
				g.addEdge(merger, author, W.merge, "pr_merged");
				count++;
			}
		}
		return count;
	}

	private static String extractFirst(String text, Pattern p) {
		Matcher m = p.matcher(text);
		return m.find() ? m.group(1) : null;
	}
	private static boolean contains(String text, Pattern p) {
		return p.matcher(text).find();
	}

	/**
	 * Tenta encontrar o fim de um objeto JSON balanceando chaves. É heurístico, mas suficiente
	 * para obter um slice aproximado do objeto atual.
	 */
	private static int nextObjectEnd(String s, int startIdx) {
		if (startIdx < 0 || startIdx >= s.length() || s.charAt(startIdx) != '{') return startIdx + 1;
		int depth = 0;
		boolean inStr = false;
		for (int i = startIdx; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '"') {
				boolean escaped = i > 0 && s.charAt(i-1) == '\\';
				if (!escaped) inStr = !inStr;
			}
			if (inStr) continue;
			if (c == '{') depth++;
			else if (c == '}') { depth--; if (depth == 0) return i + 1; }
		}
		return s.length();
	}

	private static int nextArrayEnd(String s, int startIdx) {
		if (startIdx < 0 || startIdx >= s.length() || s.charAt(startIdx) != '[') return startIdx + 1;
		int depth = 0;
		boolean inStr = false;
		for (int i = startIdx; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '"') {
				boolean escaped = i > 0 && s.charAt(i-1) == '\\';
				if (!escaped) inStr = !inStr;
			}
			if (inStr) continue;
			if (c == '[') depth++;
			else if (c == ']') { depth--; if (depth == 0) return i + 1; }
		}
		return s.length();
	}
}
