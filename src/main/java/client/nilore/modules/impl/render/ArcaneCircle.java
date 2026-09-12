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

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/*
 * Minecraft Forge 1.20.1
 *
 * 玩家脚下魔法阵
 *
 * 功能：
 * - A / B 图片切换
 * - 大小调整
 * - 旋转速度调整
 * - 高度调整
 */
@SuppressWarnings({
        "deprecation",
        "removal"
})
public class ArcaneCircle extends Module {

    public static ArcaneCircle INSTANCE;

    /*
     * ============================================================
     * Texture
     * ============================================================
     *
     * Forge 1.20.1 正确写法。
     *
     * IDEA 如果使用了较新的 Minecraft API 索引，
     * 可能提示 1.20.6 起弃用。
     *
     * 但 1.20.1 不要改成：
     *
     * ResourceLocation.fromNamespaceAndPath(...)
     *
     * 因为 1.20.1 没有这个方法。
     */
    private static final ResourceLocation TEXTURE_A =
            new ResourceLocation(
                    "nilore",
                    "textures/effects/arcane_a.png"
            );

    private static final ResourceLocation TEXTURE_B =
            new ResourceLocation(
                    "nilore",
                    "textures/effects/arcane_b.png"
            );

    /*
     * ============================================================
     * Mode
     * ============================================================
     *
     * A = arcane_a.png
     * B = arcane_b.png
     */
    public final ModeSetting mode =
            new ModeSetting(
                    "Mode",
                    "A",
                    "B"
            ).withDefault("A");

    /*
     * ============================================================
     * Size
     * ============================================================
     */
    public final NumberSetting size =
            new NumberSetting(
                    "Size",
                    3.0,
                    0.5,
                    10.0,
                    0.1
            );

    /*
     * ============================================================
     * Rotation Speed
     * ============================================================
     *
     * 0 = 不旋转
     * 正数 = 顺方向
     * 负数 = 反方向
     */
    public final NumberSetting rotationSpeed =
            new NumberSetting(
                    "Rotation Speed",
                    90.0,
                    -720.0,
                    720.0,
                    5.0
            );

    /*
     * ============================================================
     * Height
     * ============================================================
     *
     * 稍微抬高一点，
     * 防止和地面 Z-Fighting 闪烁。
     */
    public final NumberSetting height =
            new NumberSetting(
                    "Height",
                    0.02,
                    0.001,
                    0.30,
                    0.001
            );

    /*
     * ============================================================
     * Constructor
     * ============================================================
     */
    public ArcaneCircle() {
        super(
                "ArcaneCircle",
                Category.RENDER
        );

        INSTANCE = this;
    }

    /*
     * ============================================================
     * Render
     * ============================================================
     */
    @EventTarget
    public void onRender3D(RenderEvent event) {

        /*
         * 世界 / 玩家不存在时不绘制。
         */
        if (mc.player == null
                || mc.level == null) {
            return;
        }

        PoseStack poseStack =
                event.poseStack();

        float partialTick =
                event.partialTick();

        /*
         * ========================================================
         * 玩家插值位置
         * ========================================================
         *
         * 使用 xOld/yOld/zOld，
         * 防止移动时魔法阵抖动。
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
         * ========================================================
         * Camera
         * ========================================================
         */
        Vec3 camera =
                mc.gameRenderer
                        .getMainCamera()
                        .getPosition();

        /*
         * ========================================================
         * Rotation
         * ========================================================
         */
        float speed =
                rotationSpeed
                        .getValue()
                        .floatValue();

        /*
         * 根据真实时间旋转。
         *
         * rotationSpeed = 90
         * 即每秒大约 90 度。
         */
        float angle =
                (float) (
                        (
                                System.currentTimeMillis()
                                        / 1000.0D
                        )
                                * speed
                                % 360.0D
                );

        /*
         * ========================================================
         * Size
         * ========================================================
         */
        float circleSize =
                size
                        .getValue()
                        .floatValue();

        float half =
                circleSize / 2.0F;

        /*
         * ========================================================
         * Height
         * ========================================================
         */
        double yOffset =
                height
                        .getValue()
                        .doubleValue();

        /*
         * ========================================================
         * Texture Mode
         * ========================================================
         */
        ResourceLocation texture;

        if (mode.is("B")) {
            texture = TEXTURE_B;
        } else {
            texture = TEXTURE_A;
        }

        /*
         * ========================================================
         * PoseStack
         * ========================================================
         */
        poseStack.pushPose();

        try {

            /*
             * 把世界坐标转换为相机相对坐标。
             */
            poseStack.translate(
                    -camera.x,
                    -camera.y,
                    -camera.z
            );

            /*
             * 移动到玩家脚下。
             */
            poseStack.translate(
                    playerX,
                    playerY + yOffset,
                    playerZ
            );

            /*
             * ====================================================
             * 绕 Y 轴旋转
             * ====================================================
             *
             * 因为魔法阵本身直接绘制在 X/Z 平面，
             * 所以不需要再 X 轴旋转 90°。
             *
             * 直接改变顶点位置实现 Y 轴旋转。
             */
            renderCircle(
                    poseStack,
                    texture,
                    half,
                    angle
            );

        } finally {

            /*
             * 防止矩阵影响其他 ESP / HUD / 世界渲染。
             */
            poseStack.popPose();
        }
    }

