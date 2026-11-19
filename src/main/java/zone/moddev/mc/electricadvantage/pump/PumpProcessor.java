package zone.moddev.mc.electricadvantage.pump;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.material.Fluid;
import zone.moddev.mc.electricadvantage.Config;
import zone.moddev.mc.electricadvantage.infrastructure.PumpPipeBlock;
import zone.moddev.mc.lib.api.redstone.IRedstoneOutput;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.api.redstone.RedstoneOutput;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.feature.Processor.ProcessState;
import zone.moddev.mc.poweradvantage.util.FluidSearch;

/**
 * The Electric Pump's work: extends a pump pipe down through air, then takes one bucket per pump interval from the
 * source feeding its tip. A body of tagged fluid with enough connected sources is treated as naturally infinite and
 * keeps its source blocks. Fluid tracing is Power Advantage's {@link FluidSearch}.
 *
 * <p>FE costs are EA2's electricity costs at 4 FE per electricity: 400 per pipe segment, and 4000 plus 128 per block
 * of depth per bucket. The comparator reads the tank's fullness ({@code pump/tank}).
 */
public final class PumpProcessor extends StoredProcessor implements IRedstoneOutput {
    public enum Status {IDLE, EXTENDING, PUMPING_INFINITE, PUMPING, NO_FLUID, TANK_FULL, NO_POWER}

    public static final int PUMP_INTERVAL = 20, PIPE_INTERVAL = 11, PIPE_COST = 400, PUMP_COST = 4000, DEPTH_COST = 128,
            INFINITE_RECHECK = 1200, BUCKET = 1000;
    /** The tank's capacity in electric_pump.xml, for the comparator. */
    public static final int TANK_CAPACITY = 4000;
    /** Fluids that count as naturally infinite once a body reaches the configured size. */
    public static final TagKey<Fluid> INFINITE = TagKey.create(Registries.FLUID,
            ResourceLocation.fromNamespaceAndPath("electricadvantage", "pump_infinite"));
    private static final ResourceLocation PIPE = ResourceLocation.fromNamespaceAndPath("electricadvantage", "pump_pipe_electric");

    private final ProcessorState.Key<Integer> cooldown = variables.define("cooldown", Codec.INT, 0);
    private final ProcessorState.Key<Integer> depth = variables.define("depth", Codec.INT, 0,
            UnaryOperator.identity(), false, true);
    private final ProcessorState.Key<String> status = variables.define("pump_status", Codec.STRING, Status.IDLE.name(),
            UnaryOperator.identity(), false, true);
    private final ProcessorState.Key<List<Component>> details = variables.define("details",
            ComponentSerialization.CODEC.listOf(), List.of(), List::copyOf, false, true);

    // Cached infinite/finite verdict for the body at the pipe tip. Pumping only shrinks a body, so a finite verdict
    // stays valid too; both are rechecked after INFINITE_RECHECK ticks. Not saved: bodies change while unloaded.
    private BlockPos verdictTip;
    private Fluid verdictFluid;
    private int verdictThreshold;
    private boolean verdictInfinite;
    private long verdictUntil;

    public Status pumpStatus() {
        try { return Status.valueOf(variables.get(status)); } catch (IllegalArgumentException unknown) { return Status.IDLE; }
    }
    public int depth() { return variables.get(depth); }

