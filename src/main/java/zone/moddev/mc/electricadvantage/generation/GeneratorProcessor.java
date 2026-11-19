package zone.moddev.mc.electricadvantage.generation;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.capabilities.Capabilities;
import zone.moddev.mc.lib.api.redstone.IRedstoneOutput;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.api.redstone.RedstoneOutput;
import zone.moddev.mc.lib.machine.feature.Processor.ProcessState;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;
import zone.moddev.mc.lib.machine.inventory.MachineTx;

/**
 * Shared work of the photovoltaic, hydroelectric and steam-powered generators: each tick, add what the generator makes
 * to its buffer (keeping fractions of an FE for the next tick), then push up to {@link #RATE} FE into neighbours,
 * conduits included. The comparator ({@code generator/output}) reads {@link #comparatorLevel}.
 */
public abstract class GeneratorProcessor extends StoredProcessor implements IRedstoneOutput {
    /** FE pushed into neighbours per tick, and the steam generator's conversion limit. */
    public static final int RATE = 4096;

    private final ProcessorState.Key<Double> fraction = variables.define("fraction", Codec.DOUBLE, 0.0,
            UnaryOperator.identity(), false, false);
    private final ProcessorState.Key<Double> output = variables.define("output", Codec.DOUBLE, 0.0,
            UnaryOperator.identity(), false, false);

    /**
     * FE made this tick, possibly fractional, at most {@code space}. Side effects (draining steam) go through
     * {@code tx}, which is committed together with the energy.
     */
    protected abstract double generate(ProcessorMachine host, long space, MachineTx tx);

    /** FE made on the last tick this generator ran. */
    public double lastOutput() { return variables.get(output); }

    /** The comparator signal: by default, how full the buffer is. */
    protected int comparatorLevel() {
        var energy = machine.resources().energy("energy");
        return RedstoneControl.level(energy.stored() / (double) Math.max(1, energy.capacity()));
    }

    @Override
    public List<RedstoneOutput> redstoneOutputs() {
        return List.of(new RedstoneOutput("generator/output", Component.translatable("screen.electricadvantage.generator.output"),
                Component.translatable("screen.electricadvantage.generator.output.description"), 0xD85B5B, Optional.empty(),
                () -> machine == null || executionStatus() == ProcessState.HELD ? 0 : comparatorLevel()));
    }

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) { return true; }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host, int ticks) {
        var resources = host.resources();
        var energy = resources.energy("energy");
        double made;
        try (var tx = resources.begin()) {
            long space = energy.capacity() - energy.stored();
            made = generate(host, space, tx);
            double total = Math.min(space, made + state.get(fraction));
            long whole = (long) total;
            energy.insert(whole, tx);
            tx.commit();
            state.set(fraction, Math.clamp(total - whole, 0, 0.999999999));
        }
        state.set(output, made);
        pushToNeighbours(host);
        return made > 0 ? ProcessState.RUNNING : ProcessState.IDLE;
    }

    /** Offers the buffer to each neighbour in turn, starting from a different side each tick. */
    private static void pushToNeighbours(ProcessorMachine host) {
        var level = host.level();
        var pos = host.position();
        var energy = host.resources().energy("energy");
        long remaining = RATE;
        for (int offset = 0; offset < 6 && remaining > 0 && energy.stored() > 0; offset++) {
            var side = Direction.values()[(int) ((level.getGameTime() + offset) % 6)];
            var next = pos.relative(side);
            if (!level.hasChunkAt(next)) continue;
            var target = level.getCapability(Capabilities.EnergyStorage.BLOCK, next, side.getOpposite());
            if (target == null || !target.canReceive()) continue;
            try (var tx = host.resources().begin()) {
                int offered = (int) energy.extract(Math.min(remaining, Integer.MAX_VALUE), tx);
                int accepted = Math.clamp(target.receiveEnergy(offered, false), 0, offered);
                if (accepted < offered) energy.insert(offered - accepted, tx);
                tx.commit();
                remaining -= accepted;
            }
        }
    }
}
