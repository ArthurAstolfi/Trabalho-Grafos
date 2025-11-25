package mineracao;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import mineracao.Mineracao.RepoId;

public class App {
    public static void main(String[] args) throws Exception {
        // Uso:
        //  - Argumento 1 (opcional): owner/repo (ex.: torvalds/linux)
        //  - Argumento 2 (opcional): diretório de saída (padrão: ./data)
        //  - OWNER / REPO / OUT_DIR podem vir do .env
        //  - Fallback final: tentar inferir de .git/config (remote origin)

        String owner = "spring-projects"; // defaults caso nada seja fornecido
        String name = "spring-boot";
        Path out = Paths.get("data");

        EnvVars env = readEnvVars();
        if (env.owner != null && env.repo != null) {
            owner = env.owner; name = env.repo;
            System.out.println("[App] OWNER/REPO do .env: " + owner + "/" + name);
        }
        if (env.outDir != null) {
            out = Paths.get(env.outDir);
            System.out.println("[App] OUT_DIR do .env: " + out);
        }

        if (args.length >= 1 && args[0] != null && !args[0].isBlank()) {
            String[] parts = args[0].trim().split("/");
            if (parts.length == 2) {
                owner = parts[0];
                name = parts[1];
            } else {
                System.out.println("[App] Aviso: argumento 1 deve ser 'owner/repo'. Ignorando.");
            }
        }
        if (args.length >= 2 && args[1] != null && !args[1].isBlank()) {
            out = Paths.get(args[1]);
        }

        if ((env.owner == null || env.repo == null) && args.length == 0) {
            String[] gitPair = tryParseGitRemote();
            if (gitPair != null) {
                owner = gitPair[0];
                name = gitPair[1];
                System.out.println("[App] Usando owner/repo do git remote: " + owner + "/" + name);
            }
        }

        String token = resolveToken();
        if (token == null) {
            System.out.println("[App] Atenção: sem token GitHub; limite de rate será baixo (60/h)." );
        }
        Mineracao miner = new Mineracao(token);
        miner.mineRepository(new Mineracao.RepoId(owner, name), out);
    }

    // Tenta obter o token do ambiente; se não houver, tenta ler de um arquivo .env (buscando pais).
    private static String resolveToken() {
        String envTok = System.getenv("GITHUB_TOKEN");
        if (envTok != null && !envTok.isBlank()) return envTok.trim();
        for (Path p : candidateEnvPaths()) {
            if (Files.exists(p)) {
                String t = readTokenFromEnvFile(p);
                if (t != null && !t.isBlank()) return t.trim();
            }
        }
        return null;
    }

    private static String readTokenFromEnvFile(Path envPath) {
        try {
            for (String line : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                int eq = s.indexOf('=');
                if (eq < 0) continue;
                String key = s.substring(0, eq).trim();
                String value = s.substring(eq + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (key.equals("GITHUB_TOKEN")) return value;
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static class EnvVars { String owner; String repo; String outDir; }
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
                        case "OUT_DIR": vars.outDir = value; break;
                    }
                }
            } catch (IOException ignored) {}
        }
        return vars;
    }

    private static List<Path> candidateEnvPaths() {
        List<Path> list = new ArrayList<>();
        Path cwd = Paths.get("").toAbsolutePath();
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
        } catch (IOException ignored) {}
        return null;
    }
}
