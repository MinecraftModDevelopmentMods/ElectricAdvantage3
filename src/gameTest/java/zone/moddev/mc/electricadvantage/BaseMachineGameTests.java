package zone.moddev.mc.electricadvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import zone.moddev.mc.lib.machine.neoforge.BaseMachine;

/** The Java machines built on MMDLib's BaseMachine: 3.0 saves still load, and contents drop once. */
@GameTestHolder(ElectricAdvantage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BaseMachineGameTests {
    private static final java.util.List<String> POWERED = java.util.List.of("electric_furnace", "electric_crusher",
        "electric_drill", "electric_pump", "laser_turret", "electric_fabricator", "growth_chamber_controller",
        "photovoltaic_generator", "hydroelectric_generator", "steam_powered_generator");

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void version30EnergyKeyLoadsInEveryJavaMachine(GameTestHelper h) {
        for (String name : POWERED) {
            var be = place(h, name);
            var tag = new CompoundTag();
            tag.putInt("Energy", 123);
            be.loadWithComponents(tag, h.getLevel().registryAccess());
            h.assertTrue(be.screenEnergy() != null && be.screenEnergy().stored() == 123,
                "3.0's \"Energy\" key loads into MMDLib's buffer: " + name);
            var saved = be.saveWithoutMetadata(h.getLevel().registryAccess());
            h.assertTrue(saved.contains("energy") && !saved.contains("Energy"), "The buffer is saved under MMDLib's key: " + name);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void idleMachinesSaveRightAfterLoading(GameTestHelper h) {
        // A chunk can load and save again without ticking; idle lanes must not save an empty work item.
        for (String name : java.util.List.of("electric_furnace", "electric_crusher", "growth_chamber", "electric_fabricator")) {
            var be = place(h, name);
            var registries = h.getLevel().registryAccess();
            be.loadWithComponents(be.saveWithoutMetadata(registries), registries);
            var saved = be.saveWithoutMetadata(registries);
            h.assertTrue(!saved.contains("Recipe0") && !saved.contains("Recipe"), "An idle machine saves no recipe: " + name);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void brokenMachinesDropTheirContentsOnce(GameTestHelper h) {
        var furnace = (zone.moddev.mc.electricadvantage.processing.ProcessingBlockEntity) place(h, "electric_furnace");
        furnace.items.setStackInSlot(0, new ItemStack(Items.RAW_IRON, 5));
        furnace.items.setStackInSlot(4, new ItemStack(Items.IRON_INGOT, 7));
        breakMachine(h);
        h.assertTrue(dropped(h, Items.RAW_IRON) == 5 && dropped(h, Items.IRON_INGOT) == 7, "The furnace drops its items exactly once");
        clearDrops(h);
        var drill = (zone.moddev.mc.electricadvantage.drill.DrillBlockEntity) place(h, "electric_drill");
        drill.items.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 9));
        breakMachine(h);
        h.assertTrue(dropped(h, Items.COBBLESTONE) == 9, "The drill drops its items exactly once");
        h.succeed();
    }

    private static BaseMachine place(GameTestHelper h, String name) {
        h.setBlock(4, 3, 4, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID, name)));
        return (BaseMachine) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(4, 3, 4)));
    }

    /** Removes the machine without dropping the machine block itself. */
    private static void breakMachine(GameTestHelper h) {
        h.getLevel().destroyBlock(h.absolutePos(new BlockPos(4, 3, 4)), false);
    }

    private static AABB around(GameTestHelper h) {
        return new AABB(h.absolutePos(new BlockPos(4, 3, 4))).inflate(2);
    }

    private static int dropped(GameTestHelper h, Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, around(h), entity -> entity.getItem().is(item))
            .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static void clearDrops(GameTestHelper h) {
        h.getLevel().getEntitiesOfClass(ItemEntity.class, around(h)).forEach(ItemEntity::discard);
    }
}
