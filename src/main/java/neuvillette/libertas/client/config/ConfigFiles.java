package neuvillette.libertas.client.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Files visible to the client config browser, rooted at the instance's config directory. */
public final class ConfigFiles {

    private final Path root;

    public ConfigFiles(Path root) throws IOException {
        this.root = root.toRealPath();
    }

    public Path getRoot() {
        return root;
    }

    public Path resolve(String path) throws IOException {
        String relative = path.replace('\\', '/');
        if (relative.equals("config")) relative = "";
        else if (relative.startsWith("config/")) relative = relative.substring(7);
        return check(root.resolve(relative));
    }

    public Path check(Path path) throws IOException {
        Path real = path.toRealPath();
        if (!real.startsWith(root)) throw new IOException("outsideRoot");
        return real;
    }

    public String display(Path path) {
        String relative = root.relativize(path)
            .toString()
            .replace('\\', '/');
        return relative.isEmpty() ? "config/" : "config/" + relative;
    }

    public List<Path> list(Path directory) throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> children = Files.list(check(directory))) {
            children.forEach(path -> {
                try {
                    check(path);
                    result.add(path);
                } catch (IOException ignored) {
                    // Broken links and links outside config are not navigable.
                }
            });
        }
        result.sort(
            Comparator.comparing((Path path) -> !Files.isDirectory(path))
                .thenComparing(
                    path -> path.getFileName()
                        .toString(),
                    String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public static boolean isSupported(Path path) {
        String name = path.getFileName()
            .toString()
            .toLowerCase(Locale.ROOT);
        return name.endsWith(".cfg") || name.endsWith(".json") || name.endsWith(".properties") || name.endsWith(".ini");
    }
}
