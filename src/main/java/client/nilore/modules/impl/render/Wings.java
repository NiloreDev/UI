package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.RenderEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

@SuppressWarnings({
        "deprecation",
        "removal"
})
public class Wings extends Module {

    public static Wings INSTANCE;

    /*
     * ============================================================
     * Wing Textures
     * ============================================================
     *
     * 文件位置：
     *
     * assets/nilore/textures/wings/black_demon.png
     * assets/nilore/textures/wings/purple_galaxy.png
     */

    private static final ResourceLocation BLACK_DEMON =
            new ResourceLocation(
                    "nilore",
                    "textures/wings/black_demon.png"
            );

    private static final ResourceLocation PURPLE_GALAXY =
            new ResourceLocation(
                    "nilore",
                    "textures/wings/purple_galaxy.png"
            );

    /*
     * ============================================================
     * Mode
     * ============================================================
     */

    public final ModeSetting mode =
            new ModeSetting(
                    "Mode",
                    "Black Demon",
                    "Purple Galaxy"
            ).withDefault("Black Demon");

    /*
     * ============================================================
     * Size
     * ============================================================
     */

    public final NumberSetting size =
            new NumberSetting(
                    "Size",
                    1.0,
                    0.3,
                    3.0,
                    0.05
            );

    /*
     * ============================================================
     * Animation Speed
     * ============================================================
     */

    public final NumberSetting speed =
            new NumberSetting(
                    "Speed",
                    3.0,
                    0.1,
                    10.0,
                    0.1
            );

    /*
     * ============================================================
     * Flap
     * ============================================================
     */

    public final NumberSetting flap =
            new NumberSetting(
                    "Flap",
                    20.0,
                    0.0,
                    60.0,
                    1.0
            );

    /*
     * ============================================================
     * Spread
     * ============================================================
     */

    public final NumberSetting spread =
            new NumberSetting(
                    "Spread",
                    18.0,
                    0.0,
                    70.0,
                    1.0
            );

    /*
     * ============================================================
     * Back Offset
     * ============================================================
     */

    public final NumberSetting backOffset =
            new NumberSetting(
                    "Back Offset",
                    0.16,
                    -0.5,
                    0.8,
                    0.01
            );

    /*
     * ============================================================
     * Height
     * ============================================================
     */

    public final NumberSetting height =
            new NumberSetting(
                    "Height",
                    1.45,
                    0.3,
                    2.5,
                    0.01
            );

    /*
     * ============================================================
     * Wing Width
     * ============================================================
     */

    public final NumberSetting width =
            new NumberSetting(
                    "Width",
                    0.95,
                    0.3,
                    2.5,
                    0.05
            );

    /*
     * ============================================================
     * Wing Height
     * ============================================================
     */

    public final NumberSetting wingHeight =
            new NumberSetting(
                    "Wing Height",
                    1.20,
                    0.3,
                    2.5,
                    0.05
            );

    /*
     * ============================================================
     * Constructor
     * ============================================================
     */

    public Wings() {

        super(
                "Wings",
                Category.RENDER
        );

        INSTANCE = this;
    }

    /*
     * ============================================================
     * Current texture
     * ============================================================
     */

    public ResourceLocation getWingTexture() {

        if (mode.is("Purple Galaxy")) {
            return PURPLE_GALAXY;
        }

        return BLACK_DEMON;
    }

    /*
     * ============================================================
     * Render
     * ============================================================
     */

    @EventTarget
    public void onRender3D(RenderEvent event) {

        if (mc.player == null
                || mc.level == null) {
            return;
        }

        PoseStack poseStack =
                event.poseStack();

        float partialTick =
                event.partialTick();

        /*
         * 玩家插值坐标
         */
        double playerX =
                Mth.lerp(
                        partialTick,
                        mc.player.xOld,
                        mc.player.getX()
                );

        double playerY =
                Mth.lerp(
                        partialTick,
                        mc.player.yOld,
                        mc.player.getY()
                );

        double playerZ =
                Mth.lerp(
                        partialTick,
                        mc.player.zOld,
                        mc.player.getZ()
                );

        /*
         * 身体方向
         */
        float yaw =
                Mth.rotLerp(
                        partialTick,
                        mc.player.yBodyRotO,
                        mc.player.yBodyRot
                );

        /*
         * 相机位置
         */
        Vec3 camera =
                mc.gameRenderer
                        .getMainCamera()
                        .getPosition();

        /*
         * ========================================================
         * Animation
         * ========================================================
         */

        double time =
                System.currentTimeMillis()
                        / 1000.0D;

        double animationSpeed =
                speed
                        .getValue()
                        .doubleValue();

        float wave =
                (float) Math.sin(
                        time * animationSpeed
                );

        float flapAngle =
                wave
                        * flap
                        .getValue()
                        .floatValue();

        float scale =
                size
                        .getValue()
                        .floatValue();

        poseStack.pushPose();

        try {

            /*
             * 世界坐标 -> 相机相对坐标
             */
            poseStack.translate(
                    -camera.x,
                    -camera.y,
                    -camera.z
            );

            /*
             * 移动到玩家
             */
            poseStack.translate(
                    playerX,
                    playerY
                            + height
                            .getValue()
                            .doubleValue(),
                    playerZ
            );

            /*
             * 跟随玩家身体旋转
             */
            poseStack.mulPose(
                    Axis.YP.rotationDegrees(
                            -yaw
                    )
            );

            /*
             * 移到背后
             */
            poseStack.translate(
                    0.0D,
                    0.0D,
                    backOffset
                            .getValue()
                            .doubleValue()
            );

            /*
             * 蹲下适配
             */
            if (mc.player.isCrouching()) {

                poseStack.translate(
                        0.0D,
                        -0.20D,
                        0.06D
                );

                poseStack.mulPose(
                        Axis.XP.rotationDegrees(
                                15.0F
                        )
                );
            }

            /*
             * 左翼
             */
            renderWing(
                    poseStack,
                    true,
                    flapAngle,
                    scale
            );

            /*
             * 右翼
             */
            renderWing(
                    poseStack,
                    false,
                    flapAngle,
                    scale
            );

        } finally {

            poseStack.popPose();
        }
    }

