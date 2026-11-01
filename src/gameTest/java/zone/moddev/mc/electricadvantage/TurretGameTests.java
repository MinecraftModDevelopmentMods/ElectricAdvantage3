package zone.moddev.mc.electricadvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.electricadvantage.turret.LaserTurretBlockEntity;

/** Each test has its own batch: a turret's 16-block range would otherwise reach neighbouring tests. */
@GameTestHolder(ElectricAdvantage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TurretGameTests {
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure",batch="turret_fire")
    public static void locksFiresAndHitsEverythingInLine(GameTestHelper h){
        floor(h);
        var be=turret(h,"laser_turret");be.energy.setStored(8000);
        var near=h.spawnWithNoFreeWill(EntityType.ZOMBIE,7.5f,2,6.5f);
        var far=h.spawnWithNoFreeWill(EntityType.ZOMBIE,10.5f,2,6.5f);
        ticks(h,be,8);
        h.assertTrue(be.target()==near,"The nearest visible monster is locked");
        ticks(h,be,30);
        h.assertTrue(near.getHealth()<14&&far.getHealth()<14,"The beam hurts every living entity along it (7 damage)");
        h.assertTrue(near.getRemainingFireTicks()>0,"Victims are set on fire");
        h.assertTrue(h.getBlockState(new BlockPos(7,2,6)).is(Blocks.FIRE),"EA2 ground ignition under the victim");
        h.assertTrue(be.energy.stored()<4000&&!be.locked(),"A shot costs 4000 FE; below a full shot the turret stands down");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure",batch="turret_ignore")
    public static void ignoresPassiveHiddenAndDisabled(GameTestHelper h){
        floor(h);
        var be=turret(h,"laser_turret");be.energy.setStored(8000);
        h.spawnWithNoFreeWill(EntityType.COW,4.5f,2,4.5f);
        for(int z=0;z<12;z++)for(int y=2;y<=5;y++)h.setBlock(6,y,z,Blocks.STONE);
        var hidden=h.spawnWithNoFreeWill(EntityType.ZOMBIE,8.5f,2,6.5f);
        ticks(h,be,12);
        h.assertTrue(!be.locked(),"Passive mobs and monsters behind walls are ignored");
        h.setBlock(2,2,7,Blocks.REDSTONE_BLOCK);
        for(int z=0;z<12;z++)for(int y=2;y<=5;y++)h.setBlock(6,y,z,Blocks.AIR);
        long stored=be.energy.stored();ticks(h,be,12);
        h.assertTrue(!be.locked()&&be.energy.stored()==stored,"Redstone disables the turret and its idle drain");
        h.setBlock(2,2,7,Blocks.AIR);ticks(h,be,8);
        h.assertTrue(be.target()==hidden,"Once visible and enabled, the monster is locked");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure",batch="turret_evil")
    public static void evilTurretTargetsPassiveMobsNotMonsters(GameTestHelper h){
        floor(h);
        var be=turret(h,"laser_turret_evil");be.energy.setStored(8000);
        h.spawnWithNoFreeWill(EntityType.ZOMBIE,4.5f,2,6.5f);
        var cow=h.spawnWithNoFreeWill(EntityType.COW,8.5f,2,6.5f);
        ticks(h,be,8);
        h.assertTrue(be.target()==cow,"The hidden variant ignores the closer monster and locks the cow");
        net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(h.getLevel().enabledFeatures(),true,h.getLevel().registryAccess());
        var evil=BuiltInRegistries.ITEM.get(id("laser_turret_evil"));
        boolean listed=h.getLevel().registryAccess().registryOrThrow(Registries.CREATIVE_MODE_TAB).stream()
                .anyMatch(tab->tab.getDisplayItems().stream().anyMatch(s->s.is(evil))||tab.getSearchTabDisplayItems().stream().anyMatch(s->s.is(evil)));
        h.assertTrue(!listed,"The player-targeting turret is in no creative tab, search included");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure",batch="turret_team")
    public static void teamRulesAndPersistence(GameTestHelper h){
        var be=turret(h,"laser_turret");
        var scoreboard=h.getLevel().getScoreboard();
        var red=scoreboard.addPlayerTeam("ea3_test_red");
        try{
            // Mock players share one scoreboard name, so a single player moves in and out of the team.
            var player=h.makeMockPlayer(GameType.SURVIVAL);var builder=h.makeMockPlayer(GameType.CREATIVE);
            h.assertTrue(!be.isEnemy(player),"Without a team the turret never targets players");
            be.setTeam("ea3_test_red");
            h.assertTrue(be.isEnemy(player)&&!be.isEnemy(builder),"Players outside the team are enemies; creative players never are");
            scoreboard.addPlayerToTeam(player.getScoreboardName(),red);
            h.assertTrue(!be.isEnemy(player),"Team members are not enemies");
            be.setTeam(null);be.setOwner(player);
            var owner=player;
            h.assertTrue("ea3_test_red".equals(be.team())&&owner.getUUID().equals(be.owner()),"Placing records the owner and the owner's team");
            var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());
            var restored=(LaserTurretBlockEntity)be.getType().create(be.getBlockPos(),be.getBlockState());restored.loadWithComponents(saved,h.getLevel().registryAccess());
            h.assertTrue("ea3_test_red".equals(restored.team())&&owner.getUUID().equals(restored.owner()),"Owner and team persist");
        }finally{scoreboard.removePlayerTeam(red);}
        h.succeed();
    }
    private static void floor(GameTestHelper h){for(int x=0;x<12;x++)for(int z=0;z<12;z++)h.setBlock(x,1,z,Blocks.STONE);}
    private static LaserTurretBlockEntity turret(GameTestHelper h,String name){
        h.setBlock(2,2,6,BuiltInRegistries.BLOCK.get(id(name)));
        return(LaserTurretBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2,2,6)));
    }
    private static void ticks(GameTestHelper h,LaserTurretBlockEntity be,int count){
        for(int n=0;n<count;n++)LaserTurretBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID,path);}
}
