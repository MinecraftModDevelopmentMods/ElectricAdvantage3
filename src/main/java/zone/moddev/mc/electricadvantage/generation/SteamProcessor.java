package zone.moddev.mc.electricadvantage.generation;

import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.inventory.MachineTx;

/**
 * Steam-powered generator: turns Power Advantage steam into FE, {@link #STEAM_PER_FE} mB per FE (EA2's 32 steam per
 * electricity at 0.25 electricity per FE), up to {@link GeneratorProcessor#RATE} FE per tick. Steam short of a whole FE
 * stays in the tank, and a full buffer uses none.
 */
public final class SteamProcessor extends GeneratorProcessor {
    public static final int STEAM_PER_FE = 8;

    @Override
    protected double generate(ProcessorMachine host, long space, MachineTx tx) {
        var steam = host.resources().fluids("steam");
        long fe = Math.min(Math.min(steam.free(0) / STEAM_PER_FE, RATE), space);
        if (fe <= 0) return 0;
        steam.extract(0, (int) fe * STEAM_PER_FE, tx);
        return fe;
    }
}
