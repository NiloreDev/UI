package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.EntityRemoveEvent;
import client.nilore.event.impl.GameTickEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Random;

public final class KillEffect extends Module {

    private final BooleanSetting lightning =
            new BooleanSetting("Lightning", true);

    private final ModeSetting lightningMode =
            new ModeSetting(
                    "Lightning Mode",
                    "Thin",
                    "Vanilla"
            ).withDefault("Thin");

    private final BooleanSetting bloodExplosion =
            new BooleanSetting(
                    "Blood Explosion",
                    true
            );

    private final BooleanSetting explosion =
            new BooleanSetting(
                    "Explosion",
                    true
            );

    private final Random random = new Random();

    private LivingEntity target;

    private Vec3 lastTargetPos;

    private float lastTargetHeight;

    /*
     * Thin 闪电结束时间
     */
    private long thinLightningUntil;

    /*
     * Vanilla 原版 LightningBolt。
     *
     * 不把它 addFreshEntity 到世界，
     * 直接通过原版 EntityRenderDispatcher 渲染。
     */
    private LightningBolt vanillaBolt;

    private long vanillaLightningUntil;

    public KillEffect() {
        super(
                "KillEffect",
                Category.RENDER
        );
    }

    @Override
    public String getDisplayName() {
        return "KillEffect";
    }

    @Override
    public String getModuleName() {
        return "KillEffect";
    }

    @Override
    public void onTick() {
    }

    @Override
    protected void onEnable() {
        clearTarget();

        lastTargetPos = null;
        lastTargetHeight = 0.0F;

        thinLightningUntil = 0L;

        vanillaBolt = null;
        vanillaLightningUntil = 0L;
    }

    @Override
    protected void onDisable() {
        clearTarget();

        lastTargetPos = null;
        lastTargetHeight = 0.0F;

        thinLightningUntil = 0L;

        vanillaBolt = null;
        vanillaLightningUntil = 0L;
    }

    /*
     * 你这个 Nilore 项目里 EntityRemoveEvent
     * 同时被攻击 Patch 用来发送攻击目标。
     *
     * false = attack HEAD
     * true  = attack TAIL
     *
     * 所以这里只记录 false。
     */
    @EventTarget
    public void onAttack(
            EntityRemoveEvent event
    ) {

        if (
                mc.player == null
                        || mc.level == null
        ) {
            return;
        }

        if (event.dead()) {
            return;
        }

        Entity entity = event.entity();

        if (
                entity instanceof LivingEntity living
                        && living != mc.player
        ) {

            target = living;

            lastTargetPos =
                    living.position();

            lastTargetHeight =
                    living.getBbHeight();
        }
    }

    /*
     * 判断最后攻击的实体有没有死亡。
     */
    @EventTarget
    public void onGameTick(
            GameTickEvent event
    ) {

        if (
                mc.player == null
                        || mc.level == null
        ) {

            clearTarget();
            return;
        }

        if (target == null) {
            return;
        }

        /*
         * 活着就不断更新坐标。
         */
        if (target.isAlive()) {

            lastTargetPos =
                    target.position();

            lastTargetHeight =
                    target.getBbHeight();

            return;
        }

        /*
         * 死亡。
         */
        playKillEffect(
                lastTargetPos,
                lastTargetHeight
        );

        clearTarget();
    }

    private void playKillEffect(
            Vec3 pos,
            float height
    ) {

        if (
                pos == null
                        || mc.level == null
        ) {
            return;
        }

        /*
         * =====================================================
         * Lightning
         * =====================================================
         */
        if (lightning.getValue()) {

            /*
             * Thin
             *
             * 自绘细闪电。
             */
            if (lightningMode.is("Thin")) {

                thinLightningUntil =
                        System.currentTimeMillis()
                                + 650L;

                playLightningSound(pos);
            }

            /*
             * Vanilla
             *
             * 使用 Minecraft 的 LightningBolt +
             * EntityRenderDispatcher。
             */
            else if (
                    lightningMode.is(
                            "Vanilla"
                    )
            ) {

                createVanillaLightning(
                        pos
                );

                playLightningSound(pos);
            }
        }

        /*
         * =====================================================
         * Explosion
         * =====================================================
         */
        if (explosion.getValue()) {

            spawnExplosion(
                    pos,
                    height
            );
        }

        /*
         * =====================================================
         * Blood Explosion
         * =====================================================
         */
        if (bloodExplosion.getValue()) {

            spawnBloodExplosion(
                    pos,
                    height
            );
        }
    }

    private void playLightningSound(
            Vec3 pos
    ) {

        if (mc.level == null) {
            return;
        }

        mc.level.playLocalSound(
                pos.x,
                pos.y,
                pos.z,

                SoundEvents
                        .LIGHTNING_BOLT_THUNDER,

                SoundSource.WEATHER,

                4.0F,
                1.0F,

                false
        );

        mc.level.playLocalSound(
                pos.x,
                pos.y,
                pos.z,

                SoundEvents
                        .LIGHTNING_BOLT_IMPACT,

                SoundSource.WEATHER,

                2.0F,
                1.0F,

                false
        );
    }

