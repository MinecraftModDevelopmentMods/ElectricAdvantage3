package zone.moddev.mc.electricadvantage.assembler;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.*;

/** Explicit assembler recipe: shaped or shapeless, with a count for each ingredient cell. */
public record AssemblerRecipe(List<String> pattern, Map<String,SizedIngredient> key,
        List<SizedIngredient> ingredients, ItemStack result, int time, int powerCost,
        Optional<ResourceLocation> override) implements Recipe<CraftingInput> {
    private static final Codec<SizedIngredient> SIZED = SizedIngredient.FLAT_CODEC.validate(value ->
            value.count() >= 1 && value.count() <= 64 ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult.error(() -> "Ingredient cell counts must be 1..64"));
    public static final MapCodec<AssemblerRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("pattern",List.of()).forGetter(AssemblerRecipe::pattern),
            Codec.unboundedMap(Codec.STRING,SIZED).optionalFieldOf("key",Map.of()).forGetter(AssemblerRecipe::key),
            SIZED.listOf().optionalFieldOf("ingredients",List.of()).forGetter(AssemblerRecipe::ingredients),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(AssemblerRecipe::result),
            Codec.intRange(1,1000000).fieldOf("time").forGetter(AssemblerRecipe::time),
            Codec.intRange(0,Integer.MAX_VALUE).fieldOf("power_cost").forGetter(AssemblerRecipe::powerCost),
            ResourceLocation.CODEC.optionalFieldOf("override").forGetter(AssemblerRecipe::override)
    ).apply(i,AssemblerRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,AssemblerRecipe> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    public AssemblerRecipe {
        pattern=List.copyOf(pattern);key=Map.copyOf(key);ingredients=List.copyOf(ingredients);result=result.copy();
        if(pattern.isEmpty()) {
            if(!key.isEmpty()||ingredients.isEmpty()||ingredients.size()>9)throw new IllegalArgumentException("Shapeless assembler recipes require 1..9 ingredients and no key");
        } else {
            if(!ingredients.isEmpty()||pattern.size()>3||pattern.getFirst().isEmpty()||pattern.getFirst().length()>3)
                throw new IllegalArgumentException("Shaped assembler recipes require a 1..3 by 1..3 pattern and no ingredients list");
            int width=pattern.getFirst().length();boolean nonempty=false;
            for(var row:pattern){
                if(row.length()!=width)throw new IllegalArgumentException("Assembler pattern rows must have equal width");
                for(char symbol:row.toCharArray())if(symbol!=' '){nonempty=true;if(!key.containsKey(String.valueOf(symbol)))throw new IllegalArgumentException("Undefined assembler key: "+symbol);}
            }
            for(var symbol:key.keySet())if(symbol.length()!=1||symbol.equals(" "))throw new IllegalArgumentException("Assembler keys must be single non-space characters");
            if(!nonempty)throw new IllegalArgumentException("Assembler pattern cannot be empty");
            int top=0,bottom=pattern.size()-1,left=width,right=-1;
            while(pattern.get(top).chars().allMatch(c->c==' '))top++;
            while(pattern.get(bottom).chars().allMatch(c->c==' '))bottom--;
            for(int y=top;y<=bottom;y++)for(int x=0;x<width;x++)if(pattern.get(y).charAt(x)!=' '){left=Math.min(left,x);right=Math.max(right,x);}
            var trimmed=new ArrayList<String>();for(int y=top;y<=bottom;y++)trimmed.add(pattern.get(y).substring(left,right+1));
            pattern=List.copyOf(trimmed);
        }
    }
    @Override public ItemStack result(){return result.copy();}
    public List<AssemblerCatalog.Cell> cells(){
        var cells=new ArrayList<AssemblerCatalog.Cell>();
        if(pattern.isEmpty())for(int i=0;i<ingredients.size();i++)cells.add(new AssemblerCatalog.Cell(i,ingredients.get(i).ingredient(),ingredients.get(i).count()));
        else for(int y=0;y<pattern.size();y++)for(int x=0;x<pattern.get(y).length();x++){
            var sized=key.get(String.valueOf(pattern.get(y).charAt(x)));
            if(sized!=null)cells.add(new AssemblerCatalog.Cell(y*3+x,sized.ingredient(),sized.count()));
        }
        return List.copyOf(cells);
    }
    public boolean matchesDisplay(CraftingInput input){return quantities(input,true)!=null;}
    @Override public boolean matches(CraftingInput input,Level level){return quantities(input,false)!=null;}
    private int[] quantities(CraftingInput input,boolean ignoreCounts){
        if(!pattern.isEmpty()){
            int width=pattern.getFirst().length(),height=pattern.size();
            if(input.width()!=width||input.height()!=height)return null;
            for(boolean mirror:new boolean[]{false,true}){
                int[] counts=new int[input.size()];boolean valid=true;
                for(int y=0;y<height&&valid;y++)for(int x=0;x<width;x++){
                    char symbol=pattern.get(y).charAt(mirror?width-1-x:x);
                    var requirement=key.get(String.valueOf(symbol));var stack=input.getItem(x,y);
                    if(requirement==null){if(!stack.isEmpty()){valid=false;break;}}
                    else if(!requirement.ingredient().test(stack)||!ignoreCounts&&stack.getCount()<requirement.count()){valid=false;break;}
                    else counts[y*width+x]=requirement.count();
                }
                if(valid)return counts;
            }
            return null;
        }
        if(input.ingredientCount()!=ingredients.size())return null;
        int[] counts=new int[input.size()];
        return matchShapeless(input,ignoreCounts,0,0,counts,new HashSet<>())?counts:null;
    }
    private boolean matchShapeless(CraftingInput input,boolean ignoreCounts,int index,int used,int[] counts,Set<Integer> failed){
        if(index==ingredients.size())return true;
        int state=index*512+used;if(failed.contains(state))return false;
        var ingredient=ingredients.get(index);
        for(int slot=0;slot<input.size();slot++)if((used&(1<<slot))==0&&ingredient.ingredient().test(input.getItem(slot))
                &&(ignoreCounts||input.getItem(slot).getCount()>=ingredient.count())){
            counts[slot]=ingredient.count();
            if(matchShapeless(input,ignoreCounts,index+1,used|(1<<slot),counts,failed))return true;
            counts[slot]=0;
        }
        failed.add(state);return false;
    }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input){
        var counts=quantities(input,false);var remaining=NonNullList.withSize(input.size(),ItemStack.EMPTY);
        if(counts==null)return remaining;
        for(int slot=0;slot<input.size();slot++)if(counts[slot]>0){
            var remainder=input.getItem(slot).copyWithCount(1).getCraftingRemainingItem();
            if(!remainder.isEmpty())remaining.set(slot,remainder.copyWithCount(Math.multiplyExact(remainder.getCount(),counts[slot])));
        }
        return remaining;
    }
    @Override public ItemStack assemble(CraftingInput input,HolderLookup.Provider registries){return result.copy();}
    @Override public ItemStack getResultItem(HolderLookup.Provider registries){return result.copy();}
    @Override public boolean canCraftInDimensions(int width,int height){return pattern.isEmpty()?width*height>=ingredients.size():width>=pattern.getFirst().length()&&height>=pattern.size();}
    @Override public NonNullList<Ingredient> getIngredients(){var list=NonNullList.<Ingredient>create();cells().forEach(cell->list.add(cell.ingredient()));return list;}
    // Machine-only recipe: keeps it out of the vanilla recipe book, which has no category for this type.
    @Override public boolean isSpecial(){return true;}
    @Override public RecipeSerializer<?> getSerializer(){return AssemblerSystems.SERIALIZER.get();}
    @Override public RecipeType<?> getType(){return AssemblerSystems.TYPE.get();}
}