    /*
     * ============================================================
     * Draw Circle
     * ============================================================
     */
    private void renderCircle(
            PoseStack poseStack,
            ResourceLocation texture,
            float half,
            float angle
    ) {

        /*
         * ========================================================
         * OpenGL State
         * ========================================================
         */
        RenderSystem.enableBlend();

        RenderSystem.defaultBlendFunc();

        /*
         * 两面都显示。
         */
        RenderSystem.disableCull();

        /*
         * 保留深度测试。
         *
         * 被方块挡住时不会透墙显示。
         */
        RenderSystem.enableDepthTest();

        /*
         * 不往深度缓冲写，
         * 避免影响后续实体/方块渲染。
         */
        RenderSystem.depthMask(false);

        /*
         * Shader
         */
        RenderSystem.setShader(
                GameRenderer::getPositionTexShader
        );

        /*
         * Texture
         */
        RenderSystem.setShaderTexture(
                0,
                texture
        );

        /*
         * 保持图片原颜色。
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

        /*
         * ========================================================
         * Rotation Calculation
         * ========================================================
         *
         * 直接计算 X/Z 顶点旋转。
         *
         * 这样图片会始终平铺在玩家脚下。
         */
        double radians =
                Math.toRadians(angle);

        float cos =
                (float) Math.cos(radians);

        float sin =
                (float) Math.sin(radians);

        /*
         * 原始四个角：
         *
         * (-half, -half)
         * (-half, +half)
         * (+half, +half)
         * (+half, -half)
         *
         * 绕中心旋转。
         */

        float x1 =
                (-half * cos)
                        - (-half * sin);

        float z1 =
                (-half * sin)
                        + (-half * cos);

        float x2 =
                (-half * cos)
                        - (half * sin);

        float z2 =
                (-half * sin)
                        + (half * cos);

        float x3 =
                (half * cos)
                        - (half * sin);

        float z3 =
                (half * sin)
                        + (half * cos);

        float x4 =
                (half * cos)
                        - (-half * sin);

        float z4 =
                (half * sin)
                        + (-half * cos);

        /*
         * ========================================================
         * Buffer
         * ========================================================
         */
        BufferBuilder builder =
                Tesselator
                        .getInstance()
                        .getBuilder();

        builder.begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX
        );

        /*
         * 左上
         */
        builder.vertex(
                        matrix,
                        x1,
                        0.0F,
                        z1
                )
                .uv(
                        0.0F,
                        0.0F
                )
                .endVertex();

        /*
         * 左下
         */
        builder.vertex(
                        matrix,
                        x2,
                        0.0F,
                        z2
                )
                .uv(
                        0.0F,
                        1.0F
                )
                .endVertex();

        /*
         * 右下
         */
        builder.vertex(
                        matrix,
                        x3,
                        0.0F,
                        z3
                )
                .uv(
                        1.0F,
                        1.0F
                )
                .endVertex();

        /*
         * 右上
         */
        builder.vertex(
                        matrix,
                        x4,
                        0.0F,
                        z4
                )
                .uv(
                        1.0F,
                        0.0F
                )
                .endVertex();

        /*
         * ========================================================
         * Draw
         * ========================================================
         */
        BufferUploader.drawWithShader(
                builder.end()
        );

        /*
         * ========================================================
         * Restore Render State
         * ========================================================
         */

        RenderSystem.depthMask(true);

        RenderSystem.enableCull();

        RenderSystem.disableBlend();

        RenderSystem.setShaderColor(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );
    }

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