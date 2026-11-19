package zone.moddev.mc.electricadvantage.turret;

import com.mojang.serialization.Codec;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import zone.moddev.mc.electricadvantage.Config;
import zone.moddev.mc.lib.api.redstone.IRedstoneOutput;
import zone.moddev.mc.lib.api.redstone.RedstoneOutput;
import zone.moddev.mc.lib.machine.feature.MachinePlacement;
import zone.moddev.mc.lib.machine.feature.Processor.ProcessState;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

/**
 * The laser turret's work: locks onto the nearest visible target, charges, then fires a beam that hurts every living
 * entity along it up to the first solid block. The turret targets monsters and players outside its owner's team;
 * {@link Evil the hidden variant} targets everything except monsters and ambient mobs.
 *
 * <p>EA2 values at 4 FE per electricity: 4000 FE per shot, 4 FE per armed tick, a 31-tick charge that hits at 7 ticks
 * left and shows the beam for the last 10. The comparator reads 15 while locked on, otherwise up to 8 for the charge.
 */
public class LaserTurretProcessor extends StoredProcessor implements IRedstoneOutput {
    public static final ResourceKey<net.minecraft.world.damagesource.DamageType> LASER = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.parse("electricadvantage:laser"));
    public static final int SHOT_COST = 4000, IDLE_COST = 4, BURN_SECONDS = 3, TARGET_RANGE = 16, ATTACK_RANGE = 32,
            CHARGE_TIME = 31, HIT_TIME = 7, BLAST_TIME = 10, SCAN_INTERVAL = 4;
    public static final float DAMAGE = 7f;
    private static final int NO_TARGET = Integer.MIN_VALUE;

    /** The hidden player-targeting turret. */
    public static final class Evil extends LaserTurretProcessor {
        @Override protected boolean evil() { return true; }
    }

    private final ProcessorState.Key<String> owner = variables.define("owner", Codec.STRING, "",
            UnaryOperator.identity(), false, false);
    private final ProcessorState.Key<String> team = variables.define("team", Codec.STRING, "",
            UnaryOperator.identity(), false, true);
    private final ProcessorState.Key<Integer> target = variables.define("target", Codec.INT, NO_TARGET,
            UnaryOperator.identity(), false, true);
    private final ProcessorState.Key<Long> chargeStart = variables.define("charge_start", Codec.LONG, 0L,
            UnaryOperator.identity(), false, true);
    private final ProcessorState.Key<Boolean> armed = variables.define("armed", Codec.BOOL, false,
            UnaryOperator.identity(), false, true);
    private final ProcessorState.Key<List<Component>> details = variables.define("details",
            ComponentSerialization.CODEC.listOf(), List.of(), List::copyOf, false, true);

    // Server-only: ticks left in the current charge, the scan stagger, and whether a saved target ID was dropped.
    private int attack, scanCountdown = -1;
    private boolean fresh = true;

    protected boolean evil() { return false; }

    public @Nullable UUID owner() {
        var id = variables.get(owner);
        return id.isEmpty() ? null : UUID.fromString(id);
    }
    public @Nullable String team() {
        var name = variables.get(team);
        return name.isEmpty() ? null : name;
    }
    public boolean locked() { return variables.get(target) != NO_TARGET; }
    public boolean armed() { return variables.get(armed); }
    public @Nullable Entity target(Level level) { return level == null || !locked() ? null : level.getEntity(variables.get(target)); }

    /** Ticks left in the current charge, from the synced start time; on clients too. */
    public int attack(Level level) {
        return locked() ? Math.max(0, CHARGE_TIME - (int) (level.getGameTime() - variables.get(chargeStart))) : 0;
    }
    public boolean showBeam(Level level) {
        int left = attack(level);
        return locked() && left > 0 && left <= BLAST_TIME;
    }

    public void setOwner(Player player) {
        variables.update(() -> recordOwner(player));
    }
    public void setTeam(@Nullable String name) {
        variables.update(() -> variables.set(team, name == null ? "" : name));
    }
    private void recordOwner(Player player) {
        variables.set(owner, player.getUUID().toString());
        variables.set(team, player.getTeam() == null ? "" : player.getTeam().getName());
    }

    @Override
    protected void onPlaced(MachinePlacement placement) {
        if (placement.placer() != null) recordOwner(placement.placer());
    }

    /** Drops the target, as when redstone stops the turret. */
    public void standDown() {
        if (locked()) variables.update(() -> setTarget(null, 0));
    }

    @Override
    public List<RedstoneOutput> redstoneOutputs() {
        return List.of(new RedstoneOutput("turret/state", Component.translatable("screen.electricadvantage.turret.output"),
                Component.translatable("screen.electricadvantage.turret.output.description"), 0xD85B5B, Optional.empty(),
                () -> {
                    if (machine == null) return 0;
                    if (locked()) return 15;
                    var energy = machine.resources().energy("energy");
                    return (int) (8 * energy.stored() / Math.max(1, energy.capacity()));
                }));
    }

    /** EA2 team rule: players are enemies only when the turret has a team and they are not on it. */
    public boolean isEnemy(Player player) {
        if (player.isCreative() || player.isSpectator() || team() == null) return false;
        return player.getTeam() == null || !team().equals(player.getTeam().getName());
    }

    public boolean isTarget(LivingEntity entity) {
        if (!entity.isAlive() || entity instanceof ArmorStand) return false;
        if (evil()) {
            if (entity instanceof Player player) return !player.isCreative() && !player.isSpectator();
            return !(entity instanceof Enemy) && entity.getType().getCategory() != MobCategory.AMBIENT;
        }
        if (entity instanceof Player player) return isEnemy(player);
        return entity instanceof Enemy;
    }

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) { return true; }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host, int ticks) {
        if (!(host.level() instanceof ServerLevel level)) return ProcessState.IDLE;
        // Entity IDs do not survive a reload; a saved one could name a different entity.
        if (fresh) { fresh = false; setTarget(null, level.getGameTime()); }
        if (attack > 0) attack--;
        var energy = host.resources().energy("energy");
        boolean active = energy.stored() >= SHOT_COST;
        state.set(armed, active);
        if (active) {
            if (!locked()) {
                // Staggered by position so neighbouring turrets do not all scan on the same tick.
                if (scanCountdown < 0) scanCountdown = Math.floorMod(host.position().hashCode(), SCAN_INTERVAL);
                if (scanCountdown-- == 0) { scanCountdown = SCAN_INTERVAL - 1; acquire(level, host); }
            }
            validate(level, host);
            if (locked()) {
                if (attack == HIT_TIME) fire(level, host);
                else if (attack <= 1) startCharge(level.getGameTime());
            }
            try (var tx = host.resources().begin()) {
                energy.consume(Math.min(energy.stored(), IDLE_COST), tx);
                tx.commit();
            }
        } else if (locked()) setTarget(null, level.getGameTime());
        state.set(details, details(level));
        return locked() ? ProcessState.RUNNING : ProcessState.IDLE;
    }

    private List<Component> details(Level level) {
        var current = target(level);
        Component status = !armed() ? Component.translatable("screen.electricadvantage.turret.no_power")
                : current == null ? Component.translatable("screen.electricadvantage.turret.scanning")
                : Component.translatable("screen.electricadvantage.turret.target", current.getDisplayName());
        Component teamLine = evil() ? Component.translatable("screen.electricadvantage.turret.evil")
                : team() == null ? Component.translatable("screen.electricadvantage.turret.no_team")
                : Component.translatable("screen.electricadvantage.turret.team", team());
        return List.of(status, teamLine);
    }

    public static Vec3 optic(net.minecraft.core.BlockPos pos) { return Vec3.atLowerCornerOf(pos).add(0.5, 0.75, 0.5); }
    public static Vec3 center(Entity entity) { return entity.position().add(0, entity.getBbHeight() * 0.5, 0); }

    private void acquire(ServerLevel level, ProcessorMachine host) {
        var optic = optic(host.position());
        double range = TARGET_RANGE;
        level.getEntitiesOfClass(LivingEntity.class, new AABB(optic, optic).inflate(range),
                        e -> isTarget(e) && center(e).distanceToSqr(optic) <= range * range && canSee(level, optic, e))
                .stream().min(Comparator.comparingDouble(e -> center(e).distanceToSqr(optic)))
                .ifPresent(found -> setTarget(found, level.getGameTime()));
    }

    private void validate(ServerLevel level, ProcessorMachine host) {
        if (!locked()) return;
        var optic = optic(host.position());
        var current = target(level);
        if (!(current instanceof LivingEntity living) || !isTarget(living) || center(current).distanceTo(optic) > TARGET_RANGE
                || !canSee(level, optic, current))
            setTarget(null, level.getGameTime());
    }

    public static boolean canSee(Level level, Vec3 optic, Entity entity) {
        var to = center(entity);
        // Start just outside the turret head so the turret's own block never blocks the view.
        var from = optic.add(to.subtract(optic).normalize());
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() != HitResult.Type.BLOCK;
    }

    /** Where a beam from {@code optic} along {@code direction} stops: the first solid block, or full range. */
    public static Vec3 terminus(Level level, Vec3 optic, Vec3 direction) {
        var end = optic.add(direction.scale(ATTACK_RANGE));
        var hit = level.clip(new ClipContext(optic.add(direction.scale(0.5)), end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : end;
    }

    private void fire(ServerLevel level, ProcessorMachine host) {
        var current = target(level);
        if (current == null) return;
        var energy = host.resources().energy("energy");
        try (var tx = host.resources().begin()) {
            if (energy.consume(SHOT_COST, tx) != SHOT_COST) return;
            tx.commit();
        }
        var optic = optic(host.position());
        var end = terminus(level, optic, center(current).subtract(optic).normalize());
        var sound = BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.tryParse(Config.turretLaserSound()))
                .orElse(SoundEvents.ENDERMAN_TELEPORT);
        level.playSound(null, optic.x, optic.y, optic.z, sound, SoundSource.BLOCKS, 1f, 1f);
        var damage = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(LASER));
        var ownerId = owner();
        Player ownerPlayer = ownerId == null ? null : level.getPlayerByUUID(ownerId);
        for (var victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(optic, end).inflate(1),
                e -> e.isAlive() && e.getBoundingBox().clip(optic, end).isPresent())) {
            victim.igniteForSeconds(BURN_SECONDS);
            victim.hurt(damage, DAMAGE);
            // EA2 turned victims on the owner; this also names the owner in the death message.
            if (ownerPlayer != null && victim != ownerPlayer) victim.setLastHurtByMob(ownerPlayer);
            var feet = victim.blockPosition();
            if (level.isEmptyBlock(feet) && !level.isEmptyBlock(feet.below()) && BaseFireBlock.canBePlacedAt(level, feet, Direction.UP))
                level.setBlockAndUpdate(feet, BaseFireBlock.getState(level, feet));
        }
        if (!current.isAlive()) setTarget(null, level.getGameTime());
    }

    private void setTarget(@Nullable Entity entity, long now) {
        variables.set(target, entity == null ? NO_TARGET : entity.getId());
        if (entity == null) attack = 0;
        else startCharge(now);
    }

    private void startCharge(long now) {
        attack = CHARGE_TIME;
        variables.set(chargeStart, now);
    }
}
