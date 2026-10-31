package zone.moddev.mc.electricadvantage.infrastructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import zone.moddev.mc.poweradvantage.network.transport.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Push-fed FE transport; PA3 owns discovery, invalidation, route caching and fair budgets. */
public final class ElectricNetworks {
    private static final Map<ServerLevel, ElectricNetworks> LEVELS = new WeakHashMap<>();
    private final ServerLevel level;
    private final TransportNetworks<CapabilityEndpoint<IEnergyStorage>> networks;
    private int operations;
    private boolean sending;

    private ElectricNetworks(ServerLevel level) {
        this.level = level;
        networks = new TransportNetworks<>(level, new Adapter());
    }
    public static ElectricNetworks get(ServerLevel level) { return LEVELS.computeIfAbsent(level, ElectricNetworks::new); }
    public void changed(BlockPos pos) { networks.changed(pos); }
    public void neighbourChanged(BlockPos pos, BlockPos from) { networks.neighbourChanged(pos, from); }

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Pre event) -> {
            if (event.getLevel() instanceof ServerLevel server) {
                var manager = get(server);
                manager.operations = 512;
                manager.networks.tick();
            }
        });
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel server
                    && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) get(server).networks.chunkLoaded(chunk);
        });
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel server) get(server).networks.chunkUnloaded(event.getChunk().getPos());
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel server) LEVELS.remove(server);
        });
    }

    public void send(ElectricBlockEntity source) {
        dispatch(source.getBlockPos(), source.getBlockPos(), source.energy, source.settings.rate(), false);
    }

    public int sendBackup(BlockPos conductor, BlockPos origin, IEnergyStorage source, int limit) {
        return dispatch(conductor, origin, source, limit, true);
    }

    private int dispatch(BlockPos conductor, BlockPos origin, IEnergyStorage source, int limit, boolean backup) {
        if (sending) return 0;
        sending = true;
        try { return sendInternal(conductor, origin, source, limit, backup); } finally { sending = false; }
    }

    private int priority(CapabilityEndpoint<IEnergyStorage> endpoint) {
        if (endpoint.cache().getCapability() instanceof zone.moddev.mc.electricadvantage.generation.BackupEnergyStorage) return 2;
        return level.getBlockState(endpoint.position()).getBlock() instanceof ElectricBlock block && block.led() ? 0 : 1;
    }

    private int sendInternal(BlockPos conductor, BlockPos origin, IEnergyStorage source, int limit, boolean backup) {
        var node = new TransportNode(conductor);
        var component = networks.component(node);
        if (component == null || !component.valid() || !networks.canRoute() || operations <= 0) return 0;
        var routes = new ArrayList<>(component.graph().routes(node));
        routes.removeIf(route -> !networks.available(route.endpoint().position()));
        routes.sort(Comparator.comparingInt(route -> priority(route.endpoint())));
        if (routes.isEmpty()) return 0;
        var budget = component.budget();
        boolean prioritized = routes.stream().anyMatch(route -> priority(route.endpoint()) != 1);
        int start = prioritized ? 0 : budget.destinationIndex(0, routes.size());
        int moved = 0;
        for (int i = 0; i < routes.size() && operations > 1 && moved < limit && source.getEnergyStored() > 0; i++) {
            int index = (start + i) % routes.size();
            var route = routes.get(index);
            var endpoint = route.endpoint();
            if (!networks.available(endpoint.position()) || !component.valid()) continue;
            var target = endpoint.cache().getCapability();
            if (target == null || !target.canReceive() || endpoint.position().equals(origin)
                    || backup && target instanceof zone.moddev.mc.electricadvantage.generation.BackupEnergyStorage) continue;
            int allowance = budget.allowance(level.getGameTime(), new TransportNetworks.Origin(node, origin),
                    Math.min(Math.min(source.getEnergyStored(), limit - moved), source.extractEnergy(limit - moved, true)));
            if (allowance <= 0) break;
            operations--;
            int requested = Math.clamp(target.receiveEnergy(allowance, true), 0, allowance);
            if (requested <= 0 || !component.valid()) continue;
            boolean valid = route.path().nodes().stream().allMatch(step -> networks.available(step.position())
                    && level.getBlockState(step.position()).getBlock() instanceof ElectricBlock block
                    && block.conductor(level.getBlockState(step.position())));
            if (!valid) { changed(conductor); return moved; }
            operations--;
            int accepted = Math.clamp(target.receiveEnergy(requested, false), 0, requested);
            source.extractEnergy(accepted, false);
            moved += accepted;
            budget.consume(accepted);
            if (accepted > 0) budget.servedDestination(index, routes.size());
        }
        return moved;
    }

    private static final class Adapter implements TransportAdapter<CapabilityEndpoint<IEnergyStorage>> {
        @Override public boolean matches(BlockState state) {
            return state.getBlock() instanceof ElectricBlock block && block.conductor(state);
        }
        @Override public NodeData<CapabilityEndpoint<IEnergyStorage>> inspect(Context context, TransportNode node) {
            List<TransportNode> neighbours = new ArrayList<>();
            List<CapabilityEndpoint<IEnergyStorage>> endpoints = new ArrayList<>();
            for (Direction direction : Direction.values()) {
                var next = node.position().relative(direction);
                if (!context.available(next)) continue;
                var state = context.level().getBlockState(next);
                if (matches(state)) neighbours.add(new TransportNode(next));
                else if (!(state.getBlock() instanceof ElectricBlock block) || block.led()) {
                    var face = direction.getOpposite();
                    var cache = context.watch(Capabilities.EnergyStorage.BLOCK, next, face);
                    if (cache.getCapability() != null) endpoints.add(new CapabilityEndpoint<>(node, next, face, false, cache));
                }
            }
            var be = (ElectricBlockEntity)context.level().getBlockEntity(node.position());
            return new NodeData<>(neighbours, neighbours, endpoints, be.settings.rate(), true);
        }
        @Override public boolean isSource(CapabilityEndpoint<IEnergyStorage> endpoint) { return false; }
        @Override public void refresh(ServerLevel level, TransportNode node) { ElectricBlock.refresh(level, node.position()); }
    }
}
