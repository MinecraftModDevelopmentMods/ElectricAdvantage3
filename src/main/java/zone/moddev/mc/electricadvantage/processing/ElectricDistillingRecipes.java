package zone.moddev.mc.electricadvantage.processing;

import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.recipe.MachineProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeBuilder;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;
import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeGenerator;
import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeOutput;
import zone.moddev.mc.lib.machine.recipe.generation.RecipeGenerationContext;

/**
 * Recipe generator for the Electric Still: a copy of every Power Advantage {@code poweradvantage:distilling} recipe,
 * plus an energy cost per tick. Recipes added to or removed from Power Advantage's type by datapacks or scripts reach
 * the Electric Still on the next reload.
 *
 * <p>Only fluid-to-fluid recipes using Power Advantage's {@code input} and {@code output} tank groups are copied;
 * anything else would not fit the still's tanks and would fail the reload, so it is skipped. Properties, all optional:
 * {@code energy-per-tick} (default 16 FE) and {@code energy-group} (default {@code energy}). Generated IDs are
 * {@code <type namespace>:generated/<type path>/<source namespace>/<source path>}.
 */
public final class ElectricDistillingRecipes implements MachineRecipeGenerator {
    /** Power Advantage's distilling recipe type, read by this generator. */
    public static final ResourceLocation DISTILLING = ResourceLocation.parse("poweradvantage:distilling");

    @Override
    public void generate(RecipeGenerationContext context, MachineRecipeOutput output) {
        var properties = context.properties();
        properties.requireOnly("energy-group", "energy-per-tick");
        String energy = properties.string("energy-group", "energy");
        long perTick = properties.longValue("energy-per-tick", 16, 1, 1_000_000);
        var type = context.recipeType().id();
        for (var holder : context.recipes()) {
            if (!(holder.value() instanceof MachineRecipeEntry entry) || !entry.type().id().equals(DISTILLING)) continue;
            var recipe = entry.recipe();
            if (!fitsStill(recipe)) continue;
            var builder = MachineRecipeBuilder.recipe(context.recipeType());
            recipe.inputs().forEach((group, parts) -> parts.forEach(part -> builder.input(group, part)));
            recipe.outputs().forEach((group, products) -> products.forEach(product -> builder.output(group, product)));
            output.accept(GeneratedIds.copy(type, holder.id()), builder.inputEnergy(energy, perTick * recipe.time()).time(recipe.time()).build());
        }
    }

    /** Whether every input is a fluid in group {@code input} and every product a fluid in group {@code output}. */
    private static boolean fitsStill(MachineRecipe recipe) {
        return Set.of("input").containsAll(recipe.inputs().keySet())
            && Set.of("output").containsAll(recipe.outputs().keySet())
            && recipe.inputs().values().stream().flatMap(List::stream).allMatch(MachineIngredient.FluidPart.class::isInstance)
            && recipe.outputs().values().stream().flatMap(List::stream).allMatch(MachineProduct.FluidProduct.class::isInstance);
    }
}
