package fr.traqueur.mctest.config;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.defaults.DefaultBool;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.annotations.defaults.DefaultString;
import fr.traqueur.structura.api.Loadable;

/**
 * Database configuration — database.yml
 *
 * The driver is polymorphic with inline=true, meaning the "type" discriminator
 * appears at the root of the file rather than nested inside a "driver" block.
 *
 * MySQL example (database.yml):
 *   type: mysql
 *   driver:
 *     host: localhost
 *     port: 3306
 *     database: mctest
 *     username: root
 *     password: secret
 *     pool-size: 10
 *
 * SQLite example (database.yml):
 *   type: sqlite
 *   driver:
 *     file: plugins/McTest/data.db
 */
public record DatabaseConfig(
    DatabaseDriver driver
) implements Loadable {

    @Polymorphic(key = "type", inline = true)
    public interface DatabaseDriver extends Loadable {}

    public record MySQLDriver(
        @DefaultString("localhost") String  host,
        @DefaultInt(3306)           int     port,
        @DefaultString("mctest")    String  database,
        @DefaultString("root")      String  username,
        @DefaultString("")          String  password,
        @DefaultInt(10)             int     poolSize
    ) implements DatabaseDriver {}

    public record SQLiteDriver(
        @DefaultString("plugins/McTest/data.db") String file
    ) implements DatabaseDriver {}
}
