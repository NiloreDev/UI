package client.nilore.settings.impl;

import client.nilore.settings.Setting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class TextSetting extends Setting<String> {

    private String value;

    public TextSetting(String name, String defaultValue) {
        super(name, defaultValue);
        this.value = defaultValue;
    }

    @Override
    public String getValue() {
        return this.value;
    }

    @Override
    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public void save(JsonObject var1) {

    }

    @Override
    public void load(JsonElement var1) {

    }

    public void setValue(String value, boolean call) {
        this.value = value;
        if (call) {
            this.onUpdate();
        }
    }

    private void onUpdate() {
        // 可以在这里添加更新逻辑
    }
}