package zone.moddev.mc.electricadvantage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import zone.moddev.mc.electricadvantage.drill.DrillBlock;
import zone.moddev.mc.electricadvantage.drill.DrillBlockEntity;
import zone.moddev.mc.lib.common.runtime.RuntimeContentRegistrar;

/** EA2's drill beam: two crossed full-bright quads from the drill's centre to the centre of the targeted face. */
@EventBusSubscriber(modid = "electricadvantage", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LaserDrillRenderer implements BlockEntityRenderer<DrillBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("electricadvantage:textures/entity/laser_beam.png");
    private static final float BEAM = 0.125f;

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        RuntimeContentRegistrar.instances().forEach(registrar -> registrar.blockEntityHandles().values().forEach(handle -> {
            if (handle.prepared().blockEntityClass() == DrillBlockEntity.class)
                event.registerBlockEntityRenderer(handle.binding().as(DrillBlockEntity.class).type().get(), context -> new LaserDrillRenderer());
        }));
    }
    @Override public void render(DrillBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int length = be.beamLength();
        if (length <= 0) return;
        var facing = be.getBlockState().getValue(DrillBlock.FACING);
        var beam = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        var p = pose.last();
        float dx = facing.getStepX() * length, dy = facing.getStepY() * length, dz = facing.getStepZ() * length;
        // One quad across each of the two axes perpendicular to the beam, drawn from both sides by the no-cull render type.
        for (var axis : Direction.Axis.values()) {
            if (axis == facing.getAxis()) continue;
            float ax = axis == Direction.Axis.X ? BEAM : 0, ay = axis == Direction.Axis.Y ? BEAM : 0, az = axis == Direction.Axis.Z ? BEAM : 0;
            var normal = Direction.fromAxisAndDirection(otherAxis(facing.getAxis(), axis), Direction.AxisDirection.POSITIVE);
            vertex(beam, p, 0.5f - ax, 0.5f - ay, 0.5f - az, 0, 0, overlay, normal);
            vertex(beam, p, 0.5f - ax + dx, 0.5f - ay + dy, 0.5f - az + dz, 0, 1, overlay, normal);
            vertex(beam, p, 0.5f + ax + dx, 0.5f + ay + dy, 0.5f + az + dz, 1, 1, overlay, normal);
            vertex(beam, p, 0.5f + ax, 0.5f + ay, 0.5f + az, 1, 0, overlay, normal);
        }
    }
    private static Direction.Axis otherAxis(Direction.Axis a, Direction.Axis b) {
        for (var axis : Direction.Axis.values()) if (axis != a && axis != b) return axis;
        return a;
    }
    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int overlay, Direction normal) {
        vc.addVertex(p, x, y, z).setColor(-1).setUv(u, v).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(p, normal.getStepX(), normal.getStepY(), normal.getStepZ());
    }
    @Override public boolean shouldRenderOffScreen(DrillBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 128; }
    @Override public AABB getRenderBoundingBox(DrillBlockEntity be) { return new AABB(be.getBlockPos()).inflate(be.settings.range() + 1); }
}
