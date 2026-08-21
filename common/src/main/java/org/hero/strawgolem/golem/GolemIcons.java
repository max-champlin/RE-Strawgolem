package org.hero.strawgolem.golem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * The little glyphs that mark rank and immortality on a golem's name.
 *
 * <p>You cannot put an item sprite into a {@link Component} - entity names are
 * text, and the name renderer only draws glyphs. The way round it is a bitmap
 * FONT: {@code assets/strawgolem/font/icons.json} maps two private-use
 * codepoints onto a texture sheet, and {@code Style.withFont} points a piece of
 * text at that font. The result is a real texture drawn inline, and because it
 * is ordinary text it works everywhere a name works - the nameplate, Jade, the
 * Foreman's Clipboard, chat.
 *
 * <p>Deliberately a font of our OWN ({@code strawgolem:icons}) rather than an
 * override of {@code minecraft:default}: font files do not merge, they replace,
 * so shipping our own default.json would stomp on vanilla's and on any other
 * pack that does the same.
 *
 * <p>The soul is drawn WHITE on purpose. Bitmap glyphs are multiplied by the
 * text colour, so white is what lets the core keep its own gold-and-cyan; any
 * other colour would stain it.
 */
public final class GolemIcons {

    private GolemIcons() {}

    private static final ResourceLocation FONT =
            ResourceLocation.fromNamespaceAndPath("strawgolem", "icons");

    /** Private-use codepoints - see the matching "chars" in icons.json. */
    private static final String SOUL = new String(Character.toChars(0xE000));
    private static final String PIP  = new String(Character.toChars(0xE001));

    private static MutableComponent glyph(String chars, ChatFormatting colour) {
        return Component.literal(chars)
                .withStyle(style -> style.withFont(FONT).applyFormat(colour));
    }

    /**
     * One pip per rank: Journeyman gets two, Master three. Apprentice gets
     * nothing, which keeps a fresh hire's name clean and matches how the old
     * text version stayed silent at rank 0.
     */
    public static MutableComponent rank(int rank) {
        int pips = Math.max(0, Math.min(3, rank + 1));
        StringBuilder sb = new StringBuilder(" ");
        sb.append(PIP.repeat(pips));
        // Master is the one worth spotting across a field, so it goes gold.
        return glyph(sb.toString(), rank >= 2 ? ChatFormatting.GOLD : ChatFormatting.GRAY);
    }

    /** The immortal core, in white so the texture's own colours survive. */
    public static MutableComponent soul() {
        return glyph(" " + SOUL, ChatFormatting.WHITE);
    }
}
