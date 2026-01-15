package dev.dukedarius.HytaleConsole;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class LogUIPage extends InteractiveCustomUIPage<LogUIPage.LogUIEventData> {
    private static final String LAYOUT = "Pages/HytaleConsole_Logs.ui";
    private static final String SEL_LOG_BOX = "#LogBox";
    private static final String ROW_UI = "Pages/HytaleConsole_LogRow.ui";
    private static final String SEL_MIN_LEVEL_FILTER_INPUT = "#MinLevelFilter";
    private static final String SEL_LOGGER_FILTER_INPUT = "#LoggerFilter";
    private static final String SEL_TEXT_FILTER_INPUT = "#LogTextFilter";
    private final LogUIManager manager;
    @Nonnull
    private String loggerFilter = LogUIManager.FILTER_ALL;
    @Nonnull
    private String minLevelFilter = LogUIManager.FILTER_ALL;
    @Nonnull
    private String textFilter = "";

    public LogUIPage(@Nonnull PlayerRef playerRef, @Nonnull LogUIManager manager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, LogUIEventData.CODEC);
        this.manager = manager;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append(LAYOUT);
        commandBuilder.clear(SEL_LOG_BOX);
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                SEL_MIN_LEVEL_FILTER_INPUT,
                EventData.of(LogUIEventData.KEY_MIN_LEVEL_FILTER, SEL_MIN_LEVEL_FILTER_INPUT + ".Value"),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                SEL_LOGGER_FILTER_INPUT,
                EventData.of(LogUIEventData.KEY_LOGGER_FILTER, SEL_LOGGER_FILTER_INPUT + ".Value"),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                SEL_TEXT_FILTER_INPUT,
                EventData.of(LogUIEventData.KEY_TEXT_FILTER, SEL_TEXT_FILTER_INPUT + ".Value"),
                false
        );
    }

    public void updateRows(@Nonnull java.util.List<LogRow> rows) {
        UICommandBuilder b = new UICommandBuilder();
        b.clear(SEL_LOG_BOX);
        for (int i = 0; i < rows.size(); i++) {
            LogRow row = rows.get(i);
            b.append(SEL_LOG_BOX, ROW_UI);
            String selector = SEL_LOG_BOX + "[" + i + "] ";
            b.set(selector + "#RowPrefix.Text", row.prefix);
            b.set(selector + "#RowLogger.Text", row.logger);
            b.set(selector + "#RowSuffix.Text", row.suffix);
            b.set(selector + "#RowPrefix.Style.TextColor", row.colorHex);
            b.set(selector + "#RowSuffix.Style.TextColor", row.colorHex);
        }
        this.sendUpdate(b, null, false);
    }
    public void updateFilterOptions(
            @Nonnull List<DropdownEntryInfo> loggerEntries,
            @Nonnull List<DropdownEntryInfo> minLevelEntries,
            @Nullable String selectedLogger,
            @Nullable String selectedMinLevel,
            @Nullable String textFilter
    ) {
        UICommandBuilder b = new UICommandBuilder();
        b.set(SEL_LOGGER_FILTER_INPUT + ".Entries", loggerEntries);
        b.set(SEL_MIN_LEVEL_FILTER_INPUT + ".Entries", minLevelEntries);
        String loggerValue = selectedLogger != null ? selectedLogger : LogUIManager.FILTER_ALL;
        String minLevelValue = selectedMinLevel != null ? selectedMinLevel : LogUIManager.FILTER_ALL;
        b.set(SEL_LOGGER_FILTER_INPUT + ".Value", loggerValue);
        b.set(SEL_MIN_LEVEL_FILTER_INPUT + ".Value", minLevelValue);
        if (textFilter != null) {
            b.set(SEL_TEXT_FILTER_INPUT + ".Value", textFilter);
        }
        this.sendUpdate(b, null, false);
    }

    @Nonnull
    public String getLoggerFilter() {
        return loggerFilter;
    }

    public void setLoggerFilter(@Nonnull String loggerFilter) {
        this.loggerFilter = loggerFilter;
    }

    @Nonnull
    public String getTextFilter() {
        return textFilter;
    }
    @Nonnull
    public String getMinLevelFilter() {
        return minLevelFilter;
    }

    public void setMinLevelFilter(@Nonnull String minLevelFilter) {
        this.minLevelFilter = minLevelFilter;
    }

    public void setTextFilter(@Nonnull String textFilter) {
        this.textFilter = textFilter;
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull LogUIEventData data) {
        if (data.loggerFilter != null) {
            manager.onFilterChanged(this, data.loggerFilter);
        }
        if (data.minLevelFilter != null) {
            manager.onMinLevelFilterChanged(this, data.minLevelFilter);
        }
        if (data.textFilter != null) {
            manager.onTextFilterChanged(this, data.textFilter);
        }
    }


    static final class LogUIEventData {
        static final String KEY_LOGGER_FILTER = "@LoggerFilter";
        static final String KEY_MIN_LEVEL_FILTER = "@MinLevelFilter";
        static final String KEY_TEXT_FILTER = "@TextFilter";
        static final BuilderCodec<LogUIEventData> CODEC = BuilderCodec.builder(LogUIEventData.class, LogUIEventData::new)
                .addField(new KeyedCodec<>(KEY_LOGGER_FILTER, BuilderCodec.STRING), (o, i) -> o.loggerFilter = i, o -> o.loggerFilter)
                .addField(new KeyedCodec<>(KEY_MIN_LEVEL_FILTER, BuilderCodec.STRING), (o, i) -> o.minLevelFilter = i, o -> o.minLevelFilter)
                .addField(new KeyedCodec<>(KEY_TEXT_FILTER, BuilderCodec.STRING), (o, i) -> o.textFilter = i, o -> o.textFilter)
                .build();
        private String loggerFilter;
        private String minLevelFilter;
        private String textFilter;
        private LogUIEventData() {}
    }
}
