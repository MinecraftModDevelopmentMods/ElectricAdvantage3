package zone.moddev.mc.electricadvantage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import zone.moddev.mc.lib.common.recipes.CrushingRecipe;
import java.util.Set;
import zone.moddev.mc.lib.MMDLib;

import java.util.List;

@GameTestHolder(ElectricAdvantage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ComponentGameTests {
    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "empty")
    public static void componentsAndRecipes(GameTestHelper helper) {
        for (String path : List.of("blank_circuit_board", "control_circuit", "integrated_circuit", "psu",
                "silicon_ingot", "silicon_mix", "solder_mix", "solder", "lithium_ingot",
                "lithium_dust", "lithium_nugget", "sulfur_dust", "petrolplastic_ingot")) {
            var id = id(path);
            helper.assertTrue(MMDLib.itemDefinitions().containsKey(id), "Missing MMDLib definition: " + id);
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "Missing runtime item: " + id);
            helper.assertTrue(BuiltInRegistries.ITEM.get(id).getDefaultMaxStackSize() == 64,
                    "Incorrect stack limit: " + id);
        }
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        for (var pair : List.of(List.of("silicon_mix", "silicon_ingot"),
                List.of("solder_mix", "solder"), List.of("lithium_dust", "lithium_ingot"))) {
            var recipe = manager.getRecipeFor(RecipeType.SMELTING,
                    new SingleRecipeInput(stack(pair.getFirst())), level).orElseThrow();
            helper.assertTrue(recipe.value().getResultItem(level.registryAccess()).is(stack(pair.getLast()).getItem()),
                    "Wrong smelting output: " + pair);
        }
        var input = CraftingInput.of(1, 1, List.of(stack("lithium_ingot")));
        var recipe = manager.getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        ItemStack nuggets = recipe.value().assemble(input, level.registryAccess());
        helper.assertTrue(nuggets.is(stack("lithium_nugget").getItem()) && nuggets.getCount() == 9,
                "Lithium ingot must craft into nine nuggets");
        var reverse = CraftingInput.of(3, 3, java.util.Collections.nCopies(9, stack("lithium_nugget")));
        var reverseRecipe = manager.getRecipeFor(RecipeType.CRAFTING, reverse, level).orElseThrow();
        helper.assertTrue(reverseRecipe.value().assemble(reverse, level.registryAccess()).is(stack("lithium_ingot").getItem()),
                "Nine lithium nuggets must craft into one ingot");
        helper.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "empty")
    public static void restrictedMaterialsAndBattery(GameTestHelper helper) {
        var lithium = MMDLib.loadedMaterials().get(id("lithium")).content();
        var sulfur = MMDLib.loadedMaterials().get(id("sulfur")).content();
        helper.assertTrue(lithium.itemDefinitions().keySet().equals(Set.of(
                id("lithium_ingot"), id("lithium_dust"), id("lithium_nugget"), id("lithium_battery"), id("lithium_ore"))),
                "Lithium must generate its four item parts plus the ore block item: " + lithium.itemDefinitions().keySet());
        helper.assertTrue(sulfur.itemDefinitions().keySet().equals(Set.of(id("sulfur_dust"), id("sulfur_ore"))),
                "Sulfur must generate only dust and the ore block item");
        helper.assertTrue(lithium.blockDefinitions().keySet().equals(Set.of(id("lithium_ore")))
                && sulfur.blockDefinitions().keySet().equals(Set.of(id("sulfur_ore"))),
                "Each material must generate only an ore block");
        helper.assertTrue(lithium.entities().isEmpty() && sulfur.entities().isEmpty()
                && lithium.fluid() == null && sulfur.fluid() == null, "No entities or fluids may be generated");
        var battery = stack("lithium_battery");
        helper.assertTrue(battery.getMaxStackSize() == 1, "Battery must not stack");
        var energy = battery.getCapability(Capabilities.EnergyStorage.ITEM);
        helper.assertTrue(energy != null && energy.getEnergyStored() == 8000, "New lithium battery starts full");
        helper.assertTrue(energy.extractEnergy(3000, true) == 3000 && energy.getEnergyStored() == 8000,
                "Simulated extraction must not alter charge");
        helper.assertTrue(energy.extractEnergy(3000, false) == 3000, "Battery discharge");
        var saved = battery.save(helper.getLevel().registryAccess());
        var restored = ItemStack.parse(helper.getLevel().registryAccess(), saved).orElseThrow();
        var restoredEnergy = restored.getCapability(Capabilities.EnergyStorage.ITEM);
        helper.assertTrue(restoredEnergy.getEnergyStored() == 5000, "Charge persists across serialization");
        helper.assertTrue(restoredEnergy.receiveEnergy(Integer.MAX_VALUE, true) == 3000
                && restoredEnergy.getEnergyStored() == 5000, "Simulated charging must not alter charge");
        helper.assertTrue(restoredEnergy.receiveEnergy(Integer.MAX_VALUE, false) == 3000
                && restoredEnergy.getEnergyStored() == 8000, "Charging clamps at capacity");
        helper.assertTrue(restoredEnergy.extractEnergy(Integer.MAX_VALUE, false) == 8000
                && restoredEnergy.extractEnergy(-1, false) == 0
                && restoredEnergy.receiveEnergy(-1, false) == 0, "Energy bounds and negative transfer protection");
        helper.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "empty")
    public static void oresAndCrushing(GameTestHelper helper) {
        var level = helper.getLevel();
        for (var parts : List.of(List.of("lithium_ore", "lithium_dust", "2"),
                List.of("lithium_ingot", "lithium_dust", "1"), List.of("sulfur_ore", "sulfur_dust", "4"))) {
            var recipe = level.getRecipeManager().getRecipeFor(CrushingRecipe.TYPE.get(),
                    new SingleRecipeInput(stack(parts.getFirst())), level).orElseThrow();
            var result = recipe.value().getResultItem(level.registryAccess());
            helper.assertTrue(result.is(stack(parts.get(1)).getItem()) && result.getCount() == Integer.parseInt(parts.get(2)),
                    "Wrong crushing output: " + parts);
        }
        for (String mineral : List.of("lithium", "sulfur")) {
            var block = BuiltInRegistries.BLOCK.get(id(mineral + "_ore"));
            var drops = Block.getDrops(block.defaultBlockState(), level, helper.absolutePos(BlockPos.ZERO),
                    null, null, new ItemStack(Items.IRON_PICKAXE));
            helper.assertTrue(drops.size() == 1, "Ore must have a loaded loot table: " + mineral);
            helper.assertTrue(drops.getFirst().is(stack(mineral.equals("sulfur") ? "sulfur_dust" : "lithium_ore").getItem()),
                    "Wrong ore drop: " + mineral);
            helper.assertTrue(drops.getFirst().getCount() >= 1 && drops.getFirst().getCount() <= (mineral.equals("sulfur") ? 4 : 1),
                    "Wrong ore drop count: " + mineral);
        }
        var smelting = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                new SingleRecipeInput(stack("lithium_ore")), level).orElseThrow();
        helper.assertTrue(smelting.value().getResultItem(level.registryAccess()).is(stack("lithium_ingot").getItem()),
                "Lithium ore must smelt to lithium ingot");
        helper.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "empty")
    public static void allBatteryTiers(GameTestHelper helper) {
        var capacities = java.util.Map.of("lead_acid_battery", 1000, "nickel_hydride_battery", 2000,
                "alkaline_battery", 4000, "lithium_battery", 8000);
        for (var tier : capacities.entrySet()) {
            var battery = stack(tier.getKey());
            int capacity = tier.getValue();
            helper.assertTrue(battery.getMaxStackSize() == 1, "Unstackable: " + tier.getKey());
            var energy = battery.getCapability(Capabilities.EnergyStorage.ITEM);
            var otherHandle = battery.getCapability(Capabilities.EnergyStorage.ITEM);
            helper.assertTrue(energy != null && energy.getMaxEnergyStored() == capacity
                    && energy.getEnergyStored() == capacity, "Full initial charge: " + tier.getKey());
            helper.assertTrue(energy.extractEnergy(capacity / 2, true) == capacity / 2
                    && energy.getEnergyStored() == capacity, "Extraction simulation: " + tier.getKey());
            energy.extractEnergy(capacity / 2, false);
            helper.assertTrue(otherHandle.getEnergyStored() == capacity / 2,
                    "Capability handles share stack charge: " + tier.getKey());
            var copy = battery.copy();
            var copiedEnergy = copy.getCapability(Capabilities.EnergyStorage.ITEM);
            copiedEnergy.extractEnergy(Integer.MAX_VALUE, false);
            helper.assertTrue(energy.getEnergyStored() == capacity / 2 && copiedEnergy.getEnergyStored() == 0,
                    "Copied stacks have independent charge: " + tier.getKey());
            var restored = ItemStack.parse(helper.getLevel().registryAccess(), copy.save(helper.getLevel().registryAccess())).orElseThrow();
            var restoredEnergy = restored.getCapability(Capabilities.EnergyStorage.ITEM);
            helper.assertTrue(restoredEnergy.getEnergyStored() == 0, "Empty charge stays empty after reload: " + tier.getKey());
            helper.assertTrue(restoredEnergy.receiveEnergy(Integer.MAX_VALUE, true) == capacity
                    && restoredEnergy.getEnergyStored() == 0, "Charging simulation: " + tier.getKey());
            helper.assertTrue(restoredEnergy.receiveEnergy(Integer.MAX_VALUE, false) == capacity
                    && restoredEnergy.receiveEnergy(1, false) == 0, "Capacity bound: " + tier.getKey());
            helper.assertTrue(restoredEnergy.receiveEnergy(-1, false) == 0
                    && restoredEnergy.extractEnergy(-1, false) == 0, "Negative amounts refused: " + tier.getKey());
            var sink = new zone.moddev.mc.lib.api.capabilities.EnergyBuffer(250, 250);
            int accepted = sink.receiveEnergy(restoredEnergy.extractEnergy(capacity, true), true);
            sink.receiveEnergy(restoredEnergy.extractEnergy(accepted, false), false);
            helper.assertTrue(sink.stored() == 250 && restoredEnergy.getEnergyStored() == capacity - 250,
                    "Transfer to MMDLib energy buffer conserves energy: " + tier.getKey());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "empty")
    public static void batteryCraftingAndBucketRemainders(GameTestHelper helper) {
        // Test-only common tags use vanilla ingredients to exercise recipes without
        // making an external metals mod a required production dependency.
        var inputs = java.util.Map.of(
                "lead_acid_battery", List.of(new ItemStack(Items.IRON_INGOT), stack("sulfur_dust"), new ItemStack(Items.WATER_BUCKET)),
                "nickel_hydride_battery", List.of(new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.REDSTONE), new ItemStack(Items.WATER_BUCKET)),
                "alkaline_battery", List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GUNPOWDER), new ItemStack(Items.GOLD_INGOT)),
                "lithium_battery", List.of(stack("lithium_ingot"), new ItemStack(Items.REDSTONE), new ItemStack(Items.CHARCOAL)));
        var level = helper.getLevel();
        for (var entry : inputs.entrySet()) {
            var plastic = stack("petrolplastic_ingot");
            var parts = entry.getValue();
            var input = CraftingInput.of(3, 3, List.of(plastic.copy(), parts.get(0), plastic.copy(),
                    plastic.copy(), parts.get(1), plastic.copy(), plastic.copy(), parts.get(2), plastic.copy()));
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            var result = recipe.value().assemble(input, level.registryAccess());
            helper.assertTrue(result.is(stack(entry.getKey()).getItem()) && result.getCount() == 1,
                    "Battery recipe output: " + entry.getKey());
            var energy = result.getCapability(Capabilities.EnergyStorage.ITEM);
            helper.assertTrue(energy.getEnergyStored() == energy.getMaxEnergyStored(), "Crafted battery starts full");
            if (parts.get(2).is(Items.WATER_BUCKET)) {
                helper.assertTrue(recipe.value().getRemainingItems(input).get(7).is(Items.BUCKET),
                        "Battery crafting must return the empty water bucket: " + entry.getKey());
            }
        }
        helper.succeed();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID, path);
    }

    private static ItemStack stack(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(id(path)));
    }
}
