package dev.dukedarius.HytaleConsole;

import javax.annotation.Nonnull;

public final class LogRow {
    public final String prefix;
    public final String logger;
    public final String loggerName;
    public final int levelValue;
    public final String suffix;
    public final String colorHex;

    public LogRow(@Nonnull String prefix, @Nonnull String logger, @Nonnull String loggerName, int levelValue, @Nonnull String suffix, @Nonnull String colorHex) {
        this.prefix = prefix;
        this.logger = logger;
        this.loggerName = loggerName;
        this.levelValue = levelValue;
        this.suffix = suffix;
        this.colorHex = colorHex;
    }
}
