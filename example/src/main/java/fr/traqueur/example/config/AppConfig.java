package fr.traqueur.example.config;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.defaults.DefaultBool;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.annotations.defaults.DefaultString;
import fr.traqueur.structura.api.Loadable;

/**
 * General application config — app.yml
 *
 * Generated file:
 *   app-name: MyApp
 *   version: 1.0.0
 *   debug: false
 *   max-connections: 50
 *   # admin-email omitted (optional, no default)
 */
public record AppConfig(
    @DefaultString("MyApp")  String  appName,
    @DefaultString("1.0.0")  String  version,
    @DefaultBool(false)      boolean debug,
    @DefaultInt(50)          int     maxConnections,
    @Options(optional = true) String adminEmail
) implements Loadable {}
