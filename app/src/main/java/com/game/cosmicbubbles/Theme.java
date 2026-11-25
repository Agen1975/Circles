package com.game.cosmicbubbles;

import android.graphics.Color;

/**
 * Colour sets. Standard themes unlock at a player level. Seasonal themes ({@link #season} set) can
 * only be unlocked while their season runs, and are kept afterwards. Premium unlocks everything.
 */
public final class Theme {
    public final String id;
    public final String name;
    public final int unlockLevel;
    public final boolean light;
    public final int bgTop, bgBottom, panel, cell, cellAlt, text, textDim, accent;
    /** Up to 4 segment colours. */
    public final int[] pieces;
    /** Non-null for a limited-time theme. */
    public final Season season;
    /** Decoration: falling snow or twinkling stars on the background, extra sparkle when a circle pops. */
    public final boolean snow, stars, sparkle;

    private Theme(String id, String name, int unlockLevel, boolean light, String bgTop, String bgBottom,
                  String panel, String cell, String cellAlt, String text, String textDim, String accent,
                  String... pieces) {
        this(id, name, unlockLevel, null, false, false, false, light, bgTop, bgBottom, panel, cell, cellAlt, text,
                textDim, accent, pieces);
    }

    private Theme(String id, String name, int unlockLevel, Season season, boolean snow, boolean stars, boolean sparkle,
                  boolean light, String bgTop, String bgBottom, String panel, String cell, String cellAlt,
                  String text, String textDim, String accent, String[] pieces) {
        this.season = season;
        this.snow = snow;
        this.stars = stars;
        this.sparkle = sparkle;
        this.id = id;
        this.name = name;
        this.unlockLevel = unlockLevel;
        this.light = light;
        this.bgTop = Color.parseColor(bgTop);
        this.bgBottom = Color.parseColor(bgBottom);
        this.panel = Color.parseColor(panel);
        this.cell = Color.parseColor(cell);
        this.cellAlt = Color.parseColor(cellAlt);
        this.text = Color.parseColor(text);
        this.textDim = Color.parseColor(textDim);
        this.accent = Color.parseColor(accent);
        this.pieces = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) this.pieces[i] = Color.parseColor(pieces[i]);
    }

    public static final Theme[] ALL = {
            // Colourful twinkling starfield, as in the app icon.
            new Theme("space", "Space", 1, null, false, true, false, false,
                    "#0B1026", "#1B1446", "#151B3D", "#1A2248", "#1D264F",
                    "#F2F4FF", "#8F97C7", "#FFD166",
                    new String[]{"#3DDCFF", "#FF5DA2", "#9BE564", "#FFB547"}),
            new Theme("ocean", "Ocean", 3, false, "#03263B", "#0A4B6B", "#062F48", "#0D3A55", "#0F4260",
                    "#EAF8FF", "#8CC3DB", "#FFE066", "#4FD1C5", "#FF8C69", "#FFE066", "#B794F4"),
            new Theme("candy", "Candy", 5, true, "#FFE4F1", "#E9D8FF", "#FFFFFF", "#FFF3F9", "#FBE8F3",
                    "#4A2545", "#9B6F93", "#FF4F9A", "#FF6FA8", "#3FA9F5", "#5CC93B", "#FF9F1C"),
            new Theme("minimal", "Minimal", 8, true, "#F4F4F2", "#E9E9E6", "#FFFFFF", "#F7F7F5", "#F1F1EE",
                    "#1E1E1E", "#7A7A7A", "#2F6FED", "#2F6FED", "#E5484D", "#30A46C", "#F5A524"),
            // Red, green, gold and icy blue on a snowy night sky.
            new Theme("winter_lights", "Winter Lights", 0, Season.WINTER_LIGHTS, true, false, true, false,
                    "#081530", "#17345A", "#0F2142", "#132A4D", "#163056",
                    "#FFF8EC", "#A3B6D1", "#FFD36B",
                    new String[]{"#FF4D5E", "#38C97A", "#FFC94A", "#7AD3FF"}),
    };

    public static Theme byId(String id) {
        for (Theme t : ALL) if (t.id.equals(id)) return t;
        return ALL[0];
    }

    public int piece(int color) {
        return color >= 0 && color < pieces.length ? pieces[color] : text;
    }
}
