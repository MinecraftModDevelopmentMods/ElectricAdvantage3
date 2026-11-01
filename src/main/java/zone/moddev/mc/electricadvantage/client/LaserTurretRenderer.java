package zone.moddev.mc.electricadvantage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import zone.moddev.mc.electricadvantage.turret.LaserTurretBlock;
import zone.moddev.mc.electricadvantage.turret.LaserTurretBlockEntity;
import zone.moddev.mc.lib.common.runtime.RuntimeContentRegistrar;

/** EA2's turret head (a half-block cube on the optic) and full-bright beam, from the EA2 64x64 texture. */
@EventBusSubscriber(modid = "electricadvantage", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LaserTurretRenderer implements BlockEntityRenderer<LaserTurretBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("electricadvantage:textures/entity/laser_turret.png");
    private static final float R = 0.25f, BEAM = 0.125f;

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        RuntimeContentRegistrar.instances().forEach(registrar -> registrar.blockEntityHandles().values().forEach(handle -> {
            if (handle.prepared().blockEntityClass() == LaserTurretBlockEntity.class)
                event.registerBlockEntityRenderer(handle.binding().as(LaserTurretBlockEntity.class).type().get(), context -> new LaserTurretRenderer());
        }));
    }
    @Override public void render(LaserTurretBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float yaw = -(be.oldYaw + (be.yaw - be.oldYaw) * partial) - 90;
        float pitch = be.oldPitch + (be.pitch - be.oldPitch) * partial;
        boolean active = be.getBlockState().getValue(LaserTurretBlock.ACTIVE);
        pose.pushPose();
        pose.translate(0.5, 0.75, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        var head = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        var p = pose.last();
        // The lens (-Z) uses the right half of the texture: lit top row when active, dark row when not.
        float ev0 = active ? 0 : 0.5f, ev1 = active ? 0.5f : 1;
        quad(head, p, light, overlay, 0, 0, -1, R, R, -R, R, -R, -R, -R, -R, -R, -R, R, -R, 0.5f, ev0, 1, ev1);
        quad(head, p, light, overlay, 0, 0, 1, -R, R, R, -R, -R, R, R, -R, R, R, R, R, 0, 0, 0.5f, 0.5f);
        quad(head, p, light, overlay, -1, 0, 0, -R, R, -R, -R, -R, -R, -R, -R, R, -R, R, R, 0, 0, 0.5f, 0.5f);
        quad(head, p, light, overlay, 1, 0, 0, R, R, R, R, -R, R, R, -R, -R, R, R, -R, 0, 0, 0.5f, 0.5f);
        quad(head, p, light, overlay, 0, 1, 0, -R, R, -R, -R, R, R, R, R, R, R, R, -R, 0, 0, 0.5f, 0.5f);
        quad(head, p, light, overlay, 0, -1, 0, -R, -R, R, -R, -R, -R, R, -R, -R, R, -R, R, 0, 0, 0.5f, 0.5f);
        if (be.showBeam() && be.beamLength > 0) {
            var beam = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
            float l = (float) -be.beamLength;
            int full = LightTexture.FULL_BRIGHT;
            // Two crossed quads along -Z, drawn from both sides by the no-cull render type.
            quad(beam, p, full, overlay, 1, 0, 0, 0, -BEAM, 0, 0, -BEAM, l, 0, BEAM, l, 0, BEAM, 0, 0, 1, 0.5f, 0.5f);
            quad(beam, p, full, overlay, 0, 1, 0, -BEAM, 0, 0, -BEAM, 0, l, BEAM, 0, l, BEAM, 0, 0, 0, 1, 0.5f, 0.5f);
        }
        pose.popPose();
    }
    /** Four corners in order with a UV rectangle (u0,v0)-(u1,v1). */
    private static void quad(VertexConsumer vc, PoseStack.Pose p, int light, int overlay, float nx, float ny, float nz,
            float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4,
            float u0, float v0, float u1, float v1) {
        vertex(vc, p, x1, y1, z1, u0, v0, light, overlay, nx, ny, nz);
        vertex(vc, p, x2, y2, z2, u0, v1, light, overlay, nx, ny, nz);
        vertex(vc, p, x3, y3, z3, u1, v1, light, overlay, nx, ny, nz);
        vertex(vc, p, x4, y4, z4, u1, v0, light, overlay, nx, ny, nz);
    }
    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light, int overlay, float nx, float ny, float nz) {
        vc.addVertex(p, x, y, z).setColor(-1).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
    }
    @Override public boolean shouldRenderOffScreen(LaserTurretBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 96; }
    @Override public AABB getRenderBoundingBox(LaserTurretBlockEntity be) { return new AABB(be.getBlockPos()).inflate(be.settings.attackRange()); }
}
