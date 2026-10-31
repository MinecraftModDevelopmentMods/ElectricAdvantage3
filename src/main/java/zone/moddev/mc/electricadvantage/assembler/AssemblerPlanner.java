package zone.moddev.mc.electricadvantage.assembler;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import java.util.*;

/** Bounded, resumable allocation search: a sized cell uses one item/component identity. */
public final class AssemblerPlanner {
    public record Plan(List<ItemStack> buffer,ItemStack result,List<ItemStack> grid){}
    private static final class Group {
        final ItemStack sample;int total;
        Group(ItemStack sample){this.sample=sample.copyWithCount(1);}
    }
    private final AssemblerCatalog.Entry entry;
    private final Level level;
    private final List<ItemStack> original;
    private final List<Group> groups=new ArrayList<>();
    private final List<AssemblerCatalog.Cell> cells;
    private final boolean[][] candidates;
    private final int[] remaining,cursor,chosen;
    private final Set<String> failed=new HashSet<>();
    private int depth;
    private boolean done;
    private Plan plan;

    public AssemblerPlanner(AssemblerCatalog.Entry entry,Level level,List<ItemStack> buffer){
        this.entry=entry;this.level=level;original=buffer.stream().map(ItemStack::copy).toList();
        for(var stack:original)if(!stack.isEmpty()){
            Group group=groups.stream().filter(g->ItemStack.isSameItemSameComponents(g.sample,stack)).findFirst().orElse(null);
            if(group==null){group=new Group(stack);groups.add(group);}
            group.total=(int)Math.min(576L,(long)group.total+stack.getCount());
        }
        var ordered=new ArrayList<>(entry.cells());
        ordered.sort(Comparator.comparingInt((AssemblerCatalog.Cell cell)->(int)groups.stream().filter(g->cell.ingredient().test(g.sample)&&g.total>=cell.count()).count())
                .thenComparing(Comparator.comparingInt(AssemblerCatalog.Cell::count).reversed()).thenComparingInt(AssemblerCatalog.Cell::gridIndex));
        cells=List.copyOf(ordered);remaining=groups.stream().mapToInt(g->g.total).toArray();
        cursor=new int[cells.size()+1];chosen=new int[cells.size()];Arrays.fill(chosen,-1);
        candidates=new boolean[cells.size()][groups.size()];
        for(int c=0;c<cells.size();c++)for(int g=0;g<groups.size();g++)candidates[c][g]=cells.get(c).ingredient().test(groups.get(g).sample);
        done=cells.isEmpty()||groups.isEmpty();
    }
    public boolean done(){return done;}
    public Plan plan(){return plan;}
    public void advance(int budget){
        for(int step=0;step<budget&&!done;step++){
            if(depth==cells.size()){
                plan=finish();if(plan!=null){done=true;return;}backtrack();continue;
            }
            String state=depth+Arrays.toString(remaining);
            if(cursor[depth]==0&&failed.contains(state)){backtrack();continue;}
            int found=-1;
            while(cursor[depth]<groups.size()){
                int candidate=cursor[depth]++;
                if(candidates[depth][candidate]&&remaining[candidate]>=cells.get(depth).count()){found=candidate;break;}
            }
            if(found<0){if(failed.size()<65536)failed.add(state);cursor[depth]=0;backtrack();}
            else{chosen[depth]=found;remaining[found]-=cells.get(depth).count();depth++;cursor[depth]=0;}
        }
    }
    private void backtrack(){
        if(depth==0){done=true;return;}
        depth--;remaining[chosen[depth]]+=cells.get(depth).count();chosen[depth]=-1;
    }
    private Plan finish(){
        var grid=new ArrayList<ItemStack>(Collections.nCopies(9,ItemStack.EMPTY));
        int[] used=new int[groups.size()];
        for(int c=0;c<cells.size();c++){
            var cell=cells.get(c);var group=groups.get(chosen[c]);
            grid.set(cell.gridIndex(),group.sample.copyWithCount(cell.count()));used[chosen[c]]+=cell.count();
        }
        var input=CraftingInput.of(3,3,grid);
        if(!entry.recipe().matches(input,level))return null;
        var result=entry.recipe().assemble(input,level.registryAccess());if(result.isEmpty())return null;
        var after=new ArrayList<ItemStack>();original.forEach(stack->after.add(stack.copy()));
        for(int group=0;group<groups.size();group++)for(int slot=0;slot<after.size()&&used[group]>0;slot++){
            var stack=after.get(slot);
            if(ItemStack.isSameItemSameComponents(groups.get(group).sample,stack)){
                int consumed=Math.min(used[group],stack.getCount());stack.shrink(consumed);used[group]-=consumed;
            }
        }
        for(var remainder:entry.recipe().getRemainingItems(input))if(!insert(after,remainder))return null;
        return new Plan(List.copyOf(after),result.copy(),List.copyOf(grid));
    }
    private static boolean insert(List<ItemStack> buffer,ItemStack remainder){
        var pending=remainder.copy();
        for(int pass=0;pass<2;pass++)for(int slot=0;slot<buffer.size()&&!pending.isEmpty();slot++){
            var existing=buffer.get(slot);
            if(pass==0&&existing.isEmpty()||pass==1&&!existing.isEmpty())continue;
            if(!existing.isEmpty()&&!ItemStack.isSameItemSameComponents(existing,pending))continue;
            int limit=Math.min(64,pending.getMaxStackSize());
            int count=Math.min(pending.getCount(),Math.max(0,limit-existing.getCount()));
            if(count==0)continue;
            if(existing.isEmpty())buffer.set(slot,pending.copyWithCount(count));else existing.grow(count);
            pending.shrink(count);
        }
        return pending.isEmpty();
    }
}
