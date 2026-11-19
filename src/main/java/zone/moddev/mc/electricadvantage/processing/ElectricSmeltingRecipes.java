package zone.moddev.mc.electricadvantage.processing;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeBuilder;
import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeGenerator;
import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeOutput;
import zone.moddev.mc.lib.machine.recipe.generation.RecipeGenerationContext;

/**
 * Recipe generator for the Electric Oven and Electric Arc Furnace: a copy of the furnace's smelting recipes at a fixed
 * time and energy cost per tick (these machines ignore the furnace's cooking time). Recipes added or removed by
 * datapacks or scripts reach the machines on the next reload.
 *
 * <p>Properties, all optional: {@code products} ({@code all}, the default, or {@code food} for recipes whose result is
 * food), {@code time} (default 100 ticks), {@code energy-per-tick} (default 4 FE), and the {@code input-group},
 * {@code output-group} and {@code energy-group} names (default {@code input}, {@code output}, {@code energy}).
 * Generated IDs are {@code <type namespace>:generated/<type path>/<source namespace>/<source path>}.
 */
public final class ElectricSmeltingRecipes implements MachineRecipeGenerator {
    @Override
    public void generate(RecipeGenerationContext context, MachineRecipeOutput output) {
        var properties = context.properties();
        properties.requireOnly("products", "input-group", "output-group", "energy-group", "time", "energy-per-tick");
        boolean foodOnly = switch (properties.string("products", "all")) {
            case "all" -> false;
            case "food" -> true;
            default -> throw properties.error("products must be all or food");
        };
        String input = properties.string("input-group", "input");
        String result = properties.string("output-group", "output");
        String energy = properties.string("energy-group", "energy");
        int time = properties.integer("time", 100, 1, 1_000_000);
        long perTick = properties.longValue("energy-per-tick", 4, 1, 1_000_000);
        var type = context.recipeType().id();
        for (var holder : context.recipes()) {
            if (!(holder.value() instanceof SmeltingRecipe recipe) || recipe.getIngredients().size() != 1) continue;
            var product = recipe.getResultItem(context.registries());
            if (product.isEmpty() || foodOnly && !product.has(DataComponents.FOOD)) continue;
            output.accept(GeneratedIds.copy(type, holder.id()), MachineRecipeBuilder.recipe(context.recipeType())
                .inputItems(input, recipe.getIngredients().getFirst(), 1)
                .inputEnergy(energy, perTick * time)
                .outputItem(result, product.copy())
                .time(time)
                .build());
        }
    }
}