    /*
     * ============================================================
     * Render One Wing
     * ============================================================
     */

    private void renderWing(
            PoseStack poseStack,
            boolean left,
            float flapAngle,
            float scale
    ) {

        poseStack.pushPose();

        try {

            float side =
                    left
                            ? -1.0F
                            : 1.0F;

            /*
             * 两片翅膀的根部稍微分开
             */
            poseStack.translate(
                    0.065F * side,
                    0.0F,
                    0.0F
            );

            /*
             * ====================================================
             * Spread
             * ====================================================
             */

            float spreadAngle =
                    spread
                            .getValue()
                            .floatValue()
                            * side;

            poseStack.mulPose(
                    Axis.YP.rotationDegrees(
                            spreadAngle
                    )
            );

            /*
             * ====================================================
             * Flapping
             * ====================================================
             */

            poseStack.mulPose(
                    Axis.ZP.rotationDegrees(
                            flapAngle * side
                    )
            );

            /*
             * 少量向前后摆动
             */
            poseStack.mulPose(
                    Axis.XP.rotationDegrees(
                            flapAngle * 0.10F
                    )
            );

            /*
             * 整体缩放
             */
            poseStack.scale(
                    scale,
                    scale,
                    scale
            );

            /*
             * 绘制
             */
            drawWing(
                    poseStack,
                    left
            );

        } finally {

            poseStack.popPose();
        }
    }

    /*
     * ============================================================
     * Draw Texture
     * ============================================================
     */

    private void drawWing(
            PoseStack poseStack,
            boolean left
    ) {

        /*
         * ========================================================
         * Render State
         * ========================================================
         */

        RenderSystem.enableBlend();

        /*
         * 关键：
         *
         * PNG 的透明 Alpha 会在这里正常工作。
         */
        RenderSystem.defaultBlendFunc();

        /*
         * 翅膀正反两面都显示
         */
        RenderSystem.disableCull();

        /*
         * 正常深度
         */
        RenderSystem.enableDepthTest();

        /*
         * Shader
         */
        RenderSystem.setShader(
                GameRenderer::getPositionTexShader
        );

        /*
         * 当前 Mode 的纹理
         */
        RenderSystem.setShaderTexture(
                0,
                getWingTexture()
        );

        /*
         * 保持原始颜色和 Alpha
         */
        RenderSystem.setShaderColor(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        /*
         * ========================================================
         * Matrix
         * ========================================================
         */

        Matrix4f matrix =
                poseStack
                        .last()
                        .pose();

        BufferBuilder builder =
                Tesselator
                        .getInstance()
                        .getBuilder();

        builder.begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX
        );

        /*
         * ========================================================
         * Wing dimensions
         * ========================================================
         */

        float wingWidth =
                width
                        .getValue()
                        .floatValue();

        float wingSizeY =
                wingHeight
                        .getValue()
                        .floatValue();

        /*
         * 左右方向
         */
        float direction =
                left
                        ? -1.0F
                        : 1.0F;

        /*
         * ========================================================
         * UV mirror
         * ========================================================
         *
         * 同一张单翼 PNG 自动镜像。
         */

        float uInner;
        float uOuter;

        if (left) {

            uInner = 0.0F;
            uOuter = 1.0F;

        } else {

            uInner = 1.0F;
            uOuter = 0.0F;
        }

        /*
         * ========================================================
         * Top Inner
         * ========================================================
         */

        builder.vertex(
                        matrix,
                        0.0F,
                        0.0F,
                        0.0F
                )
                .uv(
                        uInner,
                        0.0F
                )
                .endVertex();

        /*
         * ========================================================
         * Top Outer
         * ========================================================
         */

        builder.vertex(
                        matrix,
                        wingWidth * direction,
                        0.0F,
                        0.0F
                )
                .uv(
                        uOuter,
                        0.0F
                )
                .endVertex();

        /*
         * ========================================================
         * Bottom Outer
         * ========================================================
         */

        builder.vertex(
                        matrix,
                        wingWidth * direction,
                        -wingSizeY,
                        0.0F
                )
                .uv(
                        uOuter,
                        1.0F
                )
                .endVertex();

        /*
         * ========================================================
         * Bottom Inner
         * ========================================================
         */

        builder.vertex(
                        matrix,
                        0.0F,
                        -wingSizeY,
                        0.0F
                )
                .uv(
                        uInner,
                        1.0F
                )
                .endVertex();

        /*
         * Draw
         */
        BufferUploader.drawWithShader(
                builder.end()
        );

        /*
         * ========================================================
         * Restore
         * ========================================================
         */

        RenderSystem.setShaderColor(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        RenderSystem.enableCull();

        RenderSystem.disableBlend();
    }

    /*
     * ============================================================
     * Module
     * ============================================================
     */

    @Override
    public String getDisplayName() {
        return "";
    }

    @Override
    public String getModuleName() {
        return "";
    }

    @Override
    public void onTick() {

    }
}