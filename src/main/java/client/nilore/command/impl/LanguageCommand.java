package client.nilore.command.impl;

import client.nilore.NiloreClient;
import client.nilore.command.Command;

public class LanguageCommand
extends Command {
    public static final class EventHandler {

        public EventHandler(LanguageCommand parent) {
        }
    }

    public LanguageCommand() {
        super("language", new String[]{"lang"});
    }

    @Override
    public void onCommand(String[] stringArray) {
        NiloreClient.getInstance().getEventBus().register(new LanguageCommand.EventHandler(this));
    }

    @Override
    public String[] onTab(String[] stringArray) {
        return new String[0];
    }
}