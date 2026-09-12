package client.nilore.modules.impl.render.nametag;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import client.nilore.event.impl.PacketEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.modules.impl.render.NameTags;
import client.nilore.modules.impl.world.Teams;
import client.nilore.render.FontPresets;
import client.nilore.render.FontRenderer;
import client.nilore.render.Fonts;
import client.nilore.render.Paint;
import client.nilore.render.Renderer;
import client.nilore.render.RoundedRectangle;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.game.ItemAlertTracker;
import client.nilore.utils.math.MathUtil;
import client.nilore.utils.math.Vector2f;
import client.nilore.utils.render.ProjectionUtil;

public class OpalNameTag extends NameTagStyle {
    private static final int PADDING = new Color(0, 0, 0, 120).getRGB();
    private static final int COLOR_LIGHT_GRAY = Color.LIGHT_GRAY.getRGB();
    private static final int COLOR_WHITE = Color.WHITE.getRGB();
    private static final int COLOR_RED = new Color(255, 85, 85).getRGB();
    private static final int COLOR_GREEN = new Color(85, 255, 85).getRGB();
    private static final int COLOR_GOLD = new Color(255, 215, 0).getRGB();
    private static final int COLOR_AQUA = new Color(85, 255, 255).getRGB();

    public static final Map<String, AtomicInteger> scoreboardHealthMap = new ConcurrentHashMap<>();

    private final NumberSetting scaleSetting;
    private final NumberSetting distanceSetting;
    private final BooleanSetting showHealthSetting;
    private final BooleanSetting showArmorSetting;

    // ========== 引用主类的类型开关 ==========
    private final BooleanSetting showPlayersSetting;
    private final BooleanSetting showInvisiblePlayersSetting;
    private final BooleanSetting showMonstersSetting;
    private final BooleanSetting showAnimalsSetting;
    private final BooleanSetting showItemsSetting;
    private final BooleanSetting showOtherEntitiesSetting;

    private final FontRenderer mainFont;
    private final FontRenderer nameFont;
    private final FontRenderer iconFont;
    private final Paint paint;
    private final Map<Entity, Vector2f> entityPositions;
    private final Map<UUID, Long> itemCheckTimestamps;
    private final Map<String, String> decodedNameCache;
    private final Map<String, Long> nameDecodeTimestamps;
    private final DecimalFormat df = new DecimalFormat("#.#");

    public OpalNameTag() {
        super("Opal");
        this.scaleSetting = NameTags.INSTANCE.scaleSetting;
        this.distanceSetting = NameTags.INSTANCE.distanceSetting;
        this.showHealthSetting = NameTags.INSTANCE.showHealthSetting;
        this.showArmorSetting = NameTags.INSTANCE.showArmorSetting;

        // ========== 引用主类的类型开关 ==========
        this.showPlayersSetting = NameTags.INSTANCE.showPlayersSetting;
        this.showInvisiblePlayersSetting = NameTags.INSTANCE.showInvisiblePlayersSetting;
        this.showMonstersSetting = NameTags.INSTANCE.showMonstersSetting;
        this.showAnimalsSetting = NameTags.INSTANCE.showAnimalsSetting;
        this.showItemsSetting = NameTags.INSTANCE.showItemsSetting;
        this.showOtherEntitiesSetting = NameTags.INSTANCE.showOtherEntitiesSetting;

        this.mainFont = FontPresets.pingfang(28.0f);
        this.nameFont = Fonts.getRenderer("AstaSans-Medium.ttf", 28.0f);
        this.iconFont = Fonts.getRenderer("MaterialIcons-Regular.ttf", 28.0f);
        this.paint = new Paint();
        this.entityPositions = new ConcurrentHashMap<>();
        this.itemCheckTimestamps = new HashMap<>();
        this.decodedNameCache = new HashMap<>();
        this.nameDecodeTimestamps = new HashMap<>();
    }

    @Override
    public String getName() {
        return "Opal";
    }

    @Override
    public void onEnable() {
        this.entityPositions.clear();
        ItemAlertTracker.clear();
    }

    @Override
    public void onDisable() {
        this.onEnable();
    }

