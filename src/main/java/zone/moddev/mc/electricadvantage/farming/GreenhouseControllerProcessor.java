package zone.moddev.mc.electricadvantage.farming;

import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import zone.moddev.mc.lib.api.redstone.IRedstoneOutput;
import zone.moddev.mc.lib.api.redstone.RedstoneOutput;
import zone.moddev.mc.lib.machine.feature.Processor.ProcessState;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StorageAdmission;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

/**
 * The Control Unit's work: turns energy, water and fertilizer into greenhouse power, up to {@link #PRODUCTION} units a
 * tick, and shares it fairly across the growth chambers it touches, directly or through other touching chambers. There
 * is no greenhouse cable.
 *
 * <p>EA2 costs: 32 FE (8 electricity), 2 mB water and 1 fertilizer per unit. The comparator gives 15 when energy,
 * water and fertilizer are all enough for a unit (EA2's behaviour). The fertilizer slot admits only items in the
 * {@code electricadvantage:fertilizer} data map.
 */
public final class GreenhouseControllerProcessor extends StoredProcessor implements StorageAdmission, IRedstoneOutput {
    public static final int PRODUCTION = 16, FE_PER_UNIT = 32, WATER_PER_UNIT = 2, FERTILIZER_PER_UNIT = 1, MAX_CHAMBERS = 16,
            SCAN_INTERVAL = 20;

    private final ProcessorState.Key<Boolean> powered = variables.define("powered", Codec.BOOL, false);
    private final ProcessorState.Key<List<Component>> details = variables.define("details",
            ComponentSerialization.CODEC.listOf(), List.of(), List::copyOf, false, true);

    @Override
    public boolean acceptsItem(ProcessorMachine host, String member, ItemStack stack) {
        return FarmingSystems.fertilizer(stack) > 0;
    }

    @Override
    public List<RedstoneOutput> redstoneOutputs() {
        return List.of(new RedstoneOutput("greenhouse/ready", Component.translatable("screen.electricadvantage.greenhouse.ready"),
                Component.translatable("screen.electricadvantage.greenhouse.ready.description"), 0x83B967, Optional.empty(),
                () -> machine instanceof GreenhouseControllerEntity controller && ready(controller) ? 15 : 0));
    }

    private static boolean ready(GreenhouseControllerEntity controller) {
        return controller.resources().energy("energy").stored() >= FE_PER_UNIT
                && controller.resources().fluids("water").peek(0).amount() >= WATER_PER_UNIT
                && controller.fertilizer >= FERTILIZER_PER_UNIT;
    }

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) { return true; }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host, int ticks) {
        if (!(host instanceof GreenhouseControllerEntity controller) || !(host.level() instanceof ServerLevel level))
            return ProcessState.IDLE;
        long made = produce(controller);
        distribute(controller, level);
        state.set(powered, controller.greenhouse > 0);
        state.set(details, List.of(
                Component.translatable("screen.electricadvantage.greenhouse.fertilizer", controller.fertilizer,
                        GreenhouseControllerEntity.FERTILIZER_CAPACITY),
                Component.translatable("screen.electricadvantage.greenhouse.power", controller.greenhouse,
                        GreenhouseControllerEntity.BUFFER),
                Component.translatable("screen.electricadvantage.greenhouse.chambers", controller.chambers.size(), MAX_CHAMBERS)));
        return made > 0 ? ProcessState.RUNNING : ProcessState.IDLE;
    }

    private static long units(long available, int perUnit) { return perUnit == 0 ? Long.MAX_VALUE : available / perUnit; }

    private static long produce(GreenhouseControllerEntity controller) {
        var resources = controller.resources();
        var energy = resources.energy("energy");
        var water = resources.fluids("water");
        long made = Math.min(PRODUCTION, GreenhouseControllerEntity.BUFFER - controller.greenhouse);
        made = Math.min(made, units(energy.stored(), FE_PER_UNIT));
        made = Math.min(made, units(water.free(0), WATER_PER_UNIT));
        made = Math.min(made, units(controller.fertilizer, FERTILIZER_PER_UNIT));
        if (made <= 0) return 0;
        try (var tx = resources.begin()) {
            energy.consume(made * FE_PER_UNIT, tx);
            water.extract(0, (int) (made * WATER_PER_UNIT), tx);
            tx.commit();
        }
        controller.fertilizer -= made * FERTILIZER_PER_UNIT;
        controller.greenhouse += made;
        controller.setChanged();
        return made;
    }

    /** Bounded flood fill over touching growth chambers, starting from the Control Unit's faces. */
    private static void scan(GreenhouseControllerEntity controller, ServerLevel level) {
        var chambers = controller.chambers;
        chambers.clear();
        var start = controller.getBlockPos();
        var queue = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && chambers.size() < MAX_CHAMBERS) {
            var from = queue.poll();
            for (var side : Direction.values()) {
                var next = from.relative(side);
                if (!seen.add(next) || !level.isLoaded(next)) continue;
                if (level.getBlockEntity(next) instanceof GrowthChamberEntity) {
                    chambers.add(next.immutable());
                    queue.add(next);
                    if (chambers.size() >= MAX_CHAMBERS) break;
                }
            }
        }
    }

    private static void distribute(GreenhouseControllerEntity controller, ServerLevel level) {
        long time = level.getGameTime();
        if (time >= controller.nextScan) { scan(controller, level); controller.nextScan = time + SCAN_INTERVAL; }
        var targets = new ArrayList<GrowthChamberEntity>();
        for (var at : controller.chambers)
            if (level.isLoaded(at) && level.getBlockEntity(at) instanceof GrowthChamberEntity chamber) targets.add(chamber);
        if (targets.isEmpty() || controller.greenhouse <= 0) return;
        // Equal shares, rotating the remainder so no chamber is always first or last.
        Collections.rotate(targets, -(controller.rotation++ % targets.size()));
        long before = controller.greenhouse;
        while (controller.greenhouse > 0) {
            var hungry = targets.stream().filter(c -> c.greenhouseSpace() > 0).toList();
            if (hungry.isEmpty()) break;
            long share = Math.max(1, controller.greenhouse / hungry.size());
            for (var chamber : hungry) {
                if (controller.greenhouse <= 0) break;
                controller.greenhouse -= chamber.acceptGreenhouse(Math.min(share, controller.greenhouse));
            }
        }
        if (controller.greenhouse != before) controller.setChanged();
    }
}
