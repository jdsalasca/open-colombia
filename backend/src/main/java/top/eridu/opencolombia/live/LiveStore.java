package top.eridu.opencolombia.live;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Persiste el último {@link LiveSnapshot} en {@code live.json} (escritura atómica). */
@Component
public class LiveStore {
    private static final Logger log = LoggerFactory.getLogger(LiveStore.class);
    private final Path dir;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    public LiveStore(@Value("${opencolombia.data-dir:./data}") String dir) {
        this(Path.of(dir).toAbsolutePath().normalize());
    }

    public LiveStore(Path dir) {
        this.dir = dir;
    }

    public void put(LiveSnapshot s) {
        try {
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "live", ".tmp");
            mapper.writeValue(tmp.toFile(), s);
            Files.move(tmp, dir.resolve("live.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<LiveSnapshot> get() {
        Path f = dir.resolve("live.json");
        if (!Files.isRegularFile(f)) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(f.toFile(), LiveSnapshot.class));
        } catch (RuntimeException e) {
            log.warn("live.json ilegible: {}", f, e);
            return Optional.empty();
        }
    }
}