    // ========== 判断是否应该显示该实体 ==========
    private boolean shouldShowEntity(Entity entity) {
        if (entity == mc.player) return false;
        if (!entity.isAlive()) return false;

        double rangeSq = Math.pow(this.distanceSetting.getValue().doubleValue(), 2.0);
        if (entity.distanceToSqr(mc.player) > rangeSq) return false;
        if (entity.getName().getString().startsWith("CIT-")) return false;

        // ========== 隐身判断 ==========
        boolean isInvisible = entity.isInvisible();
        if (isInvisible && !this.showInvisiblePlayersSetting.getValue()) return false;

        // ========== 根据类型判断 ==========
        if (entity instanceof Player) {
            if (!this.showPlayersSetting.getValue()) return false;
            if (NameTags.INSTANCE.showPingSetting.getValue() && Teams.isSameTeam(entity)) return false;
            return true;
        }

        if (entity instanceof Monster) {
            return this.showMonstersSetting.getValue();
        }

        if (entity instanceof Animal) {
            return this.showAnimalsSetting.getValue();
        }

        if (entity instanceof net.minecraft.world.entity.item.ItemEntity) {
            return this.showItemsSetting.getValue();
        }

        // 其他实体（矿车、船、经验球、箭矢等）
        return this.showOtherEntitiesSetting.getValue();
    }

    private void updatePositions(RenderEvent event) {
        if (mc.level == null || mc.player == null) {
            this.entityPositions.clear();
            ItemAlertTracker.clear();
            return;
        }
        ProjectionUtil.updateMatrices();
        float partial = event.partialTick();
        HashSet<Entity> seen = new HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!shouldShowEntity(entity)) continue;

