package zone.moddev.mc.electricadvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.electricadvantage.pump.*;

@GameTestHolder(ElectricAdvantage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PumpGameTests {
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void pipeExtendsThroughAirAndRetracts(GameTestHelper h){
        h.setBlock(5,1,5,Blocks.STONE);
        var be=pump(h,5,6,5);be.energy.setStored(40000);
        ticks(h,be,80);
        for(int y=2;y<=5;y++)h.assertBlockPresent(pipe(),new BlockPos(5,y,5));
        h.assertTrue(be.depth()==4&&be.energy.stored()==40000-4*400,"Four segments at 400 FE each");
        h.assertTrue(be.status()==PumpBlockEntity.Status.NO_FLUID,"A solid floor with no fluid stops the pipe");
        h.setBlock(5,6,5,Blocks.AIR);
        for(int y=2;y<=5;y++)h.assertBlockNotPresent(pipe(),new BlockPos(5,y,5));
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void finiteLavaLosesOneSourcePerInterval(GameTestHelper h){
        basin(h,1,Blocks.LAVA);
        var be=pump(h,5,4,5);be.energy.setStored(40000);
        ticks(h,be,13);
        h.assertTrue(be.tank.getFluidInTank(0).is(Fluids.LAVA)&&be.tank.getFluidInTank(0).getAmount()==1000,"First bucket after the pipe reaches the lava");
        h.assertTrue(sources(h,Fluids.LAVA)==8,"A small lava pool is finite: the source is removed");
        h.assertTrue(be.energy.stored()==40000-400-(4000+128*2),"Bucket costs 4000 FE plus 128 FE per block of depth");
        h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(5,2,5))).isEmpty(),"The source feeding the pipe tip goes first");
        ticks(h,be,20);
        h.assertTrue(be.tank.getFluidInTank(0).getAmount()==1000,"No more than one source per 20 ticks");
        ticks(h,be,13);
        h.assertTrue(be.depth()==2,"The pipe extends into the emptied block");
        h.assertTrue(be.tank.getFluidInTank(0).getAmount()==2000&&sources(h,Fluids.LAVA)==7,"Next bucket comes from a source beside the lowest pipe");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void largeTaggedBodyIsInfinite(GameTestHelper h){
        basin(h,2,Blocks.WATER);
        int previous=Config.PUMP_INFINITE_THRESHOLD.get();
        try{
            Config.PUMP_INFINITE_THRESHOLD.set(20);
            var be=pump(h,5,4,5);be.energy.setStored(40000);
            ticks(h,be,13+20+1+20+1);
            h.assertTrue(be.tank.getFluidInTank(0).getAmount()==3000&&sources(h,Fluids.WATER)==25,"25 connected sources reach the threshold of 20: nothing is removed");
            h.assertTrue(be.status()==PumpBlockEntity.Status.PUMPING_INFINITE,"Status reports an infinite source");
            Config.PUMP_INFINITE_THRESHOLD.set(0);
            be.tank.tank(0).drain(3000,FluidAction.EXECUTE);ticks(h,be,21);
            h.assertTrue(be.tank.getFluidInTank(0).getAmount()==1000&&sources(h,Fluids.WATER)==24,"A threshold of 0 disables infinite sources");
        }finally{Config.PUMP_INFINITE_THRESHOLD.set(previous);}
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void tankRulesRedstoneAndPersistence(GameTestHelper h){
        basin(h,1,Blocks.WATER);
        var be=pump(h,5,4,5);be.energy.setStored(40000);
        h.assertTrue(be.fluidCapability().fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE)==0,"The pump tank cannot be filled from outside");
        be.tank.tank(0).fill(new FluidStack(Fluids.LAVA,500),FluidAction.EXECUTE);
        ticks(h,be,40);
        h.assertTrue(be.status()==PumpBlockEntity.Status.TANK_FULL&&sources(h,Fluids.WATER)==9,"A different fluid in the tank blocks pumping");
        h.assertTrue(be.fluidCapability().drain(500,FluidAction.EXECUTE).getAmount()==500,"Automation can drain the tank");
        h.assertTrue(be.comparator()==0,"Comparator is 0 when empty");
        h.setBlock(5,4,6,Blocks.REDSTONE_BLOCK);ticks(h,be,40);
        h.assertTrue(be.status()==PumpBlockEntity.Status.DISABLED&&be.tank.getFluidInTank(0).isEmpty(),"Redstone stops the pump");
        h.setBlock(5,4,6,Blocks.AIR);ticks(h,be,1);
        h.assertTrue(be.tank.getFluidInTank(0).getAmount()==1000&&be.comparator()==3,"Pumping resumes; comparator follows the tank");
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());
        var restored=(PumpBlockEntity)be.getType().create(be.getBlockPos(),be.getBlockState());restored.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(restored.tank.getFluidInTank(0).getAmount()==1000&&restored.depth()==be.depth()&&restored.energy.stored()==be.energy.stored(),"Tank, depth and energy persist");
        h.succeed();
    }
    /** Stone basin with a (2r+1)-square pool of source blocks at y=2, centred under x=5,z=5. */
    private static void basin(GameTestHelper h,int r,Block fluid){
        for(int x=4-r;x<=6+r;x++)for(int z=4-r;z<=6+r;z++){
            h.setBlock(x,1,z,Blocks.STONE);
            boolean wall=x==4-r||x==6+r||z==4-r||z==6+r;
            if(wall)h.setBlock(x,2,z,Blocks.STONE);
        }
        for(int x=5-r;x<=5+r;x++)for(int z=5-r;z<=5+r;z++)h.setBlock(x,2,z,fluid);
    }
    private static int sources(GameTestHelper h,Fluid fluid){
        int count=0;
        for(int x=0;x<12;x++)for(int y=0;y<8;y++)for(int z=0;z<12;z++){
            var state=h.getLevel().getFluidState(h.absolutePos(new BlockPos(x,y,z)));
            if(state.isSource()&&state.getType().isSame(fluid))count++;
        }
        return count;
    }
    private static PumpBlockEntity pump(GameTestHelper h,int x,int y,int z){
        h.setBlock(x,y,z,BuiltInRegistries.BLOCK.get(id("electric_pump")));
        return(PumpBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(x,y,z)));
    }
    private static Block pipe(){return BuiltInRegistries.BLOCK.get(id("pump_pipe_electric"));}
    private static void ticks(GameTestHelper h,PumpBlockEntity be,int count){for(int n=0;n<count;n++)PumpBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID,path);}
}
