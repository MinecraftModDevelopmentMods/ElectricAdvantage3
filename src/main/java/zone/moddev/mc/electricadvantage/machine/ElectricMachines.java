package zone.moddev.mc.electricadvantage.machine;

import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.electricadvantage.ElectricAdvantage;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

/** Registers the block entity of each Electric Advantage XML machine in {@code mmdlib/machine/}. */
public final class ElectricMachines {
    private ElectricMachines() {}

    /** Machines that need nothing beyond {@link ElectricMachineEntity}. */
    private static final String[] MACHINES = {
        "electric_oven", "electric_still", "plastic_refinery", "electric_furnace", "electric_crusher", "electric_pump",
        "photovoltaic_generator", "steam_powered_generator", "electric_battery_array",
    };

    /** Called from the mod constructor, before MMDLib loads machine definitions. */
    public static void register() {
        for (var machine : MACHINES) register(machine, ElectricMachineEntity::new);
        register("hydroelectric_generator", zone.moddev.mc.electricadvantage.generation.HydroGeneratorEntity::new);
        register("laser_turret", zone.moddev.mc.electricadvantage.turret.LaserTurretEntity::new);
        register("laser_turret_evil", zone.moddev.mc.electricadvantage.turret.LaserTurretEntity::new);
        register("electric_drill", zone.moddev.mc.electricadvantage.drill.DrillEntity::new);
    }

    public static void register(String machine, MachineContent.EntityFactory factory) {
        MachineContent.registerEntityFactory(ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID, machine), factory);
    }
}
