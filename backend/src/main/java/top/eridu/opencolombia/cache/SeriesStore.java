package top.eridu.opencolombia.cache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import top.eridu.opencolombia.model.Series;

/** Caché de series en disco: un archivo JSON por serie, escritura atómica. */
@Component
public class SeriesStore {
    private static final Logger log = LoggerFactory.getLogger(SeriesStore.class);
    private final Path dir;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public SeriesStore(@Value("${opencolombia.data-dir:./data}") Path dir) {
        this.dir = dir;
    }

    public void put(Series s) {
        try {
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, s.id(), ".tmp");
            mapper.writeValue(tmp.toFile(), s);
            Files.move(tmp, dir.resolve(s.id() + ".json"), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<Series> get(String id) {
        Path f = dir.resolve(id + ".json");
        if (!Files.isRegularFile(f)) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(f.toFile(), Series.class));
        } catch (RuntimeException e) {
            log.warn("Archivo de caché ilegible: {}", f, e);
            return Optional.empty();
        }
    }

    public List<Series> all() {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            List<Series> out = new ArrayList<>();
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        get(name.substring(0, name.length() - 5)).ifPresent(out::add);
                    });
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
