package zone.moddev.mc.electricadvantage;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world server settings (serverconfig/electricadvantage-server.toml). */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue PUMP_INFINITE_THRESHOLD = BUILDER
            .comment("Connected source blocks at which a fluid tagged electricadvantage:pump_infinite counts as",
                    "naturally infinite: the electric pump then takes fluid without removing source blocks. 0 disables this.")
            .defineInRange("pump.infiniteSourceThreshold", 10000, 0, 1_000_000);
    public static final ModConfigSpec.ConfigValue<String> TURRET_LASER_SOUND = BUILDER
            .comment("Sound event played when a laser turret fires (EA2 option laser_sound). Unknown IDs fall back to the enderman teleport sound.")
            .define("turret.laserSound", "minecraft:block.note_block.bass");
    public static final ModConfigSpec SPEC = BUILDER.build();
    private Config() {}

    public static int pumpInfiniteThreshold() {
        return SPEC.isLoaded() ? PUMP_INFINITE_THRESHOLD.get() : PUMP_INFINITE_THRESHOLD.getDefault();
    }
    public static String turretLaserSound() {
        return SPEC.isLoaded() ? TURRET_LASER_SOUND.get() : TURRET_LASER_SOUND.getDefault();
    }
}
