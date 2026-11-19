package zone.moddev.mc.electricadvantage.processing;

import zone.moddev.mc.lib.common.recipes.CrushingRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeBuilder;
import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeGenerator;
import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeOutput;
import zone.moddev.mc.lib.machine.recipe.generation.RecipeGenerationContext;

/**
 * Recipe generator for the Electric Rock Crusher: a copy of every MMDLib {@code mmdlib:crushing} recipe at a fixed time
 * and energy cost per tick. Recipes added or removed by datapacks or scripts reach the crusher on the next reload.
 *
 * <p>Properties, all optional: {@code time} (default 200 ticks), {@code energy-per-tick} (default 12 FE), and the
 * {@code input-group}, {@code output-group} and {@code energy-group} names (default {@code input}, {@code output},
 * {@code energy}). Generated IDs are {@code <type namespace>:generated/<type path>/<source namespace>/<source path>}.
 */
public final class ElectricCrushingRecipes implements MachineRecipeGenerator {
    @Override
    public void generate(RecipeGenerationContext context, MachineRecipeOutput output) {
        var properties = context.properties();
        properties.requireOnly("input-group", "output-group", "energy-group", "time", "energy-per-tick");
        String input = properties.string("input-group", "input");
        String result = properties.string("output-group", "output");
        String energy = properties.string("energy-group", "energy");
        int time = properties.integer("time", 200, 1, 1_000_000);
        long perTick = properties.longValue("energy-per-tick", 12, 1, 1_000_000);
        var type = context.recipeType().id();
        for (var holder : context.recipes()) {
            if (!(holder.value() instanceof CrushingRecipe recipe)) continue;
            var product = recipe.getResultItem(context.registries());
            if (product.isEmpty()) continue;
            output.accept(GeneratedIds.copy(type, holder.id()), MachineRecipeBuilder.recipe(context.recipeType())
                .inputItems(input, recipe.getIngredients().getFirst(), 1)
                .inputEnergy(energy, perTick * time)
                .outputItem(result, product.copy())
                .time(time)
                .build());
        }
    }
}
