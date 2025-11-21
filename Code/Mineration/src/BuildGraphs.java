import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * BuildGraphs: Etapa 2 – lê JSONs consolidados e gera CSVs dos grafos.
 * Uso:
 *   java BuildGraphs [owner/repo] [dataDir] [outDir]
 * Defaults:
 *   owner/repo: tenta .env ou .git/config, senão spring-projects/spring-boot
 *   dataDir: ./data
 *   outDir: <dataDir>/<owner>/<repo>/graphs
 */
public class BuildGraphs {
    public static void main(String[] args) throws Exception {
        String owner = "spring-projects";
        String repo = "spring-boot";
        Path dataDir = Paths.get("data");
        Path outDir = null; // será definido depois

        EnvVars env = readEnvVars();
        if (env.owner != null && env.repo != null) { owner = env.owner; repo = env.repo; }
        if (args.length >= 1 && args[0] != null && !args[0].isBlank()) {
            String[] parts = args[0].trim().split("/");
            if (parts.length == 2) { owner = parts[0]; repo = parts[1]; }
        }
        if ((env.owner == null || env.repo == null) && args.length == 0) {
            String[] git = tryParseGitRemote();
            if (git != null) { owner = git[0]; repo = git[1]; }
        }
        if (args.length >= 2 && args[1] != null && !args[1].isBlank()) {
            dataDir = Paths.get(args[1]);
        }
        if (args.length >= 3 && args[2] != null && !args[2].isBlank()) {
            outDir = Paths.get(args[2]);
        }
        if (outDir == null) {
            outDir = dataDir.resolve(owner).resolve(repo).resolve("graphs");
        }

        Path repoDir = dataDir.resolve(owner).resolve(repo);
        if (!Files.isDirectory(repoDir)) {
            System.err.println("[BuildGraphs] Diretório de dados não encontrado: " + repoDir.toAbsolutePath());
            System.exit(1);
        }

        System.out.println("[BuildGraphs] Construindo grafos para: " + owner + "/" + repo);

        GraphBuilder builder = new GraphBuilder(repoDir);
        GraphBuilder.RepoMaps maps = builder.loadRepoMaps();
        GraphModel g1 = builder.buildGraph1_Comments(maps);
        GraphModel g2 = builder.buildGraph2_IssueClosures(maps);
        GraphModel g3 = builder.buildGraph3_PRInteractions(maps);
        GraphModel gi = builder.buildIntegrated(maps);

        Files.createDirectories(outDir);
        g1.exportEdgesCsv(outDir.resolve("graph1_comments.csv"));
        g2.exportEdgesCsv(outDir.resolve("graph2_issue_closures.csv"));
        g3.exportEdgesCsv(outDir.resolve("graph3_pr_interactions.csv"));
        gi.exportEdgesCsv(outDir.resolve("graph_integrated.csv"));

        System.out.println("[BuildGraphs] Grafos gerados em: " + outDir.toAbsolutePath());
    }

    // ---------------- .env & git remote helpers (reuso leve do App) ----------------
    private static class EnvVars { String owner; String repo; }
    private static EnvVars readEnvVars() {
        EnvVars vars = new EnvVars();
        for (Path p : candidateEnvPaths()) {
            if (!Files.exists(p)) continue;
            try {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#")) continue;
                    int eq = s.indexOf('=');
                    if (eq < 0) continue;
                    String key = s.substring(0, eq).trim();
                    String value = s.substring(eq + 1).trim();
                    if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    switch (key) {
                        case "OWNER":
                        case "REPO_OWNER": vars.owner = value; break;
                        case "REPO":
                        case "REPO_NAME": vars.repo = value; break;
                    }
                }
            } catch (Exception ignored) {}
        }
        return vars;
    }
    private static List<Path> candidateEnvPaths() {
        List<Path> list = new ArrayList<>();
        Path cwd = Paths.get(".").toAbsolutePath();
        for (int i = 0; i < 6 && cwd != null; i++) {
            list.add(cwd.resolve(".env"));
            cwd = cwd.getParent();
        }
        list.add(Paths.get("Code","Mineration",".env"));
        return list;
    }
    private static String[] tryParseGitRemote() {
        Path gitConfig = Paths.get(".git").resolve("config");
        if (!Files.exists(gitConfig)) return null;
        try {
            String all = Files.readString(gitConfig, StandardCharsets.UTF_8);
            for (String line : all.split("\n")) {
                String s = line.trim();
                if (s.startsWith("url = ")) {
                    String url = s.substring("url = ".length()).trim();
                    String regex = "(?:github\\.com[/:])([^/]+)/([^/]+)(?:\\.git)?$";
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
                    java.util.regex.Matcher m = p.matcher(url);
                    if (m.find()) {
                        return new String[]{m.group(1), m.group(2)};
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