            double x = MathUtil.lerp(partial, entity.xo, entity.getX());
            double y = MathUtil.lerp(partial, entity.yo, entity.getY()) + entity.getBbHeight() + 0.5;
            double z = MathUtil.lerp(partial, entity.zo, entity.getZ());
            Vector2f screen = ProjectionUtil.project(x, y, z);
            if (screen == null) continue;
            screen.setY(screen.getY() - 2.0f);
            this.entityPositions.put(entity, screen);
            seen.add(entity);
        }
        this.entityPositions.keySet().removeIf(e -> !seen.contains(e));
        ItemAlertTracker.updateItems(seen);
    }

    @Override
    public void onRender(RenderEvent renderEvent) {
        try {
            this.updatePositions(renderEvent);
        } catch (Exception ignored) {
        }
    }

    // ========== 获取实体显示名称 ==========
    private String getEntityDisplayName(Entity entity) {
        if (entity instanceof Player) {
            return entity.getDisplayName().getString();
        } else {
            return entity.getName().getString();
        }
    }

    // ========== 获取实体类型图标 ==========
    private String getEntityTypeIcon(Entity entity) {
        if (entity instanceof Player) return "";
        if (entity instanceof Monster) return "";
        if (entity instanceof Animal) return "";
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity) return "";
        return "";
    }

    // ========== 获取实体名称颜色 ==========
    private int getEntityNameColor(Entity entity) {
        if (entity instanceof Player) {
            return !Teams.isSameTeam(entity) ? COLOR_RED : COLOR_GREEN;
        }
        if (entity instanceof Monster) {
            return COLOR_RED;
        }
        if (entity instanceof Animal) {
            return COLOR_GREEN;
        }
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity) {
            return COLOR_GOLD;
        }
        return COLOR_WHITE;
    }

    // ========== 获取实体血量 ==========
    private float getEntityHealth(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living.getHealth();
        }
        return -1;
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (this.entityPositions.isEmpty() || mc.level == null) {
            return;
        }
        float scale = this.scaleSetting.getValue().floatValue();
        int padding = 4;
        int gap = 4;
        float corner = 6.0f;
        boolean showHealth = this.showHealthSetting.getValue();
        boolean showArmor = this.showArmorSetting.getValue();

        List<ItemRenderData> deferredItems = new ArrayList<>();
        Renderer.renderConsumer(ctx -> {
            float ascent = this.mainFont.getMetrics().ascent();
            float mainLine = this.mainFont.getMetrics().getLineHeight();
            float nameLine = this.nameFont.getMetrics().getLineHeight();

            for (Map.Entry<Entity, Vector2f> entry : this.entityPositions.entrySet()) {
                Entity entity = entry.getKey();
                Vector2f screenPos = entry.getValue();
                screenPos.set(Math.round(screenPos.x), Math.round(screenPos.y));

                // ========== 获取基本信息 ==========
                String displayName = getEntityDisplayName(entity);
                String typeIcon = getEntityTypeIcon(entity);
                int nameColor = getEntityNameColor(entity);

                boolean isPlayer = entity instanceof AbstractClientPlayer;
                AbstractClientPlayer player = isPlayer ? (AbstractClientPlayer) entity : null;

                // ========== 血量 ==========
                String healthText = "";
                float health = getEntityHealth(entity);
                if (showHealth && health >= 0) {
                    healthText = df.format(health);
                }

                // ========== 距离 ==========
                int distance = (int) mc.player.distanceTo(entity);
                String distanceIcon = "";
                String distanceText = distance + "m";

                // ========== 吸收血量（仅玩家） ==========
                String absorbText = "";
                if (isPlayer && player != null) {
                    int absorb = Math.round(player.getAbsorptionAmount());
                    absorbText = absorb > 0 ? String.valueOf(absorb) : "";
                }

                // ========== 物品警报（仅玩家） ==========
                boolean hasAlerts = false;
                int alertCount = 0;
                Set<ItemStack> alertItems = new HashSet<>();
                if (isPlayer && player != null && showArmor) {
                    long now = System.currentTimeMillis();
                    UUID uuid = player.getUUID();
                    Long last = this.itemCheckTimestamps.get(uuid);
                    if (last == null || now - last >= 250L) {
                        ItemAlertTracker.trackPlayerItem(player, player.getMainHandItem());
                        ItemStack main = player.getMainHandItem();
                        if (ItemAlertTracker.isNewItem(main)) {
                            ItemAlertTracker.trackEntityItem(player, main);
                        }
                        ItemStack off = player.getOffhandItem();
                        if (ItemAlertTracker.isNewItem(off)) {
                            ItemAlertTracker.trackEntityItem(player, off);
                        }
                        this.itemCheckTimestamps.put(uuid, now);
                    }
                    alertItems = ItemAlertTracker.getEntityItems(player);
                    alertCount = showArmor && !alertItems.isEmpty() ? alertItems.size() : 0;
                    hasAlerts = alertCount > 0;
                }

                // ========== 计算尺寸 ==========
                float distIconW = this.iconFont.getBounds(distanceIcon).getWidth();
                float distTextW = this.nameFont.getBounds(distanceText).getWidth();
                float distBoxW = distIconW + 2.0f + distTextW + padding * 2;

                float typeIconW = this.iconFont.getBounds(typeIcon).getWidth();
                float displayW = this.mainFont.getBounds(displayName).getWidth();
                float nameBoxW = typeIconW + 6.0f + displayW + padding * 2;

                float healthIconW = this.iconFont.getBounds("\uE87D").getWidth();
                float healthTextW = healthText.isEmpty() ? 0 : this.nameFont.getBounds(healthText).getWidth();
                float healthBoxW = healthText.isEmpty() ? 0 : healthIconW + 2.0f + healthTextW + padding * 2;

                float absorbIconW = this.iconFont.getBounds("\uE87D").getWidth();
                float absorbTextW = absorbText.isEmpty() ? 0 : this.nameFont.getBounds(absorbText).getWidth();
                float absorbBoxW = absorbText.isEmpty() ? 0 : absorbIconW + 2.0f + absorbTextW + padding * 2;

                float rowHeight = Math.max(mainLine, nameLine) + padding * 2 - 4.0f;
                float itemBoxH = rowHeight;
                float itemTotalW = itemBoxH * alertCount + (alertCount > 0 ? gap * (alertCount - 1) : 0);

                int boxCount = 2;
                if (!healthText.isEmpty()) boxCount++;
                if (!absorbText.isEmpty()) boxCount++;
                boxCount += alertCount;

                float fullWidth = distBoxW + nameBoxW + healthBoxW + absorbBoxW + itemTotalW + gap * (boxCount > 1 ? boxCount - 1 : 0);
                float originX = -fullWidth / 2.0f;
                float originY = -rowHeight;

                // ========== 渲染 ==========
                ctx.save();
                ctx.translate(screenPos.x, screenPos.y);
                ctx.scale(scale, scale);
                float cursorX = originX;
                float textBaseline = originY + padding - 1.0f + ascent + 28.0f;

                // 距离框
                this.paint.setColor(PADDING);
                ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(cursorX, originY, distBoxW, rowHeight, corner), this.paint);
                this.paint.setColor(COLOR_LIGHT_GRAY);
                ctx.drawString(distanceIcon, cursorX + padding, textBaseline + 1.0f, this.iconFont, this.paint);
                ctx.drawString(distanceText, cursorX + padding + distIconW + 2.0f, textBaseline - 1.0f, this.nameFont, this.paint);
                cursorX += distBoxW + gap;

                // 名称框
                this.paint.setColor(PADDING);
                ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(cursorX, originY, nameBoxW + 2.0f, rowHeight, corner), this.paint);
                this.paint.setColor(COLOR_WHITE);
                ctx.drawString(typeIcon, cursorX + padding, textBaseline, this.iconFont, this.paint);
                this.paint.setColor(nameColor);
                ctx.drawString(displayName, cursorX + padding + typeIconW + 6.0f, textBaseline - 3.0f, this.mainFont, this.paint);
                cursorX += nameBoxW + gap;

                // 血量框
                if (!healthText.isEmpty()) {
                    this.paint.setColor(PADDING);
                    ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(cursorX, originY, healthBoxW, rowHeight, corner), this.paint);
                    this.paint.setColor(COLOR_RED);
                    ctx.drawString("\uE87D", cursorX + padding, textBaseline + 1.0f, this.iconFont, this.paint);

                    // 血量颜色随百分比变化
                    float healthPercent = health / 20.0f;
                    int healthColor;
                    if (healthPercent > 0.5f) {
                        healthColor = COLOR_GREEN;
                    } else if (healthPercent > 0.25f) {
                        healthColor = COLOR_GOLD;
                    } else {
                        healthColor = COLOR_RED;
                    }
                    this.paint.setColor(healthColor);
                    ctx.drawString(healthText, cursorX + padding + healthIconW + 2.5f, textBaseline - 1.0f, this.nameFont, this.paint);
                    cursorX += healthBoxW;
                }

                // 吸收血量（仅玩家）
                if (!absorbText.isEmpty()) {
                    cursorX += gap;
                    this.paint.setColor(PADDING);
                    ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(cursorX, originY, absorbBoxW, rowHeight, corner), this.paint);
                    this.paint.setColor(COLOR_GOLD);
                    ctx.drawString("\uE87D", cursorX + padding, textBaseline + 1.0f, this.iconFont, this.paint);
                    this.paint.setColor(COLOR_WHITE);
                    ctx.drawString(absorbText, cursorX + padding + absorbIconW + 2.5f, textBaseline, this.nameFont, this.paint);
                    cursorX += absorbBoxW;
                }

                // 物品警报（仅玩家）
                if (hasAlerts) {
                    for (ItemStack item : alertItems) {
                        if (ItemAlertTracker.hasItem(player.getUUID(), item.getItem())) continue;
                        cursorX += gap;
                        this.paint.setColor(PADDING);
                        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(cursorX, originY, itemBoxH, rowHeight, corner), this.paint);
                        float itemX = screenPos.x + cursorX * scale;
                        float itemY = screenPos.y + originY * scale;
                        float itemSize = rowHeight * scale;
                        float centerX = itemX + itemSize / 2.0f;
                        float centerY = itemY + itemSize / 2.0f;
                        deferredItems.add(new ItemRenderData(item, new Vector2f(centerX - 5.0f, centerY - 5.0f)));
                        cursorX += itemBoxH;
                    }
                }

                ctx.restore();
            }
        });

        for (ItemRenderData data : deferredItems) {
            PoseStack stack = event.guiGraphics().pose();
            stack.pushPose();
            stack.translate(data.position.x, data.position.y, 0.0f);
            stack.scale(scale, scale, 1.0f);
            event.guiGraphics().renderItem(data.itemStack, 0, 0);
            event.guiGraphics().renderItemDecorations(mc.font, data.itemStack, 0, 0);
            stack.popPose();
        }
    }

    @Override
    public void onPacket(PacketEvent packetEvent) {
        if (!(packetEvent.getPacket() instanceof ClientboundSetScorePacket packet)) return;
        if (mc.level == null || mc.player == null) return;
        String objective = packet.getObjectiveName();
        if (!"belowHealth".equals(objective) && !"health".equals(objective)) return;
        if (packet.getOwner().equals(mc.player.getGameProfile().getName())) return;
        scoreboardHealthMap.computeIfAbsent(packet.getOwner(), k -> new AtomicInteger()).set(packet.getScore());
    }

    private String getDecodedName(String name) {
        long now = System.currentTimeMillis();
        Long last = this.nameDecodeTimestamps.get(name);
        if (last != null && now - last < 1000L) {
            return this.decodedNameCache.get(name);
        }
        this.decodedNameCache.put(name, name);
        this.nameDecodeTimestamps.put(name, now);
        return name;
    }

    private record ItemRenderData(ItemStack itemStack, Vector2f position) {
    }
}