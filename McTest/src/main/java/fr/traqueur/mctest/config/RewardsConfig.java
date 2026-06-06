package fr.traqueur.mctest.config;

import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.defaults.DefaultDouble;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.annotations.defaults.DefaultString;
import fr.traqueur.structura.api.Loadable;

import java.util.List;

/**
 * Rewards configuration — rewards.yml
 *
 * Each reward is polymorphic: the "type" key inside each list entry
 * determines which concrete record is instantiated.
 *
 * Example rewards.yml:
 *
 * daily:
 *   - type: item
 *     material: DIAMOND
 *     amount: 1
 *   - type: money
 *     amount: 500.0
 * weekly:
 *   - type: item
 *     material: NETHERITE_INGOT
 *     amount: 1
 *   - type: command
 *     command: give %player% golden_apple 5
 *     message: Enjoy your apples!
 */
public record RewardsConfig(
    List<Reward> daily,
    List<Reward> weekly
) implements Loadable {

    @Polymorphic(key = "type")
    public interface Reward extends Loadable {}

    public record ItemReward(
        @DefaultString("DIAMOND") String material,
        @DefaultInt(1)            int    amount
    ) implements Reward {}

    public record MoneyReward(
        @DefaultDouble(100.0) double amount
    ) implements Reward {}

    public record CommandReward(
        @DefaultString("say Hello %player%!") String command,
        @DefaultString("")                    String message
    ) implements Reward {}
}
