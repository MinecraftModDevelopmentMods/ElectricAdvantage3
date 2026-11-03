package zone.moddev.mc.electricadvantage.trades;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import java.util.List;

/**
 * EA2's six librarian trades (items sold for emeralds), moved to the toolsmith so they don't dilute
 * the librarian's enchanted-book trades, and also offered by the wandering trader.
 */
public final class TradeSystems {
    /** Sells minCount..maxCount of the item for minPrice..maxPrice emeralds; level is the villager level. */
    public record Trade(String item,int minCount,int maxCount,int minPrice,int maxPrice,int level){
        public ItemStack result(int count){return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("electricadvantage",item)),count);}
    }
    public static final List<Trade> TRADES=List.of(
            new Trade("electric_conduit",3,6,1,1,1),
            new Trade("solder",5,10,1,1,1),
            new Trade("integrated_circuit",3,6,1,1,1),
            new Trade("blank_circuit_board",3,6,1,1,1),
            new Trade("petrolplastic_ingot",4,8,1,1,2),
            new Trade("psu",1,1,1,3,2));
    private TradeSystems(){}

    public static void register(){
        NeoForge.EVENT_BUS.addListener((VillagerTradesEvent event)->{
            if(event.getType()!=VillagerProfession.TOOLSMITH)return;
            // Vanilla novice/apprentice item-sale experience.
            for(var trade:TRADES)event.getTrades().get(trade.level()).add(listing(trade,12,trade.level()==1?1:5));
        });
        NeoForge.EVENT_BUS.addListener((WandererTradesEvent event)->{
            for(var trade:TRADES)event.getGenericTrades().add(listing(trade,6,1));
        });
    }
    private static VillagerTrades.ItemListing listing(Trade trade,int maxUses,int xp){
        return (entity,random)->new MerchantOffer(new ItemCost(Items.EMERALD,random.nextIntBetweenInclusive(trade.minPrice(),trade.maxPrice())),
                trade.result(random.nextIntBetweenInclusive(trade.minCount(),trade.maxCount())),maxUses,xp,0.05f);
    }
}
