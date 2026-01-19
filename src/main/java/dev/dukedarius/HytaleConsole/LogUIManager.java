package dev.dukedarius.HytaleConsole;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.backend.HytaleConsole;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.task.TaskRegistry;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.netty.handler.logging.LogLevel;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogRecord;

public class LogUIManager {
    private static final int MAX_LINES = 5000;
    public static final String FILTER_ALL = "ALL";
    public static final String LEVEL_SEVERE = "SEVERE";
    public static final String LEVEL_WARNING = "WARNING";
    public static final String LEVEL_INFO = "INFO";
    public static final String LEVEL_CONFIG = "CONFIG";
    public static final String LEVEL_FINE = "FINE";
    public static final String LEVEL_FINER = "FINER";
    public static final String LEVEL_FINEST = "FINEST";
    public static final String LEVEL_OFF = "OFF";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final CopyOnWriteArrayList<LogRecord> logQueue;
    private final CopyOnWriteArrayList<LogUIPage> pages = new CopyOnWriteArrayList<>();
    private final ArrayDeque<LogRow> rows = new ArrayDeque<>();
    private final Set<String> loggerNames = new ConcurrentSkipListSet<>();
    private ScheduledFuture<Void> refreshTask;

    public LogUIManager(@Nonnull CopyOnWriteArrayList<LogRecord> logQueue) {
        this.logQueue = logQueue;
    }
    public void onMinLevelFilterChanged(@Nonnull LogUIPage page, @Nonnull String filter) {
        String nextFilter = resolveMinLevelFilter(filter);
        page.setMinLevelFilter(nextFilter);
        page.updateFilterOptions(getLoggerFilterEntries(), getMinLevelEntries(), page.getLoggerFilter(), nextFilter, page.getTextFilter());
        page.updateRows(getRowSnapshot(page.getLoggerFilter(), nextFilter, page.getTextFilter()));
    }

    private String resolveMinLevelFilter(@Nonnull String filter) {
        return switch (filter) {
            case LEVEL_SEVERE, LEVEL_WARNING, LEVEL_INFO, LEVEL_CONFIG, LEVEL_FINE, LEVEL_FINER, LEVEL_FINEST, LEVEL_OFF -> filter;
            default -> FILTER_ALL;
        };
    }
    private int resolveMinLevelValue(@Nonnull String filter) {
        return switch (filter) {
            case LEVEL_SEVERE -> 1000;
            case LEVEL_WARNING -> 900;
            case LEVEL_INFO -> 800;
            case LEVEL_CONFIG -> 700;
            case LEVEL_FINE -> 500;
            case LEVEL_FINER -> 400;
            case LEVEL_FINEST -> 300;
            case LEVEL_OFF -> Integer.MAX_VALUE;
            default -> Integer.MIN_VALUE;
        };
    }

    public void onTextFilterChanged(@Nonnull LogUIPage page, @Nonnull String textFilter) {
        page.setTextFilter(textFilter);
        page.updateFilterOptions(getLoggerFilterEntries(), getMinLevelEntries(), page.getLoggerFilter(), page.getMinLevelFilter(), textFilter);
        page.updateRows(getRowSnapshot(page.getLoggerFilter(), page.getMinLevelFilter(), textFilter));
    }

    private String levelColor(@Nonnull LogRecord r) {
        int v = r.getLevel().intValue();
        if (v >= 1000) { // SEVERE
            return "#ff5555";
        } else if (v >= 900) { // WARNING
            return "#ffb86c";
        } else if (v >= 800) { // INFO
            return "#e6e6e6";
        } else if (v >= 700) { // CONFIG
            return "#9aa5b1";
        } else {
            return "#8be9fd";
        }
    }

    private String wrap(@Nonnull String s, int max) {
        if (s.length() <= max) return s;
        StringBuilder sb = new StringBuilder(s.length() + 8);
        int i = 0;
        while (i < s.length()) {
            int end = Math.min(i + max, s.length());
            sb.append(s, i, end);
            if (end < s.length()) sb.append('\n');
            i = end;
        }
        return sb.toString();
    }


    public void start(@Nonnull TaskRegistry taskRegistry) {
        if (refreshTask != null) {
            return;
        }
        ScheduledFuture<Void> future = (ScheduledFuture<Void>)(ScheduledFuture<?>) HytaleServer.SCHEDULED_EXECUTOR
                .scheduleWithFixedDelay(this::refresh, 250L, 250L, TimeUnit.MILLISECONDS);
        this.refreshTask = future;
        taskRegistry.registerTask(future);
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        pages.clear();
    }

