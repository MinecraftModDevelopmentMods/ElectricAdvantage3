package zone.moddev.mc.electricadvantage.drill;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.machine.feature.Processor.ProcessState;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/**
 * EA2's electric mining drill. Every power update it looks along its facing, up to {@link #RANGE} blocks, through air
 * and replaceable blocks (fluids included), and targets the 3x3 face around the first solid block. Each tick of mining
 * costs {@link #PROGRESS_COST} FE; the face is removed once progress reaches the sum of its blocks' strengths, and the
 * drops (worked out when the face was targeted) go to its output slots. Each power update it also pushes one item into
 * the inventory behind it and burns anything in its beam. At the end of its range it moves onto adjacent electric
 * scaffold ({@link DrillEntity} makes the move), leaving a cable where it was and a steel frame in front of that.
 *
 * <p>EA2 values at 4 FE per electricity: 96 FE per tick of mining, 1000 FE per move. A face takes the sum over its
 * blocks of max(32 x hardness, 16) ticks.
 */
public final class DrillProcessor extends StoredProcessor {
    public enum Status {IDLE, MINING, OUTPUT_FULL, END_OF_RANGE, NO_POWER, DISABLED}

    public static final ResourceKey<net.minecraft.world.damagesource.DamageType> LASER = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.parse("electricadvantage:laser_drill"));
    public static final int PROGRESS_COST = 96, MOVE_COST = 1000, RANGE = 64, UPDATE_INTERVAL = 8, BURN_SECONDS = 3;
    public static final float MINING_TIME_FACTOR = 32f, BEAM_DAMAGE = 2f;
    private static final long NONE = Long.MIN_VALUE;

    private final ProcessorState.Key<Long> target = variables.define("target", Codec.LONG, NONE,
            UnaryOperator.identity(), false, false);
    private final ProcessorState.Key<Integer> progress = variables.define("progress", Codec.INT, 0,
            UnaryOperator.identity(), false, false);
    private final ProcessorState.Key<Integer> progressGoal = variables.define("progress_goal", Codec.INT, 0,
            UnaryOperator.identity(), false, false);
    private final ProcessorState.Key<Integer> beam = variables.define("beam", Codec.INT, 0,
            UnaryOperator.identity(), false, true);
    private final ProcessorState.Key<String> status = variables.define("drill_status", Codec.STRING, Status.IDLE.name(),
            UnaryOperator.identity(), false, false);
    private final ProcessorState.Key<List<Component>> details = variables.define("details",
            ComponentSerialization.CODEC.listOf(), List.of(), List::copyOf, false, true);

    // Server-only: the face's block types and drops (read again after loading), the facing last seen, the power update
    // stagger, and a move for DrillEntity to make after this tick.
    private BlockState[] targetStates;
    private List<ItemStack> targetDrops;
    private Direction facing;
    private int age;
    private boolean endOfRange;
    private Direction move;

    public @Nullable BlockPos target() { long pos = variables.get(target); return pos == NONE ? null : BlockPos.of(pos); }
    public int progress() { return variables.get(progress); }
    public int progressGoal() { return variables.get(progressGoal); }
    /** Blocks from the drill to the targeted face, or 0 when no beam is shown; on clients too. */
    public int beamLength() { return variables.get(beam); }
    public Status drillStatus() {
        try { return Status.valueOf(variables.get(status)); } catch (IllegalArgumentException unknown) { return Status.IDLE; }
    }

    public static int screenProgress(DefinedMachineEntity machine) {
        return machine.features.findProcessor("drill", DrillProcessor.class).map(DrillProcessor::progress).orElse(0);
    }
    public static int screenProgressGoal(DefinedMachineEntity machine) {
        return machine.features.findProcessor("drill", DrillProcessor.class).map(drill -> Math.max(1, drill.progressGoal())).orElse(1);
    }

    /** Faces into the block it was placed against, any of the six directions, as in EA2. */
    @Override
    public Direction placementFacing(BlockPlaceContext context, Direction suggested) {
        return context.getClickedFace().getOpposite();
    }

    /** The move requested this tick, cleared. */
    @Nullable Direction takeMove() {
        var way = move;
        move = null;
        return way;
    }

    /** Redstone stops the drill and drops its target. */
    void standDown() {
        variables.update(() -> {
            untarget();
            variables.set(beam, 0);
            status(Status.DISABLED);
        });
    }

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) { return true; }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host, int ticks) {
        if (!(host.level() instanceof ServerLevel level)) return ProcessState.IDLE;
        var pos = host.position();
        var current = RelativeFace.facing(host.blockState());
        // Rotating the drill drops its target, as in EA2.
        if (facing != null && facing != current) { untarget(); state.set(beam, 0); }
        facing = current;
        var energy = host.resources().energy("energy");
        var face = target();
        if (face != null && targetStates == null) {
            // Block types and drops are read again once the level is available; progress is kept.
            int saved = progress();
            targetFace(level, face);
            state.set(progress, Math.min(saved, progressGoal()));
        }
        if (face != null) {
            if (powered(host) && fits(host, targetDrops) && canMine(level, face)) {
                try (var tx = host.resources().begin()) {
                    energy.consume(PROGRESS_COST, tx);
                    tx.commit();
                }
                state.set(progress, progress() + 1);
                if (progress() >= progressGoal()) mine(level, host, face);
            } else if (progress() > 0) state.set(progress, progress() - 1);
        }
        // EA2's power update: every UPDATE_INTERVAL ticks, staggered by position.
        if (++age >= UPDATE_INTERVAL) age = 0;
        if (age == Math.floorMod(pos.hashCode(), UPDATE_INTERVAL) && powerUpdate(level, host)) return ProcessState.IDLE;
        status(!powered(host) ? Status.NO_POWER : target() == null ? (endOfRange ? Status.END_OF_RANGE : Status.IDLE)
                : fits(host, targetDrops) ? Status.MINING : Status.OUTPUT_FULL);
        return drillStatus() == Status.MINING ? ProcessState.RUNNING : ProcessState.IDLE;
    }

    private static boolean powered(ProcessorMachine host) { return host.resources().energy("energy").stored() > PROGRESS_COST; }

    private void status(Status next) {
        variables.set(status, next.name());
        variables.set(details, List.of(Component.translatable(
                "screen.electricadvantage.drill.status." + next.name().toLowerCase(Locale.ROOT))));
    }

    /** Targets, re-checks or moves, then sets the beam, exports one item and burns anything in the beam. True once a move is requested. */
    private boolean powerUpdate(ServerLevel level, ProcessorMachine host) {
        var pos = host.position();
        var energy = host.resources().energy("energy");
        if (energy.stored() <= 0) untarget();
        else if (target() == null) {
            endOfRange = false;
            var next = pos.relative(facing);
            for (int distance = 0; ; distance++) {
                if (distance == RANGE) { endOfRange = true; break; }
                // Unlike EA2, the drill doesn't load chunks; it waits for the one ahead.
                if (!level.isLoaded(next)) break;
                var state = level.getBlockState(next);
                if (state.isAir() || state.canBeReplaced()) {
                    next = next.relative(facing);
                    if (next.getY() <= level.getMinBuildHeight() || next.getY() >= level.getMaxBuildHeight() - 1) { endOfRange = true; break; }
                } else {
                    if (canMine(level, next)) targetFace(level, next);
                    else endOfRange = true;
                    break;
                }
            }
            if (endOfRange && followTrack(level, host)) return true;
        } else {
            var area = area(target());
            for (int i = 0; i < area.length; i++)
                if (level.getBlockState(area[i]).getBlock() != targetStates[i].getBlock()) { untarget(); break; }
        }
        var face = target();
        boolean shining = powered(host) && face != null;
        variables.set(beam, shining ? pos.distManhattan(face) : 0);
        exportBehind(level, host);
        if (shining) {
            // EA2's box runs from the target's corner to the far corner of the drill.
            var box = new AABB(face.getX(), face.getY(), face.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            var damage = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(LASER));
            for (var victim : level.getEntitiesOfClass(LivingEntity.class, box)) {
                victim.igniteForSeconds(BURN_SECONDS);
                victim.hurt(damage, BEAM_DAMAGE);
            }
        }
        return false;
    }

    /** One item from the output slots into the inventory behind the drill, if there is one. */
    private void exportBehind(ServerLevel level, ProcessorMachine host) {
        var behind = facing.getOpposite();
        var next = host.position().relative(behind);
        if (!level.isLoaded(next) || level.isEmptyBlock(next)) return;
        var target = level.getCapability(Capabilities.ItemHandler.BLOCK, next, facing);
        if (target == null) return;
        var output = host.resources().items("output");
        for (int slot = 0; slot < output.size(); slot++) {
            var stack = output.peek(slot);
            if (stack.isEmpty()) continue;
            var one = stack.copyWithCount(1);
            if (!net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(target, one, true).isEmpty()) continue;
            try (var tx = host.resources().begin()) {
                if (output.extract(slot, 1, tx) != 1) continue;
                net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(target, one, false);
                tx.commit();
            }
            return;
        }
    }

    /** Requests a move onto the first adjacent scaffold (down, up, north, south, west, east). */
    private boolean followTrack(ServerLevel level, ProcessorMachine host) {
        if (host.resources().energy("energy").stored() < MOVE_COST) return false;
        for (var side : Direction.values())
            if (DrillEntity.isTrack(level.getBlockState(host.position().relative(side)))) {
                untarget();
                move = side;
                return true;
            }
        return false;
    }

    private void mine(ServerLevel level, ProcessorMachine host, BlockPos face) {
        level.playSound(null, face, targetStates[0].getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.5f, 1f);
        level.playSound(null, host.position(), SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.5f, 1f);
        for (var pos : area(face)) if (canMine(level, pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        try (var tx = host.resources().begin()) {
            var output = host.resources().items("output");
            for (var drop : targetDrops) output.insert(drop, tx);
            tx.commit();
        }
        untarget();
    }

    private void targetFace(ServerLevel level, BlockPos center) {
        var area = area(center);
        int goal = 0;
        targetStates = new BlockState[area.length];
        targetDrops = new ArrayList<>();
        for (int i = 0; i < area.length; i++) {
            var state = level.getBlockState(area[i]);
            targetStates[i] = state;
            goal += strength(level, area[i], state);
            targetDrops.addAll(Block.getDrops(state, level, area[i], level.getBlockEntity(area[i])));
        }
        variables.set(target, center.asLong());
        variables.set(progress, 0);
        variables.set(progressGoal, goal);
    }

    private void untarget() {
        variables.set(target, NONE);
        variables.set(progress, 0);
        variables.set(progressGoal, 0);
        targetStates = null;
        targetDrops = null;
    }

    /** The centre first, then the other eight blocks of the face across the drill's axis. */
    private BlockPos[] area(BlockPos center) {
        var area = new BlockPos[9];
        area[0] = center;
        int n = 1;
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                area[n++] = switch (facing.getAxis()) {
                    case X -> center.offset(0, a, b);
                    case Y -> center.offset(a, 0, b);
                    case Z -> center.offset(a, b, 0);
                };
            }
        return area;
    }

    /** Unbreakable blocks have negative hardness. */
    private static boolean canMine(Level level, BlockPos pos) { return level.getBlockState(pos).getDestroySpeed(level, pos) >= 0; }

    private static int strength(Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) return 0;
        return (int) Math.max(MINING_TIME_FACTOR * state.getDestroySpeed(level, pos), 0.5f * MINING_TIME_FACTOR);
    }

    /** Whether all the drops fit in the output slots together. */
    private static boolean fits(ProcessorMachine host, @Nullable List<ItemStack> drops) {
        return drops == null || host.resources().items("output").fits(drops);
    }
}
