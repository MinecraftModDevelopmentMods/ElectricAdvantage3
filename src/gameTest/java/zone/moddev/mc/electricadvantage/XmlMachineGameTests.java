package zone.moddev.mc.electricadvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

/** The Electric Oven, Electric Still and Petro-Plastic Refinery, which are MMDLib XML machines. */
@GameTestHolder(ElectricAdvantage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class XmlMachineGameTests {
    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void ovenCooksOnlyFood(GameTestHelper h) {
        var be = place(h, "electric_oven");
        h.assertTrue(!items(h).insertItem(0, new ItemStack(Items.RAW_IRON), false).isEmpty(), "Oven rejects non-food smelting input");
        h.assertTrue(items(h).insertItem(0, new ItemStack(Items.BEEF), false).isEmpty(), "Oven accepts raw food");
        run(be, 50);
        h.assertTrue(be.items.getStackInSlot(1).isEmpty(), "Food is not cooked before 100 ticks");
        run(be, 55);
        h.assertTrue(be.items.getStackInSlot(1).is(Items.COOKED_BEEF) && be.items.getStackInSlot(0).isEmpty(),
            "Oven cooks one food item in 100 ticks");
        h.assertTrue(!items(h).insertItem(1, new ItemStack(Items.COOKED_BEEF), false).isEmpty(), "Automation cannot insert into the output slot");
        var recipe = h.getLevel().getRecipeManager().byKey(id("generated/oven_cooking/minecraft/cooked_beef")).orElseThrow();
        h.assertTrue(recipe.value() instanceof zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry entry
            && entry.recipe().time() == 100, "Generated oven recipes take 100 ticks, whatever the furnace time");
        h.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void stillRunsPowerAdvantageDistillingOnElectricity(GameTestHelper h) {
        var be = place(h, "electric_still");
        var tanks = fluids(h);
        h.assertTrue(tanks.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 0, "Only distilling inputs enter the still");
        // The test-only fast_distilling recipe: 2 mB crude oil to 1 mB refined oil in 2 ticks.
        tanks.fill(new FluidStack(fluid("crude_oil"), 2), FluidAction.EXECUTE);
        be.energy.setStored(be.energy.capacity());
        long before = be.energy.stored();
        for (int tick = 0; tick < 4; tick++) be.serverTick();
        h.assertTrue(be.fluids.getFluidInTank(1).is(fluid("refined_oil")) && be.fluids.getFluidInTank(1).getAmount() == 1
            && be.fluids.getFluidInTank(0).isEmpty(), "A Power Advantage distilling recipe runs in the Electric Still");
        h.assertTrue(before - be.energy.stored() == 32, "Distilling costs 16 FE per tick");
        h.assertTrue(h.getLevel().getRecipeManager()
            .byKey(id("generated/electric_distilling/poweradvantage/distilling_refined_oil")).isPresent(),
            "Power Advantage's own distilling recipe is copied for the Electric Still");
        h.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void refineryMakesPlasticFromBothOils(GameTestHelper h) {
        var be = place(h, "plastic_refinery");
        var tanks = fluids(h);
        h.assertTrue(tanks.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 0, "Refinery rejects other fluids");
        tanks.fill(new FluidStack(fluid("crude_oil"), 100), FluidAction.EXECUTE);
        run(be, 105);
        h.assertTrue(be.fluids.getFluidInTank(0).isEmpty() && be.items.getStackInSlot(0).getCount() == 1
            && be.items.getStackInSlot(0).is(item("petrolplastic_ingot")), "100 mB crude oil makes one plastic ingot");
        be.items.setStackInSlot(0, new ItemStack(item("petrolplastic_ingot"), 64));
        tanks.fill(new FluidStack(fluid("refined_oil"), 100), FluidAction.EXECUTE);
        run(be, 105);
        h.assertTrue(be.fluids.getFluidInTank(0).getAmount() == 100, "A full output keeps the oil");
        be.items.setStackInSlot(0, ItemStack.EMPTY);
        run(be, 105);
        h.assertTrue(be.fluids.getFluidInTank(0).isEmpty() && be.items.getStackInSlot(0).getCount() == 1,
            "Refined oil also makes plastic");
        h.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void version30SavesKeepTheirContents(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var oven = place(h, "electric_oven");
        var ovenSave = new CompoundTag();
        ovenSave.put("Items", legacyItems(registries, 2, 0, new ItemStack(Items.BEEF, 3), 1, new ItemStack(Items.COOKED_BEEF, 5)));
        oven.loadWithComponents(ovenSave, registries);
        h.assertTrue(oven.items.getStackInSlot(0).is(Items.BEEF) && oven.items.getStackInSlot(0).getCount() == 3
            && oven.items.getStackInSlot(1).getCount() == 5, "A 3.0 oven keeps its input and output");

        var refinery = place(h, "plastic_refinery");
        var refinerySave = new CompoundTag();
        refinerySave.put("Items", legacyItems(registries, 1, 0, new ItemStack(item("petrolplastic_ingot"), 7)));
        refinerySave.put("Input", legacyTank(registries, new FluidStack(fluid("crude_oil"), 750)));
        refinery.loadWithComponents(refinerySave, registries);
        h.assertTrue(refinery.items.getStackInSlot(0).getCount() == 7 && refinery.fluids.getFluidInTank(0).getAmount() == 750,
            "A 3.0 refinery keeps its plastic and oil");

        var still = place(h, "electric_still");
        var stillSave = new CompoundTag();
        stillSave.put("Input", legacyTank(registries, new FluidStack(fluid("crude_oil"), 400)));
        still.loadWithComponents(stillSave, registries);
        h.assertTrue(still.fluids.getFluidInTank(0).getAmount() == 400, "A 3.0 still keeps its input oil");
        h.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure", timeoutTicks = 200)
    public static void cablePowersAnXmlMachine(GameTestHelper h) {
        h.setBlock(1, 3, 4, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("poweradvantage:infinite_power")));
        h.setBlock(2, 3, 4, block("electric_conduit"));
        h.setBlock(3, 3, 4, block("electric_oven"));
        var be = (DefinedMachineEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3, 3, 4)));
        be.items.setStackInSlot(0, new ItemStack(Items.POTATO));
        h.runAfterDelay(150, () -> {
            h.assertTrue(be.items.getStackInSlot(1).is(Items.BAKED_POTATO), "Electric cable powers the XML oven");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void itemPortsMoveItemsThroughNeighbours(GameTestHelper h) {
        var be = place(h, "electric_oven");
        h.setBlock(5, 3, 4, net.minecraft.world.level.block.Blocks.CHEST);
        var chest = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(new BlockPos(5, 3, 4)), null);
        var automation = be.screenAutomation();
        be.items.setStackInSlot(1, new ItemStack(Items.COOKED_BEEF, 5));
        automation.slots().setStackInSlot(zone.moddev.mc.lib.api.automation.AutomationInventory.addonSlot(automation.index("output"), 0),
            new ItemStack(item("item_output_port")));
        be.serverTick();
        be.serverTick();
        h.assertTrue(be.items.getStackInSlot(1).isEmpty() && count(chest, Items.COOKED_BEEF) == 5,
            "An Item Output Port pushes the output group into a neighbouring chest");
        // Slot 0 holds the cooked beef just pushed out.
        h.assertTrue(chest.insertItem(1, new ItemStack(Items.BEEF, 3), false).isEmpty(), "Raw beef goes into the chest");
        automation.slots().setStackInSlot(zone.moddev.mc.lib.api.automation.AutomationInventory.addonSlot(automation.index("input"), 0),
            new ItemStack(item("item_intake_port")));
        be.serverTick();
        be.serverTick();
        h.assertTrue(be.items.getStackInSlot(0).is(Items.BEEF) && be.items.getStackInSlot(0).getCount() == 3,
            "An Item Intake Port pulls food from a neighbouring chest into the input group");
        var port = new ItemStack(item("item_output_port"));
        h.assertTrue(port.getCapability(zone.moddev.mc.lib.api.automation.AddonCapabilities.ADDON) != null,
            "Ports are MMDLib automation addons");
        h.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void xmlMachinesAndPortsAreInTheModsTab(GameTestHelper h) {
        net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess());
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id("materials"));
        for (String name : java.util.List.of("electric_oven", "electric_still", "plastic_refinery", "item_intake_port", "item_output_port"))
            h.assertTrue(tab.getDisplayItems().stream().anyMatch(stack -> stack.is(item(name))), "Electric Advantage's tab lists " + name);
        h.succeed();
    }

    private static int count(IItemHandler handler, Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++)
            if (handler.getStackInSlot(slot).is(item)) total += handler.getStackInSlot(slot).getCount();
        return total;
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void menusAndCraftingRecipes(GameTestHelper h) {
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var frame = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("poweradvantage:steel_frame")));
        var psu = new ItemStack(item("psu"));
        var plate = new ItemStack(Items.IRON_INGOT);
        var bucket = new ItemStack(Items.BUCKET);
        var gear = new ItemStack(Items.IRON_BARS);
        var inputs = java.util.Map.of(
            "electric_oven", java.util.List.of(psu, new ItemStack(Items.GLASS_PANE), psu, plate, frame, plate),
            "electric_still", java.util.List.of(psu, bucket, bucket, plate, frame, plate),
            "plastic_refinery", java.util.List.of(psu, new ItemStack(Items.PISTON), gear, plate, frame, plate));
        for (var entry : inputs.entrySet()) {
            h.assertTrue(MachineContent.definitions().containsKey(id(entry.getKey())), "XML machine loads: " + entry.getKey());
            var be = place(h, entry.getKey());
            var menu = new zone.moddev.mc.lib.gui.runtime.MachineMenu(1, player.getInventory(), be);
            // XML machine menus also hold each resource group's automation addon slots.
            h.assertTrue(menu.slots.size() >= be.items.getSlots() + 41,
                "Screen holds every machine slot and the player inventory: " + entry.getKey() + " has " + menu.slots.size());
            var input = net.minecraft.world.item.crafting.CraftingInput.of(3, entry.getValue().size() / 3, entry.getValue());
            var recipe = h.getLevel().getRecipeManager()
                .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
            h.assertTrue(recipe.value().assemble(input, h.getLevel().registryAccess()).is(item(entry.getKey())),
                "Machine crafting recipe: " + entry.getKey());
        }
        h.succeed();
    }

    static DefinedMachineEntity place(GameTestHelper h, String name) {
        h.setBlock(4, 3, 4, block(name));
        return (DefinedMachineEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(4, 3, 4)));
    }

    /** Ticks the machine {@code ticks} times, refilling its energy first each tick. */
    static void run(DefinedMachineEntity be, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            be.energy.setStored(be.energy.capacity());
            be.serverTick();
        }
    }

    private static IItemHandler items(GameTestHelper h) {
        return h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(new BlockPos(4, 3, 4)), null);
    }

    private static IFluidHandler fluids(GameTestHelper h) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(new BlockPos(4, 3, 4)), null);
    }

    /** An item storage as 3.0's ItemStorage saved it: {@code Items} entries with numeric {@code Slot}s. */
    static CompoundTag legacyItems(net.minecraft.core.HolderLookup.Provider registries, int size, Object... slotsAndStacks) {
        var list = new ListTag();
        for (int n = 0; n < slotsAndStacks.length; n += 2) {
            var entry = new CompoundTag();
            entry.putInt("Slot", (Integer) slotsAndStacks[n]);
            list.add(((ItemStack) slotsAndStacks[n + 1]).save(registries, entry));
        }
        var tag = new CompoundTag();
        tag.put("Items", list);
        tag.putInt("Size", size);
        return tag;
    }

    /** A one-tank fluid storage as 3.0 saved it. */
    private static CompoundTag legacyTank(net.minecraft.core.HolderLookup.Provider registries, FluidStack fluid) {
        var storage = new zone.moddev.mc.lib.api.capabilities.FluidStorage();
        storage.addTank(fluid.getAmount() * 2, zone.moddev.mc.lib.api.capabilities.Role.INPUT, stack -> true);
        storage.tank(0).setFluid(fluid);
        return storage.serializeNBT(registries);
    }

    static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID, name); }
    static Block block(String name) { return BuiltInRegistries.BLOCK.get(id(name)); }
    static Item item(String name) { return BuiltInRegistries.ITEM.get(id(name)); }
    private static Fluid fluid(String name) { return BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath("poweradvantage", name)); }
}
