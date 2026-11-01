package zone.moddev.mc.electricadvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.electricadvantage.drill.*;

/** Every drill points at a bedrock wall inside its own test so the 64-block scan never leaves the structure. */
@GameTestHolder(ElectricAdvantage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DrillGameTests {
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure",batch="drill_mine")
    public static void minesThreeByThreeFaceAndExportsBehind(GameTestHelper h){
        face(h,6,Blocks.STONE);wall(h,8);
        h.setBlock(1,3,6,Blocks.CHEST);
        var be=drill(h,2,3,6,Direction.EAST);
        be.energy.setStored(2000);ticks(h,be,8,false);
        h.assertTrue(be.target()!=null&&be.target().equals(h.absolutePos(new BlockPos(6,3,6))),"The first solid block along the facing is the centre of the face");
        h.assertTrue(be.progressGoal()==9*48,"Nine stone blocks at max(32 x 1.5, 16) ticks each");
        h.assertTrue(be.energy.stored()==2000-96L*be.progress(),"Each tick of mining costs 96 FE");
        ticks(h,be,9*48,true);
        for(int y=2;y<=4;y++)for(int z=5;z<=7;z++)h.assertBlockPresent(Blocks.AIR,new BlockPos(6,y,z));
        h.assertTrue(count(h,be,Items.COBBLESTONE)==9,"The whole 3x3 face drops into the drill");
        ticks(h,be,80,true);
        h.assertTrue(count(h,be,Items.COBBLESTONE)==9&&chest(h,1,3,6,Items.COBBLESTONE)>=8,"One item per power update goes to the inventory behind");
        h.assertTrue(be.target()==null&&be.status()==DrillBlockEntity.Status.END_OF_RANGE,"Bedrock ends the scan; without scaffold the drill stays");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure",batch="drill_rules")
    public static void decayRevalidationAndRedstone(GameTestHelper h){
        face(h,6,Blocks.STONE);wall(h,8);
        var be=drill(h,2,3,6,Direction.EAST);
        ticks(h,be,108,true);
        int progress=be.progress();
        h.assertTrue(progress>=100,"Mining is under way");
        for(int slot=0;slot<6;slot++)be.items.setStackInSlot(slot,new ItemStack(Items.DIRT,64));
        ticks(h,be,5,true);
        h.assertTrue(be.progress()==progress-5&&be.status()==DrillBlockEntity.Status.OUTPUT_FULL,"Progress falls by one a tick while the drops don't fit");
        for(int slot=0;slot<6;slot++)be.items.setStackInSlot(slot,ItemStack.EMPTY);
        h.setBlock(6,2,5,Blocks.DIRT);
        ticks(h,be,16,true);
        h.assertTrue(be.progressGoal()==8*48+16&&be.progress()<16,"A changed block drops the face; it is targeted again from scratch");
        h.setBlock(2,3,7,Blocks.REDSTONE_BLOCK);
        long stored=be.energy.stored();
        ticks(h,be,8,false);
        h.assertTrue(be.target()==null&&be.progress()==0&&be.energy.stored()==stored&&be.status()==DrillBlockEntity.Status.DISABLED,"Redstone stops the drill and drops its target");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure",batch="drill_move")
    public static void movesAlongScaffoldLeavingCableAndFrame(GameTestHelper h){
        h.setBlock(5,5,5,Blocks.BEDROCK);h.setBlock(6,5,5,Blocks.BEDROCK);
        h.setBlock(6,6,5,block("electric_track"));
        var be=drill(h,5,6,5,Direction.DOWN);
        be.energy.setStored(999);be.items.setStackInSlot(0,new ItemStack(Items.COBBLESTONE,5));
        ticks(h,be,8,false);
        h.assertTrue(h.getBlockState(new BlockPos(5,6,5)).getBlock() instanceof DrillBlock,"Below 1000 FE the drill doesn't move");
        be.energy.setStored(2000);
        ticks(h,be,8,false);
        h.assertBlockPresent(block("electric_conduit"),new BlockPos(5,6,5));
        h.assertBlockPresent(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("poweradvantage:steel_frame")),new BlockPos(5,5,5));
        var moved=(DrillBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(6,6,5)));
        h.assertTrue(moved!=null&&moved.getBlockState().getValue(DrillBlock.FACING)==Direction.DOWN,"The drill takes the scaffold's place, still facing down");
        h.assertTrue(moved.items.getStackInSlot(0).getCount()==5&&moved.energy.stored()==1000,"Inventory moves with it; the move costs 1000 FE");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(BlockPos.ZERO)).inflate(12)).isEmpty(),"Nothing is dropped or duplicated");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure",batch="drill_beam")
    public static void beamBurnsWhatStandsInIt(GameTestHelper h){
        face(h,7,Blocks.STONE);wall(h,9);
        var be=drill(h,2,3,6,Direction.EAST);
        // The pig stands on a block below the beam line so it is inside the beam.
        h.setBlock(4,2,6,Blocks.STONE);
        var pig=h.spawnWithNoFreeWill(EntityType.PIG,4.5f,3,6.5f);
        ticks(h,be,16,true);
        h.assertTrue(be.beamLength()==5,"The beam reaches the face");
        h.assertTrue(pig.getHealth()<pig.getMaxHealth()&&pig.getRemainingFireTicks()>0,"Entities in the beam are hurt and set on fire");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure",batch="drill_save")
    public static void targetProgressAndInventoryPersist(GameTestHelper h){
        face(h,6,Blocks.STONE);wall(h,8);
        var be=drill(h,2,3,6,Direction.EAST);
        be.items.setStackInSlot(2,new ItemStack(Items.DIAMOND,3));
        ticks(h,be,40,true);
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());
        var restored=(DrillBlockEntity)be.getType().create(be.getBlockPos(),be.getBlockState());restored.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(restored.items.getStackInSlot(2).getCount()==3&&restored.energy.stored()==be.energy.stored(),"Inventory and energy persist");
        restored.energy.setStored(restored.energy.capacity());
        DrillBlockEntity.tick(h.getLevel(),restored.getBlockPos(),restored.getBlockState(),restored);
        h.assertTrue(be.target().equals(restored.target())&&restored.progress()==be.progress()+1&&restored.progressGoal()==be.progressGoal(),"The face and its progress are restored");
        h.succeed();
    }

    /** A 3x3 face of the given block across x, centred on y=3, z=6. */
    private static void face(GameTestHelper h,int x,Block block){for(int y=2;y<=4;y++)for(int z=5;z<=7;z++)h.setBlock(x,y,z,block);}
    private static void wall(GameTestHelper h,int x){for(int y=1;y<=5;y++)for(int z=4;z<=8;z++)h.setBlock(x,y,z,Blocks.BEDROCK);}
    private static DrillBlockEntity drill(GameTestHelper h,int x,int y,int z,Direction facing){
        h.setBlock(x,y,z,block("electric_drill").defaultBlockState().setValue(DrillBlock.FACING,facing));
        return(DrillBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(x,y,z)));
    }
    /** Ticks until the drill moves away; topUp keeps it fully charged. */
    private static void ticks(GameTestHelper h,DrillBlockEntity be,int count,boolean topUp){
        for(int n=0;n<count&&h.getLevel().getBlockEntity(be.getBlockPos())==be;n++){
            if(topUp)be.energy.setStored(be.energy.capacity());
            DrillBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);
        }
    }
    private static int count(GameTestHelper h,DrillBlockEntity be,Item item){
        int total=0;
        for(int slot=0;slot<be.items.getSlots();slot++)if(be.items.getStackInSlot(slot).is(item))total+=be.items.getStackInSlot(slot).getCount();
        return total+chest(h,1,3,6,item);
    }
    private static int chest(GameTestHelper h,int x,int y,int z,Item item){
        var handler=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(new BlockPos(x,y,z)),null);
        int total=0;
        if(handler!=null)for(int slot=0;slot<handler.getSlots();slot++)if(handler.getStackInSlot(slot).is(item))total+=handler.getStackInSlot(slot).getCount();
        return total;
    }
    private static Block block(String path){return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID,path));}
}
