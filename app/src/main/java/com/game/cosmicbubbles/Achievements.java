package com.game.cosmicbubbles;

import com.game.cosmicbubbles.game.GameConfig;
import com.game.cosmicbubbles.game.GameState;
import com.game.cosmicbubbles.game.MoveResult;

import java.util.ArrayList;
import java.util.List;

/** Achievement list and the checks that unlock them. Each unlock grants 1 hint token. */
public final class Achievements {
    public static final class Def {
        public final String id, title, desc;

        Def(String id, String title, String desc) {
            this.id = id;
            this.title = title;
            this.desc = desc;
        }
    }

    public static final Def[] ALL = {
            new Def("first_circle", "First circle", "Complete your first circle"),
            new Def("cluster2", "Double", "Make 2 circles in one move"),
            new Def("cluster3", "Triple", "Make 3 circles in one move"),
            new Def("streak3", "On a roll", "Score 3 moves in a row"),
            new Def("streak5", "Unstoppable", "Score 5 moves in a row"),
            new Def("board_clear", "Clean slate", "Clear the whole board"),
            new Def("bomb", "Kaboom", "Use a bomb"),
            new Def("score500", "500 club", "Score 500 in one game"),
            new Def("score1500", "High roller", "Score 1,500 in one game"),
            new Def("circles100", "Collector", "Make 100 circles in total"),
            new Def("daily_first", "Daily player", "Finish a daily puzzle"),
            new Def("daily7", "Week streak", "Play the daily puzzle 7 days in a row"),
            new Def("journey10", "Explorer", "Complete journey level 10"),
            new Def("three_stars", "Perfectionist", "Get 3 stars on a journey level"),
    };

    private Achievements() {
    }

    public static Def byId(String id) {
        for (Def d : ALL) if (d.id.equals(id)) return d;
        return null;
    }

    /** Checks after a move; returns newly unlocked achievements. */
    public static List<Def> afterMove(Progress p, MoveResult r, GameState st) {
        List<Def> out = new ArrayList<>();
        int made = r.circles.size() + r.luckyCircles.size();
        if (made > 0) check(p, out, "first_circle");
        if (r.circles.size() >= 2) check(p, out, "cluster2");
        if (r.circles.size() >= 3) check(p, out, "cluster3");
        if (st.streak >= 3) check(p, out, "streak3");
        if (st.streak >= 5) check(p, out, "streak5");
        if (r.boardCleared) check(p, out, "board_clear");
        if (r.bomb) check(p, out, "bomb");
        if (st.score >= 500) check(p, out, "score500");
        if (st.score >= 1500) check(p, out, "score1500");
        return out;
    }

    /** Checks after a game ends (progress already updated). */
    public static List<Def> afterGame(Progress p, GameConfig cfg, GameState st, int stars) {
        List<Def> out = new ArrayList<>();
        if (p.totalCircles() >= 100) check(p, out, "circles100");
        if (cfg.mode == GameConfig.Mode.DAILY) {
            check(p, out, "daily_first");
            if (p.dailyStreak() >= 7) check(p, out, "daily7");
        }
        if (cfg.mode == GameConfig.Mode.JOURNEY && st.won) {
            if (cfg.number >= 10) check(p, out, "journey10");
            if (stars >= 3) check(p, out, "three_stars");
        }
        return out;
    }

    private static void check(Progress p, List<Def> out, String id) {
        if (p.unlock(id)) out.add(byId(id));
    }
}
