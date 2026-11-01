package zone.moddev.mc.electricadvantage.farming;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;

/** Growth chamber recipe: a catalyst seed on a catalyst soil yields rolled results every cycle. */
public record GrowingRecipe(Ingredient seed, Ingredient soil, int time, List<Result> results) implements Recipe<GrowingRecipe.Input> {
    public record Input(ItemStack seed, ItemStack soil) implements RecipeInput {
        @Override public ItemStack getItem(int index){return index==0?seed:index==1?soil:ItemStack.EMPTY;}
        @Override public int size(){return 2;}
    }
    /**
     * One result entry. {@code chance} gates the entry; each soil fertility level adds
     * {@code chance_per_fertility} to it and one roll of {@code bonus_per_fertility} for +1 item.
     * An {@code upgrade} entry drops only while a matching chamber upgrade is installed.
     */
    public record Result(Optional<ItemStack> item, boolean copySeed, int count, float chance,
            float chancePerFertility, float bonusPerFertility, Optional<String> upgrade) {
        private static final Codec<Float> UNIT=Codec.floatRange(0,1);
        private static final Codec<String> UPGRADE=Codec.STRING.validate(s->s.matches("[a-z0-9_/.-]+")?DataResult.success(s):DataResult.error(()->"Invalid upgrade name: "+s));
        public static final Codec<Result> CODEC=RecordCodecBuilder.<Result>create(i->i.group(
                ItemStack.STRICT_CODEC.optionalFieldOf("item").forGetter(Result::item),
                Codec.BOOL.optionalFieldOf("copy_seed",false).forGetter(Result::copySeed),
                Codec.intRange(1,64).optionalFieldOf("count",1).forGetter(Result::count),
                UNIT.optionalFieldOf("chance",1f).forGetter(Result::chance),
                UNIT.optionalFieldOf("chance_per_fertility",0f).forGetter(Result::chancePerFertility),
                UNIT.optionalFieldOf("bonus_per_fertility",0f).forGetter(Result::bonusPerFertility),
                UPGRADE.optionalFieldOf("upgrade").forGetter(Result::upgrade)
        ).apply(i,Result::new)).validate(r->r.item().isPresent()!=r.copySeed()?DataResult.success(r)
                :DataResult.error(()->"A growing result needs exactly one of \"item\" or \"copy_seed\""));
        public Result { item=item.map(ItemStack::copy); }
        public TagKey<Item> upgradeTag(){return upgrade.map(name->TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("electricadvantage","growth_chamber_upgrades/"+name))).orElse(null);}
        /**
         * Rolls this entry; {@code item} entries keep their own count, {@code copy_seed} entries use {@code count}.
         * The soil's yield then scales the count, and its fractional part is the chance of one more.
         */
        public ItemStack roll(ItemStack seed,int fertility,float yield,RandomSource random){
            if(random.nextFloat()>=Math.min(1f,chance+chancePerFertility*fertility))return ItemStack.EMPTY;
            var stack=item.map(ItemStack::copy).orElseGet(()->seed.copyWithCount(count));
            int amount=stack.getCount();
            for(int n=0;n<fertility;n++)if(random.nextFloat()<bonusPerFertility)amount++;
            float scaled=amount*yield;
            amount=(int)scaled;
            if(random.nextFloat()<scaled-amount)amount++;
            return amount>0?stack.copyWithCount(amount):ItemStack.EMPTY;
        }
    }
    public static final MapCodec<GrowingRecipe> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("seed").forGetter(GrowingRecipe::seed),
            Ingredient.CODEC_NONEMPTY.fieldOf("soil").forGetter(GrowingRecipe::soil),
            Codec.intRange(1,1000000).fieldOf("time").forGetter(GrowingRecipe::time),
            Codec.list(Result.CODEC,1,16).fieldOf("results").forGetter(GrowingRecipe::results)
    ).apply(i,GrowingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,GrowingRecipe> STREAM_CODEC=ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    public GrowingRecipe { results=List.copyOf(results); }
    /** Rolls every entry, skipping upgrade entries the installed upgrade does not grant. */
    public List<ItemStack> harvest(ItemStack seed,int fertility,float yield,ItemStack upgrade,RandomSource random){
        var harvest=new ArrayList<ItemStack>();
        for(var result:results){
            var tag=result.upgradeTag();
            if(tag!=null&&!upgrade.is(tag))continue;
            var stack=result.roll(seed,fertility,yield,random);
            while(!stack.isEmpty()){int part=Math.min(stack.getCount(),stack.getMaxStackSize());harvest.add(stack.copyWithCount(part));stack.shrink(part);}
        }
        return harvest;
    }
    @Override public boolean matches(Input input,Level level){return seed.test(input.seed())&&soil.test(input.soil());}
    @Override public ItemStack assemble(Input input,HolderLookup.Provider registries){return getResultItem(registries);}
    @Override public ItemStack getResultItem(HolderLookup.Provider registries){
        return results.stream().filter(r->r.item().isPresent()).findFirst().map(r->r.item().get().copy()).orElse(ItemStack.EMPTY);
    }
    @Override public boolean canCraftInDimensions(int width,int height){return true;}
    @Override public NonNullList<Ingredient> getIngredients(){return NonNullList.of(Ingredient.EMPTY,seed,soil);}
    // Machine-only recipe: keeps it out of the vanilla recipe book, which has no category for this type.
    @Override public boolean isSpecial(){return true;}
    @Override public RecipeSerializer<?> getSerializer(){return FarmingSystems.SERIALIZER.get();}
    @Override public RecipeType<?> getType(){return FarmingSystems.TYPE.get();}
}
