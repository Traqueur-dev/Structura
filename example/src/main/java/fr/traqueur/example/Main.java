package fr.traqueur.example;

import fr.traqueur.example.config.AppConfig;
import fr.traqueur.example.config.StorageConfig;
import fr.traqueur.example.config.StorageConfig.LocalBackend;
import fr.traqueur.example.config.StorageConfig.S3Backend;
import fr.traqueur.example.config.StorageConfig.StorageBackend;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.registries.PolymorphicRegistry;

import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    private static final Path CONFIG_DIR = Path.of("example-configs");

    public static void main(String[] args) throws Exception {

        // ── 1. Setup polymorphic registries ───────────────────────────────────
        PolymorphicRegistry.create(StorageBackend.class, r -> {
            r.register("local", LocalBackend.class);
            r.register("s3",    S3Backend.class);
        });

        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║     Structura — Java pure example    ║");
        System.out.println("╚══════════════════════════════════════╝\n");

        // ── 2. saveDefault — generate files from @Default* annotations ────────
        section("Step 1 — saveDefault()");

        Path appFile     = CONFIG_DIR.resolve("app.yml");
        Path storageFile = CONFIG_DIR.resolve("storage.yml");

        if (!Files.exists(appFile)) {
            Structura.saveDefault(appFile, AppConfig.class);
            System.out.println("Generated: " + appFile);
        } else {
            System.out.println("Already exists: " + appFile);
        }

        // StorageConfig has a polymorphic field — must use write() with an explicit default
        if (!Files.exists(storageFile)) {
            StorageConfig defaultStorage = new StorageConfig(new LocalBackend("./data", 100));
            Structura.write(storageFile, defaultStorage);
            System.out.println("Generated: " + storageFile);
        } else {
            System.out.println("Already exists: " + storageFile);
        }

        System.out.println("\nContents of app.yml:");
        System.out.println(Files.readString(appFile).indent(2).stripTrailing());

        System.out.println("\nContents of storage.yml:");
        System.out.println(Files.readString(storageFile).indent(2).stripTrailing());

        // ── 3. load — read config from disk ──────────────────────────────────
        section("Step 2 — Structura.load()");

        AppConfig     appConfig     = Structura.load(appFile,     AppConfig.class);
        StorageConfig storageConfig = Structura.load(storageFile, StorageConfig.class);

        System.out.println("app-name        : " + appConfig.appName());
        System.out.println("version         : " + appConfig.version());
        System.out.println("debug           : " + appConfig.debug());
        System.out.println("max-connections : " + appConfig.maxConnections());
        System.out.println("admin-email     : " + (appConfig.adminEmail() != null
                ? appConfig.adminEmail() : "(not set — optional field)"));

        System.out.println();
        StorageBackend backend = storageConfig.backend();
        if (backend instanceof LocalBackend(String path, int maxFileSizeMb)) {
            System.out.println("storage type    : local");
            System.out.println("path            : " + path);
            System.out.println("max-file-size   : " + maxFileSizeMb + " MB");
        } else if (backend instanceof S3Backend s3) {
            System.out.println("storage type    : s3");
            System.out.println("bucket          : " + s3.bucket());
            System.out.println("region          : " + s3.region());
            System.out.println("use-ssl         : " + s3.useSsl());
        }

        // ── 4. modify in memory + write() ────────────────────────────────────
        section("Step 3 — modify + Structura.write()");

        AppConfig updated = new AppConfig(
            appConfig.appName(),
            appConfig.version(),
            true,                       // enable debug
            appConfig.maxConnections(),
            "admin@example.com"         // set optional field
        );
        Structura.write(appFile, updated);
        System.out.println("Saved updated config (debug=true, admin-email set).");

        System.out.println("\nNew contents of app.yml:");
        System.out.println(Files.readString(appFile).indent(2).stripTrailing());

        // ── 5. reload from disk — verify round-trip ───────────────────────────
        section("Step 4 — reload and verify round-trip");

        AppConfig reloaded = Structura.load(appFile, AppConfig.class);
        System.out.println("debug after reload  : " + reloaded.debug());
        System.out.println("admin-email         : " + reloaded.adminEmail());
        System.out.println();

        if (reloaded.debug() && "admin@example.com".equals(reloaded.adminEmail())) {
            System.out.println("✓ Round-trip OK — write() then load() produces the same values.");
        } else {
            System.out.println("✗ Round-trip FAILED.");
        }

        // ── 6. switch storage backend + write ────────────────────────────────
        section("Step 5 — switch storage backend to S3");

        StorageConfig s3Config = new StorageConfig(
            new S3Backend("prod-bucket", "us-east-1", "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI", true)
        );
        Structura.write(storageFile, s3Config);
        System.out.println("Switched to S3. New contents of storage.yml:");
        System.out.println(Files.readString(storageFile).indent(2).stripTrailing());

        StorageConfig reloadedStorage = Structura.load(storageFile, StorageConfig.class);
        System.out.println("Reloaded backend type : "
                + (reloadedStorage.backend() instanceof S3Backend ? "S3 ✓" : "unexpected type ✗"));

        System.out.println("\nDone. Config files are in: " + CONFIG_DIR.toAbsolutePath());
    }

    private static void section(String title) {
        System.out.println("\n── " + title + " " + "─".repeat(Math.max(0, 40 - title.length())));
    }
}
