import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class App {
    public static void main(String[] args) throws Exception {
        // Uso:
        //  - Argumento 1 (obrigatório): owner/repo (ex.: torvalds/linux)
        //  - Argumento 2 (opcional): diretório de saída (padrão: ./data)
        //  - Token via env (opcional): GITHUB_TOKEN

        Path out = Paths.get(args.length >= 1 ? args[0] : "data");
        String owner = "spring-projects";
        String name = "spring-boot";

        String token = resolveToken();
        Mineracao miner = new Mineracao(token);
        miner.mineRepository(new Mineracao.RepoId(owner, name), out);


    }

    // Tenta obter o token do ambiente; se não houver, tenta ler de um arquivo .env.
    private static String resolveToken() {
        String env = System.getenv("GITHUB_TOKEN");
        if (env != null && !env.isBlank()) return env.trim();

        // Candidatos de caminho para .env
        Path[] candidates = new Path[] {
            Paths.get(".env"),
            Paths.get("Code").resolve("Mineration").resolve(".env")
        };
        for (Path p : candidates) {
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
                // Remover aspas se houver
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (key.equals("GITHUB_TOKEN")) {
                    return value;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}
