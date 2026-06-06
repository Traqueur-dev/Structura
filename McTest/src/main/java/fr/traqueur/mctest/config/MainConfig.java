package fr.traqueur.mctest.config;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.defaults.DefaultBool;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.annotations.defaults.DefaultString;
import fr.traqueur.structura.api.Loadable;

/**
 * Main plugin configuration — config.yml
 *
 * Example YAML produced by Structura.saveDefault():
 *
 * plugin-name: McTest
 * language: en
 * debug: false
 * join-message-radius: 20
 * # discord-webhook is optional — omitted when null
 */
public record MainConfig(
    @DefaultString("McTest")  String  pluginName,
    @DefaultString("en")      String  language,
    @DefaultBool(false)       boolean debug,
    @DefaultInt(20)           int     joinMessageRadius,
    @Options(optional = true) String  discordWebhook
) implements Loadable {}
