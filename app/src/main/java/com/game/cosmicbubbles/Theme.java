package com.game.cosmicbubbles;

import android.graphics.Color;

/** Colour sets. Each theme unlocks at a player level (later also purchasable). */
public final class Theme {
    public final String id;
    public final String name;
    public final int unlockLevel;
    public final boolean light;
    public final int bgTop, bgBottom, panel, cell, cellAlt, text, textDim, accent;
    /** Up to 4 segment colours. */
    public final int[] pieces;

    private Theme(String id, String name, int unlockLevel, boolean light, String bgTop, String bgBottom,
                  String panel, String cell, String cellAlt, String text, String textDim, String accent,
                  String... pieces) {
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
            new Theme("space", "Space", 1, false, "#0B1026", "#1B1446", "#151B3D", "#1A2248", "#1D264F",
                    "#F2F4FF", "#8F97C7", "#FFD166", "#3DDCFF", "#FF5DA2", "#9BE564", "#FFB547"),
            new Theme("ocean", "Ocean", 3, false, "#03263B", "#0A4B6B", "#062F48", "#0D3A55", "#0F4260",
                    "#EAF8FF", "#8CC3DB", "#FFE066", "#4FD1C5", "#FF8C69", "#FFE066", "#B794F4"),
            new Theme("candy", "Candy", 5, true, "#FFE4F1", "#E9D8FF", "#FFFFFF", "#FFF3F9", "#FBE8F3",
                    "#4A2545", "#9B6F93", "#FF4F9A", "#FF6FA8", "#3FA9F5", "#5CC93B", "#FF9F1C"),
            new Theme("minimal", "Minimal", 8, true, "#F4F4F2", "#E9E9E6", "#FFFFFF", "#F7F7F5", "#F1F1EE",
                    "#1E1E1E", "#7A7A7A", "#2F6FED", "#2F6FED", "#E5484D", "#30A46C", "#F5A524"),
    };

    public static Theme byId(String id) {
        for (Theme t : ALL) if (t.id.equals(id)) return t;
        return ALL[0];
    }

    public int piece(int color) {
        return color >= 0 && color < pieces.length ? pieces[color] : text;
    }
}
