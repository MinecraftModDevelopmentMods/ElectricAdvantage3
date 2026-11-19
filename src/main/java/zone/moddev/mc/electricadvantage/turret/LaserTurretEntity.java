package zone.moddev.mc.electricadvantage.turret;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import zone.moddev.mc.electricadvantage.machine.ElectricMachineEntity;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.feature.Processor.ProcessState;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/**
 * Both laser turrets. Redstone holds the processor; this drops its target then, as in 3.0. On clients it keeps the
 * head's aim for {@code LaserTurretRenderer}: an idle sweep, tracking the target at 9 degrees per tick, or drooping
 * when unarmed (EA2's animation). Yaw 0 faces +X as in EA2.
 */
public final class LaserTurretEntity extends ElectricMachineEntity {
    public float yaw, pitch, oldYaw, oldPitch;
    public double beamLength;

    public LaserTurretEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineDefinition definition,
            MachineRecipeType recipes) {
        super(type, pos, state, definition, recipes);
    }

    public LaserTurretProcessor turret() {
        return features.findProcessor("turret", LaserTurretProcessor.class).orElseThrow();
    }

    /** Armed and not stopped by redstone. */
    public boolean active() {
        var turret = turret();
        return turret.armed() && turret.executionStatus() != ProcessState.HELD;
    }

    /** 3.0 and 3.1 saved the owner and team at the top level; they now live in the processor's state. */
    @Override
    protected CompoundTag migrateMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        tag = super.migrateMachineData(tag, registries);
        if (!tag.hasUUID("Owner") && !tag.contains("Team")) return tag;
        var converted = tag.copy();
        var features = converted.getCompound("features");
        var turret = features.getCompound("turret");
        if (converted.hasUUID("Owner")) turret.putString("owner", converted.getUUID("Owner").toString());
        if (converted.contains("Team")) turret.putString("team", converted.getString("Team"));
        features.put("turret", turret);
        converted.put("features", features);
        converted.remove("Owner");
        converted.remove("Team");
        return converted;
    }

    @Override
    protected void afterMachineTick() {
        super.afterMachineTick();
        var turret = turret();
        if (turret.executionStatus() == ProcessState.HELD) turret.standDown();
    }

    @Override
    public void clientTick() {
        super.clientTick();
        oldYaw = yaw;
        oldPitch = pitch;
        if (!active()) {
            if (pitch > -30) pitch = Math.max(-30, pitch - 2);
            return;
        }
        var target = turret().target(level);
        if (target == null) {
            pitch = approach(pitch, 0, 2);
            yaw += 2;
            if (yaw > 180) { yaw -= 360; oldYaw -= 360; }
            beamLength = 0;
            return;
        }
        var optic = LaserTurretProcessor.optic(worldPosition);
        var delta = LaserTurretProcessor.center(target).subtract(optic);
        float goalYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x));
        float goalPitch = (float) Math.toDegrees(Math.asin(delta.y / Math.max(1e-6, delta.length())));
        if (goalYaw - yaw > 180) { yaw += 360; oldYaw += 360; }
        else if (goalYaw - yaw < -180) { yaw -= 360; oldYaw -= 360; }
        yaw = approach(yaw, goalYaw, 9);
        pitch = approach(pitch, goalPitch, 9);
        beamLength = turret().showBeam(level) ? optic.distanceTo(LaserTurretProcessor.terminus(level, optic, delta.normalize())) : 0;
    }

    private static float approach(float value, float goal, float step) {
        return Math.abs(goal - value) <= step ? goal : value + Math.signum(goal - value) * step;
    }
}
