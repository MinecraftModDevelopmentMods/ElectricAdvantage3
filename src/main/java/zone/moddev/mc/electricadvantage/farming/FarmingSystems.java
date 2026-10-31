package zone.moddev.mc.electricadvantage.farming;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import zone.moddev.mc.lib.api.*;
import zone.moddev.mc.lib.common.factories.BlockEntityFactory;
import zone.moddev.mc.lib.common.types.*;

public final class FarmingSystems {
    /** Item data map: a soil's fertility level; soils without an entry have fertility 0. */
    public record SoilFertility(int fertility){
        public static final Codec<SoilFertility> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.intRange(0,100).fieldOf("fertility").forGetter(SoilFertility::fertility)).apply(i,SoilFertility::new));
    }
    /** Item data map: greenhouse fertilizer units supplied by one item. */
    public record Fertilizer(int value){
        public static final Codec<Fertilizer> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.intRange(1,1000000).fieldOf("value").forGetter(Fertilizer::value)).apply(i,Fertilizer::new));
    }
    public static final DataMapType<Item,SoilFertility> SOIL_FERTILITY=DataMapType.builder(id("soil_fertility"),Registries.ITEM,SoilFertility.CODEC).synced(SoilFertility.CODEC,false).build();
    public static final DataMapType<Item,Fertilizer> FERTILIZER=DataMapType.builder(id("fertilizer"),Registries.ITEM,Fertilizer.CODEC).synced(Fertilizer.CODEC,false).build();
    public static final TagKey<Item> UPGRADES=TagKey.create(Registries.ITEM,id("growth_chamber_upgrades"));

    private static final DeferredRegister<RecipeType<?>> TYPES=DeferredRegister.create(Registries.RECIPE_TYPE,"electricadvantage");
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,"electricadvantage");
    public static final DeferredHolder<RecipeType<?>,RecipeType<GrowingRecipe>> TYPE=TYPES.register("growing",()->new RecipeType<>(){
        @Override public String toString(){return "electricadvantage:growing";}
    });
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<GrowingRecipe>> SERIALIZER=SERIALIZERS.register("growing",()->new RecipeSerializer<>(){
        @Override public MapCodec<GrowingRecipe> codec(){return GrowingRecipe.CODEC;}
        @Override public StreamCodec<RegistryFriendlyByteBuf,GrowingRecipe> streamCodec(){return GrowingRecipe.STREAM_CODEC;}
    });
    private FarmingSystems(){}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("electricadvantage",path);}
    public static int fertility(ItemStack soil){var data=soil.getItemHolder().getData(SOIL_FERTILITY);return data==null?0:data.fertility();}
    public static int fertilizer(ItemStack stack){if(stack.isEmpty())return 0;var data=stack.getItemHolder().getData(FERTILIZER);return data==null?0:data.value();}

    public static void register(IEventBus bus){
        TYPES.register(bus);SERIALIZERS.register(bus);
        bus.addListener((RegisterDataMapTypesEvent event)->{event.register(SOIL_FERTILITY);event.register(FERTILIZER);});
        var chamber=id("growth_chamber");
        MaterialExtensions.GLOBAL.registerBlockImplementation(chamber,new BlockImplementation<GrowthChamberBlock>(){
            @Override public Class<GrowthChamberBlock> blockClass(){return GrowthChamberBlock.class;}
            @Override public GrowthChamberBlock create(MMDMaterial material,MMDBlock block){throw new UnsupportedOperationException("BlockContext required");}
            @Override public GrowthChamberBlock create(BlockContext context){return new GrowthChamberBlock(context.properties(),context.blockEntity().orElseThrow().as(GrowthChamberBlockEntity.class));}
        });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(chamber,new BlockEntityFactory<GrowthChamberBlockEntity,GrowthChamberSettings>(){
            @Override public Class<GrowthChamberBlockEntity> blockEntityClass(){return GrowthChamberBlockEntity.class;}
            @Override public Codec<GrowthChamberSettings> settingsCodec(){return GrowthChamberSettings.CODEC;}
            @Override public GrowthChamberBlockEntity create(BlockEntityType<GrowthChamberBlockEntity> type,BlockPos pos,BlockState state,MMDBlockEntity definition,GrowthChamberSettings settings){return new GrowthChamberBlockEntity(type,pos,state,settings);}
            @Override public BlockEntityTicker<GrowthChamberBlockEntity> ticker(Level level,MMDBlockEntity definition,GrowthChamberSettings settings){return level.isClientSide?null:GrowthChamberBlockEntity::tick;}
        });
        var controller=id("greenhouse_controller");
        MaterialExtensions.GLOBAL.registerBlockImplementation(controller,new BlockImplementation<GreenhouseControllerBlock>(){
            @Override public Class<GreenhouseControllerBlock> blockClass(){return GreenhouseControllerBlock.class;}
            @Override public GreenhouseControllerBlock create(MMDMaterial material,MMDBlock block){throw new UnsupportedOperationException("BlockContext required");}
            @Override public GreenhouseControllerBlock create(BlockContext context){return new GreenhouseControllerBlock(context.properties(),context.blockEntity().orElseThrow().as(GreenhouseControllerBlockEntity.class));}
        });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(controller,new BlockEntityFactory<GreenhouseControllerBlockEntity,GreenhouseControllerSettings>(){
            @Override public Class<GreenhouseControllerBlockEntity> blockEntityClass(){return GreenhouseControllerBlockEntity.class;}
            @Override public Codec<GreenhouseControllerSettings> settingsCodec(){return GreenhouseControllerSettings.CODEC;}
            @Override public GreenhouseControllerBlockEntity create(BlockEntityType<GreenhouseControllerBlockEntity> type,BlockPos pos,BlockState state,MMDBlockEntity definition,GreenhouseControllerSettings settings){return new GreenhouseControllerBlockEntity(type,pos,state,settings);}
            @Override public BlockEntityTicker<GreenhouseControllerBlockEntity> ticker(Level level,MMDBlockEntity definition,GreenhouseControllerSettings settings){return level.isClientSide?null:GreenhouseControllerBlockEntity::tick;}
        });
    }
}
