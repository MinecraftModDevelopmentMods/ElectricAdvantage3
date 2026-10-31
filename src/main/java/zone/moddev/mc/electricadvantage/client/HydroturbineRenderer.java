package zone.moddev.mc.electricadvantage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import zone.moddev.mc.electricadvantage.generation.GenerationBlockEntity;
import zone.moddev.mc.lib.common.runtime.RuntimeContentRegistrar;

/** A BE-owned turbine avoids detached/duplicated world entities across chunk reloads. */
@EventBusSubscriber(modid = "electricadvantage", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HydroturbineRenderer implements BlockEntityRenderer<GenerationBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("electricadvantage:textures/hydroturbine.png");
    private final ModelPart shaft;
    private final ModelPart rotor;
    public HydroturbineRenderer() {
        var shaftMesh = new MeshDefinition();
        shaftMesh.getRoot().addOrReplaceChild("shaft", CubeListBuilder.create().texOffs(8,8).addBox(-1, 0, -1, 2, 8, 2), PartPose.ZERO);
        shaft = LayerDefinition.create(shaftMesh, 16, 16).bakeRoot();
        var rotorMesh = new MeshDefinition();
        for (int i = 0; i < 3; i++) rotorMesh.getRoot().addOrReplaceChild("blade" + i,
                CubeListBuilder.create().texOffs(0,0).addBox(-1.5f, 1, -1, 3, 6, 1),
                PartPose.offsetAndRotation(0, 0, 0, 0, 0, (float)(i * Math.PI * 2 / 3)));
        rotor = LayerDefinition.create(rotorMesh, 16, 16).bakeRoot();
    }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        RuntimeContentRegistrar.instances().forEach(registrar -> registrar.blockEntityHandles().values().forEach(handle -> {
            if (handle.prepared().blockEntityClass() == GenerationBlockEntity.class)
                event.registerBlockEntityRenderer(handle.binding().as(GenerationBlockEntity.class).type().get(), context -> new HydroturbineRenderer());
        }));
    }
    @Override public void render(GenerationBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.settings.kind().equals("hydro")) return;
        pose.pushPose();
        pose.translate(0.5, -0.5, 0.5);
        pose.mulPose(Axis.YP.rotation(be.rotorYaw));
        var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        shaft.render(pose, vertices, light, overlay);
        pose.translate(0, 0, -0.25);
        float rotation = be.previousRotorAngle + (be.getBlockState().getValue(zone.moddev.mc.electricadvantage.generation.GenerationBlock.ACTIVE) ? 30 * partial : 0);
        pose.mulPose(Axis.ZP.rotationDegrees(rotation));
        rotor.render(pose, vertices, light, overlay);
        pose.popPose();
    }
    @Override public AABB getRenderBoundingBox(GenerationBlockEntity be) { return new AABB(be.getBlockPos()).expandTowards(0, -1, 0); }
}
