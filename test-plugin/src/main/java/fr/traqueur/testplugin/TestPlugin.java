package fr.traqueur.testplugin;

import fr.traqueur.config.Injector;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class TestPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        File test = new File(this.getDataFolder(), "test.yml");
        if (!test.exists()) {
            this.saveResource("test.yml", false);
        }
        File test2 = new File(this.getDataFolder(), "test2.yml");
        if(!test2.exists()) {
            this.saveResource("test2.yml", false);
        }

        Injector injector = new Injector();
        injector.injectEnum(test, Test1.class);
        injector.injectEnum(test2, Test2.class);

        for (Test1 value : Test1.values()) {
            System.out.println(value);
        }
        for (Test2 value : Test2.values()) {
            System.out.println(value);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