    /*
     * =========================================================
     * Vanilla Lightning
     * =========================================================
     */
    private void createVanillaLightning(
            Vec3 pos
    ) {

        if (mc.level == null) {
            return;
        }

        LightningBolt bolt =
                EntityType
                        .LIGHTNING_BOLT
                        .create(
                                mc.level
                        );

        if (bolt == null) {
            return;
        }

        bolt.moveTo(
                pos.x,
                pos.y,
                pos.z
        );

        /*
         * 只是视觉闪电。
         */
        bolt.setVisualOnly(
                true
        );

        /*
         * 不 addFreshEntity。
         *
         * 直接存下来让 Renderer 绘制。
         */
        vanillaBolt = bolt;

        /*
         * 原版闪电本身出现时间较短。
         */
        vanillaLightningUntil =
                System.currentTimeMillis()
                        + 750L;
    }

    /*
     * =========================================================
     * Render
     * =========================================================
     */
    @EventTarget
    public void onRender(
            RenderEvent event
    ) {

        if (
                mc.player == null
                        || mc.level == null
        ) {
            return;
        }

        if (!lightning.getValue()) {
            return;
        }

        /*
         * Vanilla
         */
        if (
                lightningMode.is(
                        "Vanilla"
                )
        ) {

            renderVanillaLightning(
                    event
            );

            return;
        }

        /*
         * Thin
         */
        if (
                lightningMode.is(
                        "Thin"
                )
        ) {

            renderThinLightning(
                    event
            );
        }
    }

    /*
     * =========================================================
     * 原版粗闪电
     * =========================================================
     */
    private void renderVanillaLightning(
            RenderEvent event
    ) {

        if (vanillaBolt == null) {
            return;
        }

        if (
                System.currentTimeMillis()
                        >= vanillaLightningUntil
        ) {

            vanillaBolt = null;
            return;
        }

        Vec3 camera =
                mc.gameRenderer
                        .getMainCamera()
                        .getPosition();

        double renderX =
                vanillaBolt.getX()
                        - camera.x;

        double renderY =
                vanillaBolt.getY()
                        - camera.y;

        double renderZ =
                vanillaBolt.getZ()
                        - camera.z;

        PoseStack poseStack =
                event.poseStack();

        poseStack.pushPose();

        /*
         * 原版所有 EntityRenderer
         * 都走这个 BufferSource。
         */
        MultiBufferSource.BufferSource bufferSource =
                mc.renderBuffers()
                        .bufferSource();

        try {

            /*
             * 给 LightningBolt 增加 tickCount，
             * 避免一直卡在同一帧。
             */
            vanillaBolt.tickCount++;

            /*
             * Minecraft 原版实体渲染器。
             *
             * LightningBolt 会自动匹配
             * LightningBoltRenderer。
             */
            mc.getEntityRenderDispatcher()
                    .render(
                            vanillaBolt,

                            renderX,
                            renderY,
                            renderZ,

                            vanillaBolt.getYRot(),

                            event.partialTick(),

                            poseStack,

                            bufferSource,

                            0xF000F0
                    );

            /*
             * 提交 Renderer 产生的顶点。
             */
            bufferSource.endBatch();

        } catch (Throwable throwable) {

            /*
             * 如果当前映射下原版实体 renderer
             * 出现异常，不让整个游戏崩掉。
             */
            throwable.printStackTrace();

            vanillaBolt = null;
        }

        poseStack.popPose();
    }

    /*
     * =========================================================
     * Thin Lightning
     * =========================================================
     */
    private void renderThinLightning(
            RenderEvent event
    ) {

        if (lastTargetPos == null) {
            return;
        }

        if (
                System.currentTimeMillis()
                        >= thinLightningUntil
        ) {
            return;
        }

        Vec3 camera =
                mc.gameRenderer
                        .getMainCamera()
                        .getPosition();

        PoseStack poseStack =
                event.poseStack();

        poseStack.pushPose();

        poseStack.translate(

                lastTargetPos.x
                        - camera.x,

                lastTargetPos.y
                        - camera.y,

                lastTargetPos.z
                        - camera.z
        );

        RenderSystem.enableBlend();

        RenderSystem.defaultBlendFunc();

        RenderSystem.disableDepthTest();

        RenderSystem.depthMask(
                false
        );

        RenderSystem.disableCull();

        RenderSystem.setShader(
                GameRenderer
                        ::getPositionColorShader
        );

        /*
         * Thin 的粗细。
         */
        RenderSystem.lineWidth(
                2.0F
        );

        Matrix4f matrix =
                poseStack
                        .last()
                        .pose();

        /*
         * 中间一条 + 左右两条。
         */
        for (
                int i = 0;
                i < 3;
                i++
        ) {

            drawThinBolt(
                    matrix,
                    i
            );
        }

        RenderSystem.lineWidth(
                1.0F
        );

        RenderSystem.depthMask(
                true
        );

        RenderSystem.enableDepthTest();

        RenderSystem.enableCull();

        RenderSystem.disableBlend();

        poseStack.popPose();
    }

