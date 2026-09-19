package de.tobi.voxelprint.util;

import java.util.Objects;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Everything the mod says in chat.
 *
 * <p>One prefix, one set of colours, one place to change either. Colours are
 * given as plain RGB rather than as the sixteen legacy codes: the mod is not
 * limited to a palette from 2011, and a named constant reads better at the call
 * site than a section sign and a letter.
 *
 * <p>The prefix is only the name in brackets. The messages themselves used to
 * repeat it -- every line came out as "[VoxelPrint] VoxelPrint: ..." -- which is
 * the sort of thing nobody notices while writing it and everybody notices while
 * reading it.
 */
public final class ChatUtil {

    /** Grey, for anything that is merely being reported. */
    public static final int INFO = 0xAAAAAA;
    /** The mod's green, for something that worked. */
    public static final int SUCCESS = 0x8AFFB3;
    /** Red, for something that did not. */
    public static final int ERROR = 0xFF5555;
    /** Blue, for a piece of text that does something when clicked. */
    public static final int ACTION = 0x7FC8FF;

    private static final int BRACKET = 0xF2FFF6;

    private ChatUtil() {
        throw new AssertionError("No instances.");
    }

    public static void info(FabricClientCommandSource source, String key, Object... args) {
        source.sendFeedback(line(INFO, key, args));
    }

    public static void info(LocalPlayer player, String key, Object... args) {
        player.sendSystemMessage(line(INFO, key, args));
    }

    public static void success(FabricClientCommandSource source, String key, Object... args) {
        source.sendFeedback(line(SUCCESS, key, args));
    }

    public static void success(LocalPlayer player, String key, Object... args) {
        player.sendSystemMessage(line(SUCCESS, key, args));
    }

    public static void error(FabricClientCommandSource source, String key, Object... args) {
        source.sendError(line(ERROR, key, args));
    }

    public static void error(LocalPlayer player, String key, Object... args) {
        player.sendSystemMessage(line(ERROR, key, args));
    }

    /**
     * Sends a row of clickable actions, prefixed like any other line.
     *
     * <p>Separated by a space and nothing else: brackets around each one are
     * already enough to say it can be clicked, and a row of separators between
     * bracketed words reads as punctuation soup.
     */
    public static void actions(LocalPlayer player, Component... actions) {
        player.sendSystemMessage(prefix().append(join(actions)));
    }

    public static void actions(FabricClientCommandSource source, Component... actions) {
        source.sendFeedback(prefix().append(join(actions)));
    }

    /**
     * A bracketed word that does something when clicked.
     *
     * @param key      what it says
     * @param hoverKey what the tooltip says, so the click is never a surprise
     * @param click    what happens
     */
    public static MutableComponent action(String key, String hoverKey, ClickEvent click) {
        Objects.requireNonNull(click, "click");
        return Component.literal("[")
                .append(Component.translatable(key))
                .append(Component.literal("]"))
                .setStyle(Style.EMPTY
                        .withColor(ACTION)
                        .withClickEvent(click)
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable(hoverKey))));
    }

    private static MutableComponent line(int colour, String key, Object... args) {
        return prefix().append(Component.translatable(key, args).withColor(colour));
    }

    private static MutableComponent prefix() {
        return Component.literal("[").withColor(BRACKET)
                .append(Component.literal("VoxelPrint").withColor(SUCCESS))
                .append(Component.literal("] ").withColor(BRACKET));
    }

    private static MutableComponent join(Component... parts) {
        MutableComponent row = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                row.append(Component.literal(" "));
            }
            row.append(parts[i]);
        }
        return row;
    }
}
