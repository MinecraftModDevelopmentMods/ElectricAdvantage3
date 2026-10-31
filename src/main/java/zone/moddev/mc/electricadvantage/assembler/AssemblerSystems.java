package zone.moddev.mc.electricadvantage.assembler;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import zone.moddev.mc.lib.api.*;
import zone.moddev.mc.lib.common.factories.BlockEntityFactory;
import zone.moddev.mc.lib.common.types.*;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;

public final class AssemblerSystems {
    private static final DeferredRegister<RecipeType<?>> TYPES=DeferredRegister.create(Registries.RECIPE_TYPE,"electricadvantage");
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,"electricadvantage");
    public static final DeferredHolder<RecipeType<?>,RecipeType<AssemblerRecipe>> TYPE=TYPES.register("assembling",()->new RecipeType<>(){
        @Override public String toString(){return "electricadvantage:assembling";}
    });
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<AssemblerRecipe>> SERIALIZER=SERIALIZERS.register("assembling",()->new RecipeSerializer<>(){
        @Override public MapCodec<AssemblerRecipe> codec(){return AssemblerRecipe.CODEC;}
        @Override public StreamCodec<RegistryFriendlyByteBuf,AssemblerRecipe> streamCodec(){return AssemblerRecipe.STREAM_CODEC;}
    });
    private AssemblerSystems(){}
    public static void register(IEventBus bus){
        TYPES.register(bus);SERIALIZERS.register(bus);
        var id=ResourceLocation.parse("electricadvantage:assembler");
        MaterialExtensions.GLOBAL.registerBlockImplementation(id,new BlockImplementation<AssemblerBlock>(){
            @Override public Class<AssemblerBlock> blockClass(){return AssemblerBlock.class;}
            @Override public AssemblerBlock create(MMDMaterial material,MMDBlock block){throw new UnsupportedOperationException("BlockContext required");}
            @Override public AssemblerBlock create(BlockContext context){return new AssemblerBlock(context.properties(),context.blockEntity().orElseThrow().as(AssemblerBlockEntity.class));}
        });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(id,new BlockEntityFactory<AssemblerBlockEntity,AssemblerSettings>(){
            @Override public Class<AssemblerBlockEntity> blockEntityClass(){return AssemblerBlockEntity.class;}
            @Override public Codec<AssemblerSettings> settingsCodec(){return AssemblerSettings.CODEC;}
            @Override public AssemblerBlockEntity create(BlockEntityType<AssemblerBlockEntity> type,BlockPos pos,BlockState state,MMDBlockEntity definition,AssemblerSettings settings){return new AssemblerBlockEntity(type,pos,state,settings);}
            @Override public BlockEntityTicker<AssemblerBlockEntity> ticker(Level level,MMDBlockEntity definition,AssemblerSettings settings){return level.isClientSide?null:AssemblerBlockEntity::tick;}
        });
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event)->{
            var manager=event.getServerResources().getRecipeManager();
            event.addListener((ResourceManagerReloadListener)resources->AssemblerCatalog.rebuild(manager));
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event)->AssemblerCatalog.clear());
    }
    private static AssemblerBlockEntity host(ScreenClick click){
        return click.menu().blockEntity instanceof AssemblerBlockEntity be
                &&click.player().containerMenu==click.menu()&&click.menu().stillValid(click.player())?be:null;
    }
    public static void next(ScreenClick click){var be=host(click);if(be!=null)be.cycleRecipe(1);}
    public static void previous(ScreenClick click){var be=host(click);if(be!=null)be.cycleRecipe(-1);}
    public static void clear(ScreenClick click){var be=host(click);if(be!=null)be.clearRecipe();}
}