    private void drawThinBolt(
            Matrix4f matrix,
            int boltIndex
    ) {

        BufferBuilder buffer =
                Tesselator
                        .getInstance()
                        .getBuilder();

        buffer.begin(
                VertexFormat.Mode
                        .DEBUG_LINE_STRIP,

                DefaultVertexFormat
                        .POSITION_COLOR
        );

        /*
         * 三条线的位置略微错开。
         */
        float startX =
                (boltIndex - 1)
                        * 0.055F;

        float startZ =
                (1 - boltIndex)
                        * 0.04F;

        /*
         * 从目标上方开始。
         */
        float height =
                Math.max(
                        7.0F,
                        lastTargetHeight
                                + 5.5F
                );

        /*
         * 第一段。
         */
        buffer.vertex(
                        matrix,

                        startX,
                        height,
                        startZ
                )
                .color(
                        235,
                        245,
                        255,
                        245
                )
                .endVertex();

        final int segments = 12;

        /*
         * 每隔几十 ms 改一次形状。
         */
        long timeSeed =
                System.currentTimeMillis()
                        / 45L;

        Random renderRandom =
                new Random(
                        timeSeed
                                * 31L
                                + boltIndex
                                * 9176L
                );

        float x = startX;
        float z = startZ;

        for (
                int i = 1;
                i <= segments;
                i++
        ) {

            float progress =
                    i / (float) segments;

            float y =
                    height
                            * (
                            1.0F
                                    - progress
                    );

            /*
             * 越靠近目标，
             * 抖动越小。
             */
            float strength =
                    1.0F
                            - progress;

            x += (
                    renderRandom
                            .nextFloat()
                            - 0.5F
            )
                    * 0.75F
                    * strength;

            z += (
                    renderRandom
                            .nextFloat()
                            - 0.5F
            )
                    * 0.75F
                    * strength;

            /*
             * 最后一段回到目标中心。
             */
            if (i == segments) {

                x =
                        startX
                                * 0.2F;

                z =
                        startZ
                                * 0.2F;

                y = 0.05F;
            }

            int alpha =
                    Math.max(
                            130,

                            250
                                    - i
                                    * 6
                    );

            buffer.vertex(
                            matrix,
                            x,
                            y,
                            z
                    )
                    .color(
                            185,
                            220,
                            255,
                            alpha
                    )
                    .endVertex();
        }

        BufferUploader.drawWithShader(
                buffer.end()
        );
    }

    /*
     * =========================================================
     * Explosion
     * =========================================================
     */
    private void spawnExplosion(
            Vec3 pos,
            float height
    ) {

        if (mc.level == null) {
            return;
        }

        /*
         * 火焰。
         */
        for (
                int i = 0;
                i < 45;
                i++
        ) {

            double velocityX =
                    (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.7;

            double velocityY =
                    random.nextDouble()
                            * 0.55;

            double velocityZ =
                    (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.7;

            mc.level.addParticle(
                    ParticleTypes.FLAME,

                    pos.x
                            + (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.5,

                    pos.y
                            + random.nextDouble()
                            * Math.max(
                            0.8,
                            height
                    ),

                    pos.z
                            + (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.5,

                    velocityX,
                    velocityY,
                    velocityZ
            );
        }

        /*
         * 白烟。
         */
        for (
                int i = 0;
                i < 12;
                i++
        ) {

            mc.level.addParticle(
                    ParticleTypes.POOF,

                    pos.x
                            + (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.7,

                    pos.y
                            + random.nextDouble()
                            * Math.max(
                            1.0,
                            height
                    ),

                    pos.z
                            + (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.7,

                    (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.2,

                    random.nextDouble()
                            * 0.2,

                    (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.2
            );
        }

        mc.level.playLocalSound(
                pos.x,
                pos.y,
                pos.z,

                SoundEvents
                        .FIRECHARGE_USE,

                SoundSource.PLAYERS,

                1.0F,
                0.8F,

                false
        );
    }

    /*
     * =========================================================
     * Blood
     * =========================================================
     */
    private void spawnBloodExplosion(
            Vec3 pos,
            float height
    ) {

        if (mc.level == null) {
            return;
        }

        /*
         * 红石块 BLOCK particle。
         */
        BlockParticleOption blood =
                new BlockParticleOption(
                        ParticleTypes.BLOCK,

                        Blocks.REDSTONE_BLOCK
                                .defaultBlockState()
                );

        for (
                int i = 0;
                i < 110;
                i++
        ) {

            double particleY =
                    pos.y
                            + random.nextDouble()
                            * Math.max(
                            1.0,
                            height + 0.4
                    );

            double velocityX =
                    (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.65;

            double velocityY =
                    (
                            random.nextDouble()
                                    - 0.1
                    )
                            * 0.5;

            double velocityZ =
                    (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.65;

            mc.level.addParticle(
                    blood,

                    pos.x
                            + (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.45,

                    particleY,

                    pos.z
                            + (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.45,

                    velocityX,
                    velocityY,
                    velocityZ
            );
        }
    }

    private void clearTarget() {
        target = null;
    }
}