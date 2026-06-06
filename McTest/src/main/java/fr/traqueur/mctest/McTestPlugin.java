package fr.traqueur.mctest;

import fr.traqueur.mctest.config.DatabaseConfig;
import fr.traqueur.mctest.config.DatabaseConfig.DatabaseDriver;
import fr.traqueur.mctest.config.DatabaseConfig.MySQLDriver;
import fr.traqueur.mctest.config.DatabaseConfig.SQLiteDriver;
import fr.traqueur.mctest.config.MainConfig;
import fr.traqueur.mctest.config.RewardsConfig;
import fr.traqueur.mctest.config.RewardsConfig.CommandReward;
import fr.traqueur.mctest.config.RewardsConfig.ItemReward;
import fr.traqueur.mctest.config.RewardsConfig.MoneyReward;
import fr.traqueur.mctest.config.RewardsConfig.Reward;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import fr.traqueur.structura.writers.StructuraWriters;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class McTestPlugin extends JavaPlugin {

    private MainConfig    mainConfig;
    private DatabaseConfig databaseConfig;
    private RewardsConfig  rewardsConfig;

    @Override
    public void onEnable() {
        setupRegistries();
        loadConfigs();
        getLogger().info("McTest enabled — plugin name: " + mainConfig.pluginName());
    }

    @Override
    public void onDisable() {
        getLogger().info("McTest disabled.");
    }

    // -------------------------------------------------------------------------
    // Config
    // -------------------------------------------------------------------------

    private void setupRegistries() {
        // Register all polymorphic types before loading any config that uses them
        PolymorphicRegistry.create(DatabaseDriver.class, r -> {
            r.register("mysql",  MySQLDriver.class);
            r.register("sqlite", SQLiteDriver.class);
        });

        PolymorphicRegistry.create(Reward.class, r -> {
            r.register("item",    ItemReward.class);
            r.register("money",   MoneyReward.class);
            r.register("command", CommandReward.class);
        });
    }

    private void loadConfigs() {
        Path folder = getDataFolder().toPath();

        mainConfig     = loadOrDefault(folder.resolve("config.yml"),   MainConfig.class);
        databaseConfig = loadOrDefault(folder.resolve("database.yml"), DatabaseConfig.class);
        rewardsConfig  = loadOrDefault(folder.resolve("rewards.yml"),  RewardsConfig.class);
    }

    /**
     * Loads a config file if it exists, otherwise generates it from @Default* annotations.
     * This is the standard Structura pattern for plugin startup.
     */
    private <T extends fr.traqueur.structura.api.Loadable> T loadOrDefault(Path file, Class<T> type) {
        if (!Files.exists(file)) {
            getLogger().info("Generating default " + file.getFileName() + " ...");
            StructuraWriters.saveDefault(file, type);
        }
        return Structura.load(file, type);
    }

    // -------------------------------------------------------------------------
    // Command: /mctest <reload|save|info>
    // -------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /mctest <reload|save|info>");
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                loadConfigs();
                sender.sendMessage("[McTest] Configs reloaded from disk.");
            }

            case "save" -> {
                // Demonstrate Structura.write() — persists the current in-memory config to disk
                Path folder = getDataFolder().toPath();
                StructuraWriters.write(folder.resolve("config.yml"),   mainConfig);
                StructuraWriters.write(folder.resolve("database.yml"), databaseConfig);
                StructuraWriters.write(folder.resolve("rewards.yml"),  rewardsConfig);
                sender.sendMessage("[McTest] Configs saved to disk.");
            }

            case "info" -> {
                sender.sendMessage("=== McTest Info ===");
                sender.sendMessage("Plugin name    : " + mainConfig.pluginName());
                sender.sendMessage("Language       : " + mainConfig.language());
                sender.sendMessage("Debug          : " + mainConfig.debug());
                sender.sendMessage("Join radius    : " + mainConfig.joinMessageRadius());
                sender.sendMessage("Discord webhook: " + (mainConfig.discordWebhook() != null
                        ? mainConfig.discordWebhook() : "(not set)"));

                sender.sendMessage("--- Database ---");
                DatabaseDriver driver = databaseConfig.driver();
                if (driver instanceof MySQLDriver mysql) {
                    sender.sendMessage("Driver  : MySQL");
                    sender.sendMessage("Host    : " + mysql.host() + ":" + mysql.port());
                    sender.sendMessage("DB name : " + mysql.database());
                    sender.sendMessage("Pool    : " + mysql.poolSize());
                } else if (driver instanceof SQLiteDriver sqlite) {
                    sender.sendMessage("Driver  : SQLite");
                    sender.sendMessage("File    : " + sqlite.file());
                }

                sender.sendMessage("--- Daily rewards ---");
                printRewards(sender, rewardsConfig.daily());
                sender.sendMessage("--- Weekly rewards ---");
                printRewards(sender, rewardsConfig.weekly());
            }

            default -> sender.sendMessage("Unknown subcommand. Usage: /mctest <reload|save|info>");
        }

        return true;
    }

    private void printRewards(CommandSender sender, List<Reward> rewards) {
        if (rewards == null || rewards.isEmpty()) {
            sender.sendMessage("  (none)");
            return;
        }
        for (Reward reward : rewards) {
            String desc = switch (reward) {
                case ItemReward    r -> "item "    + r.amount() + "x " + r.material();
                case MoneyReward   r -> "money "   + r.amount();
                case CommandReward r -> "command " + r.command();
                default              -> reward.toString();
            };
            sender.sendMessage("  - " + desc);
        }
    }
}
