package de.tobi.voxelprint.util;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ChatUtil {

    private ChatUtil() {
    }

    public static void info(FabricClientCommandSource source, String key, Object... args) {
        source.sendFeedback(formatMessage(key, 0xAAAAAA, args));
    }

    public static void info(LocalPlayer player, String key, Object... args) {
        player.sendSystemMessage(formatMessage(key, 0xAAAAAA, args));
    }

    public static void success(FabricClientCommandSource source, String key, Object... args) {
        source.sendFeedback(formatMessage(key, 0x8AFFB3, args));
    }

    public static void success(LocalPlayer player, String key, Object... args) {
        player.sendSystemMessage(formatMessage(key, 0x8AFFB3, args));
    }

    public static void error(FabricClientCommandSource source, String key, Object... args) {
        source.sendError(formatMessage(key, 0xFF5555, args));
    }

    public static void error(LocalPlayer player, String key, Object... args) {
        player.sendSystemMessage(formatMessage(key, 0xFF5555, args));
    }

    private static Component formatMessage(String key, int color, Object... args) {
        MutableComponent bracketOpen = Component.literal("[").withColor(0xF2FFF6); 
        MutableComponent prefixName = Component.literal("VoxelPrint").withColor(0x8AFFB3);
        MutableComponent bracketClose = Component.literal("] ").withColor(0xF2FFF6);

        MutableComponent content = Component.translatable(key, args).withColor(color);
        return bracketOpen.append(prefixName).append(bracketClose).append(content);
    }
}
