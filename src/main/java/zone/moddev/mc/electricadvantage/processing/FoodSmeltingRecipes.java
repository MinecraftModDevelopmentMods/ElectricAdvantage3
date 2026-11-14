package zone.moddev.mc.electricadvantage.processing;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeBuilder;
import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeGenerator;
import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeOutput;
import zone.moddev.mc.lib.machine.recipe.generation.RecipeGenerationContext;

/**
 * Recipe generator for the Electric Oven: every smelting recipe whose result is food, at a fixed cooking time and
 * energy cost per tick (the oven ignores the furnace's cooking time).
 *
 * <p>Properties, all optional: {@code time} (default 100 ticks), {@code energy-per-tick} (default 4 FE), and the
 * {@code input-group}, {@code output-group} and {@code energy-group} names (default {@code input}, {@code output},
 * {@code energy}). Generated IDs are {@code <type namespace>:generated/<type path>/<source namespace>/<source path>}.
 */
public final class FoodSmeltingRecipes implements MachineRecipeGenerator {
    @Override
    public void generate(RecipeGenerationContext context, MachineRecipeOutput output) {
        var properties = context.properties();
        properties.requireOnly("input-group", "output-group", "energy-group", "time", "energy-per-tick");
        String input = properties.string("input-group", "input");
        String result = properties.string("output-group", "output");
        String energy = properties.string("energy-group", "energy");
        int time = properties.integer("time", 100, 1, 1_000_000);
        long perTick = properties.longValue("energy-per-tick", 4, 1, 1_000_000);
        var type = context.recipeType().id();
        for (var holder : context.recipes()) {
            if (!(holder.value() instanceof SmeltingRecipe recipe)) continue;
            var product = recipe.getResultItem(context.registries());
            if (product.isEmpty() || !product.has(DataComponents.FOOD) || recipe.getIngredients().size() != 1) continue;
            var id = ResourceLocation.fromNamespaceAndPath(type.getNamespace(),
                "generated/" + type.getPath() + "/" + holder.id().getNamespace() + "/" + holder.id().getPath());
            output.accept(id, MachineRecipeBuilder.recipe(context.recipeType())
                .inputItems(input, recipe.getIngredients().getFirst(), 1)
                .inputEnergy(energy, perTick * time)
                .outputItem(result, product.copy())
                .time(time)
                .build());
        }
    }
}
