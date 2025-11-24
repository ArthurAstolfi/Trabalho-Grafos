import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Modelo simples de grafo dirigido e simples (sem multiarestas explícitas).
 * - Nós: identificados pelo login (String)
 * - Arestas: (from -> to), com peso acumulado (double) e contagem (int), mais tags por tipo de interação
 */
public class GraphModel {
	public static class EdgeKey {
		public final String from;
		public final String to;
		public EdgeKey(String from, String to) {
			this.from = from;
			this.to = to;
		}
		@Override public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof EdgeKey)) return false;
			EdgeKey e = (EdgeKey) o;
			return Objects.equals(from, e.from) && Objects.equals(to, e.to);
		}
		@Override public int hashCode() { return Objects.hash(from, to); }
	}

	public static class Edge {
		public final String from;
		public final String to;
		public double weight; // peso total acumulado
		public int count;     // quantidade total de eventos
		public final Map<String, Integer> tagCounts = new HashMap<>(); // tipo -> contagem
		public Edge(String from, String to) {
			this.from = from;
			this.to = to;
			this.weight = 0.0;
			this.count = 0;
		}
		public void add(double w, String tag) {
			this.weight += w;
			this.count += 1;
			if (tag != null && !tag.isBlank()) {
				this.tagCounts.merge(tag, 1, Integer::sum);
			}
		}
	}

	private final Set<String> nodes = new HashSet<>();
	private final Map<EdgeKey, Edge> edges = new HashMap<>();

	public void addNode(String login) {
		if (login == null || login.isBlank()) return;
		nodes.add(login);
	}

	public void addEdge(String from, String to, double weight, String tag) {
		if (from == null || to == null || from.isBlank() || to.isBlank()) return;
		if (from.equals(to)) return; // simples: não cria laço
		addNode(from);
		addNode(to);
		EdgeKey k = new EdgeKey(from, to);
		Edge e = edges.computeIfAbsent(k, kk -> new Edge(from, to));
		e.add(weight, tag);
	}

	public Set<String> getNodes() { return Collections.unmodifiableSet(nodes); }
	public Collection<Edge> getEdges() { return Collections.unmodifiableCollection(edges.values()); }

	public void exportEdgesCsv(Path outFile) {
		try {
			Files.createDirectories(outFile.getParent());
			StringBuilder sb = new StringBuilder();
			sb.append("source,target,weight,count,tags\n");
			for (Edge e : getEdges()) {
				String tags = encodeTags(e.tagCounts);
				sb.append(escape(e.from)).append(',')
				  .append(escape(e.to)).append(',')
				  .append(String.format(Locale.ROOT, "%.1f", e.weight)).append(',')
				  .append(e.count).append(',')
				  .append(escape(tags)).append('\n');
			}
			Files.writeString(outFile, sb.toString(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static String encodeTags(Map<String,Integer> tagCounts) {
		if (tagCounts.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Map.Entry<String,Integer> en : tagCounts.entrySet()) {
			if (!first) sb.append(';');
			sb.append(en.getKey()).append(':').append(en.getValue());
			first = false;
		}
		return sb.toString();
	}

	private static String escape(String s) {
		if (s == null) return "";
		if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
			return '"' + s.replace("\"", "\"\"") + '"';
		}
		return s;
	}
}
