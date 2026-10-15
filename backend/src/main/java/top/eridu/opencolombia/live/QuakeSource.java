package top.eridu.opencolombia.live;

import java.util.List;
import top.eridu.opencolombia.source.SourceException;

@FunctionalInterface
public interface QuakeSource {
    /** Sismos recientes, del más nuevo al más antiguo. */
    List<Quake> fetch() throws SourceException;
}
