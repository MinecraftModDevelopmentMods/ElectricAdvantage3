package zone.moddev.mc.electricadvantage.generation;

import net.minecraft.world.level.LightLayer;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.inventory.MachineTx;

/**
 * Photovoltaic generator: {@link #GENERATION} FE per tick in full sun with open sky above, less in rain and thunder,
 * nothing at night; under a roof, a fraction of the block light above. The comparator reads the output as a share of
 * full sun.
 */
public final class SolarProcessor extends GeneratorProcessor {
    public static final double GENERATION = 5.140418, BLOCK_LIGHT_FACTOR = 0.1, RAIN_FACTOR = 0.35, THUNDER_FACTOR = 0.2;

    @Override
    protected double generate(ProcessorMachine host, long space, MachineTx tx) {
        return GENERATION * intensity(host);
    }

    /** Share of full output, 0 to 1. */
    public static double intensity(ProcessorMachine host) {
        var level = host.level();
        var above = host.position().above();
        if (level.dimensionType().hasSkyLight() && level.canSeeSky(above)) {
            double sunlight = Math.max(0, Math.sin((level.getDayTime() % 24000) * Math.PI / 12000));
            return sunlight * (level.isThundering() ? THUNDER_FACTOR : level.isRaining() ? RAIN_FACTOR : 1);
        }
        return BLOCK_LIGHT_FACTOR * level.getBrightness(LightLayer.BLOCK, above) / 15.0;
    }

    @Override
    protected int comparatorLevel() {
        return (int) Math.clamp(15 * lastOutput() / GENERATION, 0, 15);
    }
}
