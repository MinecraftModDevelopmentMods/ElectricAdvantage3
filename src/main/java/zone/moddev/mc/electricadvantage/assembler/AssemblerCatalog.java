package zone.moddev.mc.electricadvantage.assembler;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.security.MessageDigest;
import java.util.*;

/** Additive assembler catalog; it never edits the source crafting recipe manager. */
public final class AssemblerCatalog {
    public record Cell(int gridIndex,Ingredient ingredient,int count){}
    public record Entry(ResourceLocation id,ResourceLocation source,Recipe<CraftingInput> recipe,boolean imported){
        public int time(AssemblerSettings settings){return imported?settings.defaultTime():((AssemblerRecipe)recipe).time();}
        public int power(AssemblerSettings settings){return imported?settings.defaultPower():((AssemblerRecipe)recipe).powerCost();}
        public long feCost(AssemblerSettings settings){return (long)power(settings)*settings.fePerElectricity();}
        public List<Cell> cells(){
            if(recipe instanceof AssemblerRecipe explicit)return explicit.cells();
            var cells=new ArrayList<Cell>();var ingredients=recipe.getIngredients();
            int width=recipe instanceof ShapedRecipe shaped?shaped.getWidth():3;
            for(int slot=0;slot<ingredients.size();slot++)if(!ingredients.get(slot).isEmpty())cells.add(new Cell((slot/width)*3+slot%width,ingredients.get(slot),1));
            return List.copyOf(cells);
        }
        public boolean matchesDisplay(CraftingInput input,Level level){return recipe instanceof AssemblerRecipe explicit?explicit.matchesDisplay(input):recipe.matches(input,level);}
    }
    public record Snapshot(long revision,List<Entry> entries,Map<ResourceLocation,Entry> byId){}
    private static final Map<RecipeManager,Snapshot> SNAPSHOTS=new WeakHashMap<>();
    private static long revision;
    private AssemblerCatalog(){}
    public static ResourceLocation importedId(ResourceLocation source){return ResourceLocation.fromNamespaceAndPath("electricadvantage","imported/"+source.getNamespace()+"/"+source.getPath());}
    public static synchronized Snapshot get(RecipeManager manager){return SNAPSHOTS.computeIfAbsent(manager,AssemblerCatalog::build);}
    public static synchronized Snapshot rebuild(RecipeManager manager){var snapshot=build(manager);SNAPSHOTS.put(manager,snapshot);return snapshot;}
    public static synchronized void invalidate(RecipeManager manager){SNAPSHOTS.remove(manager);}
    public static synchronized void clear(){SNAPSHOTS.clear();}
    private static Snapshot build(RecipeManager manager){
        var entries=new HashMap<ResourceLocation,Entry>();
        var overridden=new HashSet<ResourceLocation>();
        var explicit=new ArrayList<>(manager.getAllRecipesFor(AssemblerSystems.TYPE.get()));
        explicit.sort(Comparator.comparing(holder->holder.id().toString()));
        for(var holder:explicit)holder.value().override().ifPresent(source->{
            if(!overridden.add(source))throw new IllegalArgumentException("Multiple assembler overrides for "+source);
        });
        for(var holder:manager.getAllRecipesFor(RecipeType.CRAFTING)){
            var recipe=holder.value();
            if(holder.id().getPath().endsWith("_manual_only")||overridden.contains(holder.id())||!recipe.canCraftInDimensions(3,3))continue;
            if(recipe.getSerializer()!=RecipeSerializer.SHAPED_RECIPE&&recipe.getSerializer()!=RecipeSerializer.SHAPELESS_RECIPE)continue;
            var id=importedId(holder.id());entries.put(id,new Entry(id,holder.id(),recipe,true));
        }
        // Authored assembler recipes always win, including an exact imported-ID collision.
        for(var holder:explicit)entries.put(holder.id(),new Entry(holder.id(),holder.value().override().orElse(holder.id()),holder.value(),false));
        var sorted=new ArrayList<>(entries.values());sorted.sort(Comparator.comparing(Entry::imported).thenComparing(e->e.id().toString()));
        return new Snapshot(++revision,List.copyOf(sorted),Map.copyOf(entries));
    }
    public static String fingerprint(Entry entry,RegistryAccess registries,AssemblerSettings settings){
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),registries);
        try{
            Recipe.STREAM_CODEC.encode(buffer,entry.recipe());buffer.writeVarInt(entry.time(settings));buffer.writeLong(entry.feCost(settings));
            return hash(buffer);
        }finally{buffer.release();}
    }
    public static String workFingerprint(String recipe,List<ItemStack> grid,RegistryAccess registries){
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),registries);
        try{buffer.writeUtf(recipe);for(var stack:grid)ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer,stack);return hash(buffer);}finally{buffer.release();}
    }
    private static String hash(RegistryFriendlyByteBuf buffer){
        try{var digest=MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[buffer.readableBytes()];buffer.getBytes(buffer.readerIndex(),bytes);return HexFormat.of().formatHex(digest.digest(bytes));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
