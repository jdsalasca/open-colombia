package top.eridu.opencolombia.source;

import top.eridu.opencolombia.model.Series;

public interface SourceConnector {
    boolean supports(IndicatorSpec spec);

    Series fetch(IndicatorSpec spec) throws SourceException;
}
