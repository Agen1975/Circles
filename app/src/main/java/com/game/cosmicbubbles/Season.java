package com.game.cosmicbubbles;

/**
 * Limited-time seasons. Dates are built in (no server): a season runs every year between two
 * month/day pairs, inclusive, and may wrap over New Year. A seasonal theme can only be unlocked
 * while its season is running; once unlocked it is kept for good.
 *
 * Dates are ints in the form yyyymmdd, matching {@link Progress#today()}.
 */
public final class Season {
    public final String id;
    public final String name;
    /** Month * 100 + day, e.g. 1201 for Dec 1. */
    public final int startMd, endMd;
    /** Human-readable last day, e.g. "Jan 6". */
    public final String endLabel;

    private Season(String id, String name, int startMd, int endMd, String endLabel) {
        this.id = id;
        this.name = name;
        this.startMd = startMd;
        this.endMd = endMd;
        this.endLabel = endLabel;
    }

    public static final Season WINTER_LIGHTS = new Season("winter_lights", "Winter Lights", 1201, 106, "Jan 6");

    public static final Season[] ALL = {WINTER_LIGHTS};

    /** True if {@code date} (yyyymmdd) falls inside this season. */
    public boolean activeOn(int date) {
        int md = date % 10000;
        return startMd <= endMd ? md >= startMd && md <= endMd : md >= startMd || md <= endMd;
    }

    public boolean active() {
        return activeOn(today());
    }

    /** The season running today, or null. */
    public static Season current() {
        int d = today();
        for (Season s : ALL) if (s.activeOn(d)) return s;
        return null;
    }

    /**
     * Today as yyyymmdd. Debug builds can pretend to be on another date with
     * {@code ./gradlew installDebug -PseasonDate=20261210}.
     */
    public static int today() {
        return BuildConfig.SEASON_DATE != 0 ? BuildConfig.SEASON_DATE : Progress.today();
    }
}
