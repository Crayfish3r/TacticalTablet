package com.makar.tacticaltablet.tablet.client.prototype;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Forge vanilla rendering only. Mesh and keyframes are prepared on resource reload. */
public final class TabletPrototypeRenderer extends BlockEntityWithoutLevelRenderer {
    // First-person framing only. Fractions are relative to the current viewport.
    private static final float OPEN_WIDTH_FRACTION = 0.675F;
    private static final float OPEN_CENTER_Y_NDC = -0.08F; // 4% of window height below its center.
    private static final float OPEN_EDGE_MARGIN = 0.04F;
    private static final float OPEN_DISTANCE = 0.80F;
    private static final float DRAW_FOCUS_START = 0.55F;
    private static final float HAND_SCALE = 0.55F;
    private static TabletPrototypeModel.Model framedModel;
    private static Bounds framedBounds;

    private record Bounds(float centerX, float centerY, float centerZ,
                          float halfWidth, float halfHeight, float halfDepth) { }

    public TabletPrototypeRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                                       MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        // ItemRenderer centers baked items by -0.5; our mesh is already centered on tablet_root.
        pose.translate(0.5, 0.5, 0.5);
        float size = switch (context) {
            case GUI, FIXED -> 0.62F;
            case GROUND -> 0.30F;
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> 0.38F;
            default -> 0.42F;
        };
        if (context == ItemDisplayContext.GROUND) pose.translate(0, 0.22, 0);
        pose.scale(size, size, size);
        frontFacing(pose);
        render(pose, buffers, light, overlay, null, 0);
        pose.popPose();
    }

    public static void renderFirstPerson(PoseStack pose, MultiBufferSource buffers, int light,
                                         boolean rightHand, String animation, double seconds) {
        TabletPrototypeModel.Model model = TabletPrototypeModel.INSTANCE.model();
        if (model == null) return;
        Bounds bounds = bounds(model);
        TabletPrototypeModel.Clip clip = model.clip(animation);
        float progress = (float) Math.max(0, Math.min(1, seconds / clip.length()));
        float focus = "holster".equals(animation) ? 1 - smooth(progress)
                : smooth((progress - DRAW_FOCUS_START) / (1 - DRAW_FOCUS_START));

        TabletPrototypeModel.Vec position = TabletPrototypeModel.Clip.sample(clip.position(), seconds);
        TabletPrototypeModel.Vec rotation = model.rootRotation().plus(
                TabletPrototypeModel.Clip.sample(clip.rotation(), seconds));
        float radians = (float) (Math.PI / 180);
        Quaternionf authoredRotation = new Quaternionf().rotationZYX(
                rotation.z() * radians, rotation.y() * radians, rotation.x() * radians);
        Vector3f animatedCenter = authoredRotation.transform(
                new Vector3f(bounds.centerX, bounds.centerY, bounds.centerZ))
                .add(position.x(), position.y(), position.z());

        // Reconstruct the unchanged hand pose, including incoming camera sway, at focus=0.
        Matrix4f handPose = new Matrix4f(pose.last().pose())
                .translate(rightHand ? 0.07F : -0.07F, -0.24F, -0.95F)
                .scale(HAND_SCALE).rotateY((float) Math.PI);
        Vector3f center = handPose.transformPosition(new Vector3f(animatedCenter));
        Quaternionf parentRotation = handPose.getUnnormalizedRotation(new Quaternionf());

        // This is the actual hand-pass projection, already including window aspect and
        // the hand FOV. Read it without changing the player's projection or FOV options.
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        float projectionX = Math.abs(projection.m00());
        float projectionY = Math.abs(projection.m11());
        float halfHeightNdc = 1 - 2 * OPEN_EDGE_MARGIN - Math.abs(OPEN_CENTER_Y_NDC);
        // Include the closest surface of the body, not just its center plane, in the fit.
        float widthScale = OPEN_WIDTH_FRACTION * OPEN_DISTANCE / (projectionX * bounds.halfWidth
                + (OPEN_WIDTH_FRACTION + Math.abs(projection.m20())) * bounds.halfDepth);
        float heightScale = halfHeightNdc * OPEN_DISTANCE / (projectionY * bounds.halfHeight
                + (halfHeightNdc + Math.abs(OPEN_CENTER_Y_NDC + projection.m21())) * bounds.halfDepth);
        float openScale = Math.min(widthScale, heightScale);
        Vector3f openCenter = new Vector3f(OPEN_DISTANCE * projection.m20() / projection.m00(),
                OPEN_DISTANCE * (OPEN_CENTER_Y_NDC + projection.m21()) / projection.m11(), -OPEN_DISTANCE);
        center.lerp(openCenter, focus);
        // Parent correction gradually levels the authored pose and camera sway. The
        // existing animation remains the child transform, including its root pivot.
        Quaternionf openParentRotation = new Quaternionf().rotationY((float) Math.PI)
                .mul(new Quaternionf(authoredRotation).conjugate());
        parentRotation.slerp(openParentRotation, focus);
        float scale = HAND_SCALE + (openScale - HAND_SCALE) * focus;

        pose.pushPose();
        pose.setIdentity();
        pose.translate(center.x(), center.y(), center.z());
        pose.mulPose(parentRotation);
        pose.scale(scale, scale, scale);
        // Scale and level around the actual animated body center, never the hand origin.
        pose.translate(-animatedCenter.x(), -animatedCenter.y(), -animatedCenter.z());
        render(pose, buffers, light, OverlayTexture.NO_OVERLAY, animation, seconds);
        pose.popPose();
    }

    private static float smooth(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * t * (t * (6 * t - 15) + 10);
    }

    private static Bounds bounds(TabletPrototypeModel.Model model) {
        if (framedModel != model) {
            float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
            float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
            for (TabletPrototypeModel.Vertex v : model.vertices()) {
                minX = Math.min(minX, v.x()); maxX = Math.max(maxX, v.x());
                minY = Math.min(minY, v.y()); maxY = Math.max(maxY, v.y());
                minZ = Math.min(minZ, v.z()); maxZ = Math.max(maxZ, v.z());
            }
            framedBounds = new Bounds((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2,
                    (maxX - minX) / 2, (maxY - minY) / 2, (maxZ - minZ) / 2);
            framedModel = model; // A resource reload supplies a different immutable model.
        }
        return framedBounds;
    }

    private static void frontFacing(PoseStack pose) {
        // The authored screen is on NORTH (-Z). A proper 180-degree Y basis
        // rotation faces it toward the camera (+Z), preserving handedness and UVs.
        pose.mulPose(Axis.YP.rotationDegrees(180));
    }

    private static void render(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                               String animation, double seconds) {
        TabletPrototypeModel.Model model = TabletPrototypeModel.INSTANCE.model();
        if (model == null) return;
        TabletPrototypeModel.Vec rotation = model.rootRotation();
        if (animation != null) {
            TabletPrototypeModel.Clip clip = model.clip(animation);
            TabletPrototypeModel.Vec position = TabletPrototypeModel.Clip.sample(clip.position(), seconds);
            pose.translate(position.x(), position.y(), position.z());
            rotation = rotation.plus(TabletPrototypeModel.Clip.sample(clip.rotation(), seconds));
        }
        float radians = (float) (Math.PI / 180);
        pose.mulPose(new Quaternionf().rotationZYX(rotation.z() * radians, rotation.y() * radians, rotation.x() * radians));
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(model.texture()));
        PoseStack.Pose transform = pose.last();
        for (TabletPrototypeModel.Vertex v : model.vertices()) {
            consumer.vertex(transform.pose(), v.x(), v.y(), v.z()).color(255,255,255,255)
                    .uv(v.u(), v.v()).overlayCoords(overlay).uv2(light)
                    .normal(transform.normal(), v.nx(), v.ny(), v.nz()).endVertex();
        }
    }
}
