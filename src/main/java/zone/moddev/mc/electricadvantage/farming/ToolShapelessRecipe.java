package zone.moddev.mc.electricadvantage.farming;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.List;

/**
 * Shapeless crafting that hands damageable ingredients (tools) back with one more point of damage
 * instead of using them up; a tool on its last point breaks. Used for dirt + hoe to farmland.
 */
public record ToolShapelessRecipe(CraftingBookCategory category,ItemStack result,NonNullList<Ingredient> ingredients) implements CraftingRecipe {
    public static final MapCodec<ToolShapelessRecipe> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(ToolShapelessRecipe::category),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ToolShapelessRecipe::result),
            Codec.list(Ingredient.CODEC_NONEMPTY,1,9).<NonNullList<Ingredient>>xmap(list->NonNullList.of(Ingredient.EMPTY,list.toArray(Ingredient[]::new)),List::copyOf)
                    .fieldOf("ingredients").forGetter(ToolShapelessRecipe::ingredients)
    ).apply(i,ToolShapelessRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,ToolShapelessRecipe> STREAM_CODEC=ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    @Override public boolean matches(CraftingInput input,Level level){
        if(input.ingredientCount()!=ingredients.size())return false;
        return input.size()==1&&ingredients.size()==1?ingredients.getFirst().test(input.getItem(0)):input.stackedContents().canCraft(this,null);
    }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input){
        var remaining=NonNullList.withSize(input.size(),ItemStack.EMPTY);
        for(int slot=0;slot<input.size();slot++){
            var stack=input.getItem(slot);
            if(stack.isDamageableItem()){
                var tool=stack.copyWithCount(1);tool.setDamageValue(tool.getDamageValue()+1);
                remaining.set(slot,tool.getDamageValue()>=tool.getMaxDamage()?ItemStack.EMPTY:tool);
            }else if(stack.hasCraftingRemainingItem())remaining.set(slot,stack.getCraftingRemainingItem());
        }
        return remaining;
    }
    @Override public ItemStack assemble(CraftingInput input,HolderLookup.Provider registries){return result.copy();}
    @Override public boolean canCraftInDimensions(int width,int height){return width*height>=ingredients.size();}
    @Override public ItemStack getResultItem(HolderLookup.Provider registries){return result;}
    @Override public NonNullList<Ingredient> getIngredients(){return ingredients;}
    @Override public RecipeSerializer<?> getSerializer(){return FarmingSystems.TOOL_SHAPELESS.get();}
}
