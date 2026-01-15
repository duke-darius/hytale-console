package dev.dukedarius.HytaleConsole;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.backend.HytaleLoggerBackend;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.LogRecord;

public class HytaleConsolePlugin extends JavaPlugin {

    private CopyOnWriteArrayList<LogRecord> logs = new CopyOnWriteArrayList<>();
    private LogUIManager logUIManager;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public HytaleConsolePlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());

        HytaleLoggerBackend.subscribe(logs);
        this.logUIManager = new LogUIManager(logs);
        this.logUIManager.start(this.getTaskRegistry());
        this.getCommandRegistry().registerCommand(new OpenConsoleCommand(this.logUIManager));
    }

    @Override
    protected void shutdown() {
        if (this.logUIManager != null) {
            this.logUIManager.shutdown();
            this.logUIManager = null;
        }
        HytaleLoggerBackend.unsubscribe(logs);
    }
}