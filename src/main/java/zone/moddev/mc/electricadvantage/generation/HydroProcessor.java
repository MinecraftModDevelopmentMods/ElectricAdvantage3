package zone.moddev.mc.electricadvantage.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.inventory.MachineTx;

/**
 * Hydroelectric generator: {@link #GENERATION} FE per tick while water flows sideways through the block below, where
 * the turbine hangs. The spinning blades hurt anything in that block.
 */
public final class HydroProcessor extends GeneratorProcessor {
    public static final double GENERATION = 4.0;
    public static final float PROPELLER_DAMAGE = 5f;
    private static final ResourceKey<net.minecraft.world.damagesource.DamageType> PROPELLER = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.parse("electricadvantage:propeller"));

    @Override
    protected double generate(ProcessorMachine host, long space, MachineTx tx) {
        var level = host.level();
        if (waterFlow(level, host.position()).horizontalDistanceSqr() <= 0.00001) return 0;
        if (level.getGameTime() % 8 == 0) {
            var damage = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PROPELLER));
            for (var victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(host.position().below()).deflate(0.03125)))
                victim.hurt(damage, PROPELLER_DAMAGE);
        }
        return GENERATION;
    }

    /** Flow of the water in the block below, or zero; also drives the turbine's direction on clients. */
    public static Vec3 waterFlow(Level level, BlockPos pos) {
        var below = pos.below();
        if (level == null || !level.hasChunkAt(below)) return Vec3.ZERO;
        for (var direction : Direction.Plane.HORIZONTAL)
            if (!level.hasChunkAt(below.relative(direction))) return Vec3.ZERO;
        var fluid = level.getFluidState(below);
        return fluid.is(FluidTags.WATER) ? fluid.getFlow(level, below) : Vec3.ZERO;
    }
}
