package client.nilore.event.impl;

import client.nilore.event.Event;

/**
 * 世界渲染结束事件 - 在每一帧世界渲染完成后调用
 * 用于绘制 3D 方框、线条等 ESP 渲染
 */
public class RenderWorldLastEvent extends Event {
    private final float partialTicks;

    public RenderWorldLastEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public float getPartialTicks() {
        return partialTicks;
    }
}