    public void openFor(@Nonnull PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            if (!ref.isValid()) {
                return;
            }
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            if (playerComponent == null) {
                return;
            }
            LogUIPage page = new LogUIPage(playerRef, this);
            PageManager pageManager = playerComponent.getPageManager();
            pageManager.openCustomPage(ref, store, page);
            pages.addIfAbsent(page);
        });
    }

    private void refresh() {
        if (pages.isEmpty()) {
            return;
        }

        List<LogRecord> batch = drainLogs();
        if (!batch.isEmpty()) {
            for (LogRecord r : batch) {
                appendRow(formatRow(r));
            }
            for (LogUIPage page : pages) {
                page.updateFilterOptions(getLoggerFilterEntries(), getMinLevelEntries(), page.getLoggerFilter(), page.getMinLevelFilter(), page.getTextFilter());
                page.updateRows(getRowSnapshot(page.getLoggerFilter(), page.getMinLevelFilter(), page.getTextFilter()));
            }
        }
    }

    private List<LogRecord> drainLogs() {
        if (logQueue.isEmpty()) {
            return List.of();
        }
        List<LogRecord> batch = new ArrayList<>(logQueue);
        logQueue.clear();
        return batch;
    }

    private void appendRow(@Nonnull LogRow row) {
        rows.addLast(row);
        loggerNames.add(row.loggerName);
        while (rows.size() > MAX_LINES) {
            rows.removeFirst();
        }
    }
    List<LogRow> getRowSnapshot(@Nonnull String loggerFilter, @Nonnull String minLevelFilter, @Nonnull String textFilter) {
        Object[] arr = rows.toArray();
        List<LogRow> list = new ArrayList<>(arr.length);
        String text = textFilter.trim().toLowerCase();
        int minLevelValue = resolveMinLevelValue(minLevelFilter);
        for (int i = arr.length - 1; i >= 0; i--) {
            LogRow row = (LogRow) arr[i];
            if (!FILTER_ALL.equals(loggerFilter) && !row.loggerName.equals(loggerFilter)) {
                continue;
            }
            if (minLevelValue != Integer.MIN_VALUE && row.levelValue < minLevelValue) {
                continue;
            }
            if (!text.isEmpty()) {
                String haystack = (row.loggerName + " " + row.prefix + " " + row.suffix).toLowerCase();
                if (!haystack.contains(text)) {
                    continue;
                }
            }
            list.add(row);
        }
        return list;
    }
    private List<DropdownEntryInfo> getLoggerFilterEntries() {
        List<DropdownEntryInfo> entries = new ArrayList<>(loggerNames.size() + 1);
        entries.add(new DropdownEntryInfo(LocalizableString.fromString(FILTER_ALL), FILTER_ALL));
        for (String name : loggerNames) {
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(name), name));
        }
        return entries;
    }

    List<DropdownEntryInfo> getLoggerFilterEntriesSnapshot() {
        return getLoggerFilterEntries();
    }

    private List<DropdownEntryInfo> getMinLevelEntries() {
        List<DropdownEntryInfo> entries = new ArrayList<>(5);

        entries.add(new DropdownEntryInfo(LocalizableString.fromString(FILTER_ALL), FILTER_ALL));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString(LEVEL_SEVERE), LEVEL_SEVERE));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString(LEVEL_WARNING), LEVEL_WARNING));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString(LEVEL_INFO), LEVEL_INFO));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString(LEVEL_CONFIG), LEVEL_CONFIG));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString(LEVEL_FINE), LEVEL_FINE));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString(LEVEL_FINER), LEVEL_FINER));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString(LEVEL_FINEST), LEVEL_FINEST));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString(LEVEL_OFF), LEVEL_OFF));
        return entries;
    }

    List<DropdownEntryInfo> getMinLevelEntriesSnapshot() {
        return getMinLevelEntries();
    }

    public void onFilterChanged(@Nonnull LogUIPage page, @Nonnull String filter) {
        String nextFilter = loggerNames.contains(filter) ? filter : FILTER_ALL;
        page.setLoggerFilter(nextFilter);
        page.updateFilterOptions(getLoggerFilterEntries(), getMinLevelEntries(), nextFilter, page.getMinLevelFilter(), page.getTextFilter());
        page.updateRows(getRowSnapshot(nextFilter, page.getMinLevelFilter(), page.getTextFilter()));
    }

    private LogRow formatRow(@Nonnull LogRecord r) {
        String time = TIME_FMT.format(Instant.ofEpochMilli(r.getMillis()));
        String logger = r.getLoggerName() != null ? r.getLoggerName() : "Log";
        String msg = r.getMessage() != null ? r.getMessage() : "";
        String level = r.getLevel().getName();
        int levelValue = r.getLevel().intValue();
        String prefix = "[" + time + "][" + level + "]";
        String loggerPart = "[" + logger + "]";
        String suffix = " " + msg;
        return new LogRow(prefix, loggerPart, logger, levelValue, suffix, levelColor(r));
    }

    public void pageDismissed(LogUIPage logUIPage) {
        pages.remove(logUIPage);
    }
}
