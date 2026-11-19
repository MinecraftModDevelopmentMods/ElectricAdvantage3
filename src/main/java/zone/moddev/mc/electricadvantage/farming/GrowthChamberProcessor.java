package zone.moddev.mc.electricadvantage.farming;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import zone.moddev.mc.electricadvantage.machine.ItemPush;
import zone.moddev.mc.lib.machine.feature.Processor.ProcessState;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StorageAdmission;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

/**
 * Grows the growth chamber's three lanes. Each growth tick of a lane uses one unit of greenhouse power; a finished
 * lane rolls its harvest from the {@code electricadvantage:growing} recipe for its seed and soil, using the soil's
 * fertility and yield and the chamber's upgrade, and holds it until all of it fits. Outputs are pushed, up to
 * {@link #EXPORT} items a tick, into neighbours other than chambers: harvested seeds and soils must not fill a
 * neighbouring chamber's catalyst slots.
 *
 * <p>The soil and seed slots admit only items some growing recipe uses there.
 */
public final class GrowthChamberProcessor extends StoredProcessor implements StorageAdmission {
    public static final int EXPORT = 16;

    private final ProcessorState.Key<Boolean> powered = variables.define("powered", Codec.BOOL, false);
    private final ProcessorState.Key<List<Component>> details = variables.define("details",
            ComponentSerialization.CODEC.listOf(), List.of(), List::copyOf, false, true);

    @Override
    public boolean acceptsItem(ProcessorMachine host, String member, ItemStack stack) {
        var level = host.level();
        if (level == null) return false;
        if (member.startsWith("soil/")) return isSoil(level, stack);
        if (member.startsWith("seed/")) return isSeed(level, stack);
        return false;
    }

    public static boolean isSoil(Level level, ItemStack stack) {
        return level.getRecipeManager().getAllRecipesFor(FarmingSystems.TYPE.get()).stream().anyMatch(r -> r.value().soil().test(stack));
    }
    public static boolean isSeed(Level level, ItemStack stack) {
        return level.getRecipeManager().getAllRecipesFor(FarmingSystems.TYPE.get()).stream().anyMatch(r -> r.value().seed().test(stack));
    }

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) { return true; }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host, int ticks) {
        if (!(host instanceof GrowthChamberEntity chamber)) return ProcessState.IDLE;
        boolean active = false;
        for (int lane = 0; lane < GrowthChamberEntity.LANES; lane++) active |= growLane(chamber, lane);
        int remaining = EXPORT;
        var level = host.level();
        for (int n = 0; n < 6 && remaining > 0; n++) {
            var side = Direction.values()[(int) ((level.getGameTime() + n) % 6)];
            if (!(level.getBlockEntity(host.position().relative(side)) instanceof GrowthChamberEntity))
                remaining -= ItemPush.push(host, "output", side, remaining);
        }
        state.set(powered, chamber.greenhouse() > 0);
        state.set(details, List.of(Component.translatable("screen.electricadvantage.growth_chamber.greenhouse",
                chamber.greenhouse(), GrowthChamberEntity.CAPACITY)));
        return active ? ProcessState.RUNNING : ProcessState.IDLE;
    }

    private boolean growLane(GrowthChamberEntity chamber, int lane) {
        var held = chamber.pending.get(lane);
        if (!held.isEmpty()) {
            if (!emit(chamber, held)) return false;
            held.clear();
            chamber.setChanged();
        }
        var found = chamber.recipe(lane);
        if (found.isEmpty()) { reset(chamber, lane); return false; }
        var seed = chamber.seed(lane);
        var soil = chamber.soil(lane);
        if (!found.get().id().equals(chamber.recipeIds[lane]) || !ItemStack.isSameItemSameComponents(seed, chamber.workSeeds[lane])
                || !ItemStack.isSameItemSameComponents(soil, chamber.workSoils[lane])) {
            reset(chamber, lane);
            chamber.recipeIds[lane] = found.get().id();
            chamber.workSeeds[lane] = seed.copyWithCount(1);
            chamber.workSoils[lane] = soil.copyWithCount(1);
            chamber.setChanged();
        }
        // Without greenhouse power, growth pauses and keeps its progress.
        if (!chamber.useGreenhouse()) return false;
        var recipe = found.get().value();
        if (++chamber.progress[lane] >= recipe.time()) {
            chamber.progress[lane] = 0;
            var harvest = recipe.harvest(seed, FarmingSystems.fertility(soil), FarmingSystems.yield(soil), chamber.upgrade(),
                    chamber.getLevel().random);
            if (!emit(chamber, harvest)) held.addAll(harvest);
        }
        return true;
    }

    private static void reset(GrowthChamberEntity chamber, int lane) {
        if (chamber.progress[lane] != 0 || chamber.recipeIds[lane] != null) chamber.setChanged();
        chamber.progress[lane] = 0;
        chamber.recipeIds[lane] = null;
        chamber.workSeeds[lane] = ItemStack.EMPTY;
        chamber.workSoils[lane] = ItemStack.EMPTY;
    }

    /** Inserts the whole harvest into the outputs, or nothing at all. */
    private static boolean emit(GrowthChamberEntity chamber, List<ItemStack> harvest) {
        var output = chamber.resources().items("output");
        if (!output.fits(harvest)) return false;
        try (var tx = chamber.resources().begin()) {
            for (var stack : harvest) output.insert(stack, tx);
            tx.commit();
        }
        return true;
    }
}