    @Override
    public List<RedstoneOutput> redstoneOutputs() {
        return List.of(new RedstoneOutput("pump/tank", Component.translatable("screen.electricadvantage.pump.tank_output"),
                Component.translatable("screen.electricadvantage.pump.tank_output.description"), 0x508EE0, java.util.Optional.empty(),
                () -> {
                    if (machine == null) return 0;
                    var fluid = machine.resources().fluids("tank").peek(0);
                    return fluid.isEmpty() ? 0 : Math.max(1, RedstoneControl.level(fluid.amount() / (double) TANK_CAPACITY));
                }));
    }

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) { return true; }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host, int ticks) {
        int wait = state.get(cooldown);
        if (wait > 0) {
            state.set(cooldown, wait - 1);
            var current = pumpStatus();
            return current == Status.EXTENDING || current == Status.PUMPING || current == Status.PUMPING_INFINITE
                    ? ProcessState.RUNNING : ProcessState.IDLE;
        }
        var level = host.level();
        var pos = host.position();
        var tip = pos.below();
        while (!level.isOutsideBuildHeight(tip) && level.isLoaded(tip) && level.getBlockState(tip).getBlock() instanceof PumpPipeBlock)
            tip = tip.below();
        state.set(depth, pos.getY() - tip.getY() - 1);
        if (level.isOutsideBuildHeight(tip) || !level.isLoaded(tip)) return status(state, Status.NO_FLUID);
        var resources = host.resources();
        if (level.isEmptyBlock(tip)) {
            try (var tx = resources.begin()) {
                if (resources.energy("energy").consume(PIPE_COST, tx) != PIPE_COST) return status(state, Status.NO_POWER);
                tx.commit();
            }
            level.setBlock(tip, BuiltInRegistries.BLOCK.get(PIPE).defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, tip, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.3f, 1f);
            state.set(depth, state.get(depth) + 1);
            verdictTip = null;
            state.set(cooldown, PIPE_INTERVAL);
            return status(state, Status.EXTENDING);
        }
        var tank = resources.fluids("tank");
        var source = findSource(level, tip);
        if (source == null) {
            state.set(cooldown, PUMP_INTERVAL);
            return status(state, Status.NO_FLUID);
        }
        var fluid = level.getFluidState(source).getType();
        var bucket = new FluidAmount(fluid.builtInRegistryHolder(), net.minecraft.core.component.DataComponentPatch.EMPTY, BUCKET);
        long cost = PUMP_COST + (long) DEPTH_COST * Math.max(0, pos.getY() - source.getY());
        try (var tx = resources.begin()) {
            if (tank.insert(0, bucket, tx) != BUCKET) return status(state, Status.TANK_FULL);
            if (resources.energy("energy").consume(cost, tx) != cost) return status(state, Status.NO_POWER);
            boolean infinite = infinite(level, tip, source, fluid);
            if (!infinite) {
                var block = level.getBlockState(source);
                if (!(block.getBlock() instanceof BucketPickup pickup) || pickup.pickupBlock(null, level, source, block).isEmpty()) {
                    state.set(cooldown, PUMP_INTERVAL);
                    return status(state, Status.NO_FLUID);
                }
            }
            tx.commit();
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.5f, 1f);
            state.set(cooldown, PUMP_INTERVAL);
            return status(state, infinite ? Status.PUMPING_INFINITE : Status.PUMPING);
        }
    }

    /** Records the status and the screen's lines; working states keep the processor running. */
    private ProcessState status(ProcessorState state, Status next) {
        state.set(status, next.name());
        state.set(details, List.of(
                Component.translatable("screen.electricadvantage.pump.status." + next.name().toLowerCase(Locale.ROOT)),
                Component.translatable("screen.electricadvantage.pump.depth", state.get(depth))));
        return next == Status.EXTENDING || next == Status.PUMPING || next == Status.PUMPING_INFINITE
                ? ProcessState.RUNNING : ProcessState.IDLE;
    }

    /** The source feeding the pipe tip, or the blocks beside the lowest pipe, traced upstream by PA3. */
    private static BlockPos findSource(Level level, BlockPos tip) {
        var seeds = new ArrayList<BlockPos>();
        seeds.add(tip);
        for (var side : Direction.Plane.HORIZONTAL) seeds.add(tip.above().relative(side));
        for (var seed : seeds) {
            if (!level.isLoaded(seed)) continue;
            var fluid = level.getFluidState(seed);
            if (fluid.isEmpty()) continue;
            // PA3's upstream trace does not check chunk loading; reject a source it found in an unloaded chunk.
            var source = FluidSearch.findSourceBlock(level, fluid.getType(), seed);
            if (source != null && level.isLoaded(source) && level.getFluidState(source).isSource()) return source;
        }
        return null;
    }

    private boolean infinite(Level level, BlockPos tip, BlockPos source, Fluid fluid) {
        int threshold = Config.pumpInfiniteThreshold();
        if (threshold <= 0 || !fluid.defaultFluidState().is(INFINITE)) return false;
        long time = level.getGameTime();
        if (tip.equals(verdictTip) && fluid.isSame(verdictFluid) && threshold == verdictThreshold && time < verdictUntil)
            return verdictInfinite;
        verdictInfinite = FluidSearch.countSourceBlocks(level, fluid, source, threshold) >= threshold;
        verdictTip = tip;
        verdictFluid = fluid;
        verdictThreshold = threshold;
        verdictUntil = time + INFINITE_RECHECK;
        return verdictInfinite;
    }
}
