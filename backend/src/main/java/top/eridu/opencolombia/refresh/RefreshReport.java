package top.eridu.opencolombia.refresh;

import java.time.Instant;
import java.util.List;

public record RefreshReport(Instant at, List<SourceResult> results) {}
