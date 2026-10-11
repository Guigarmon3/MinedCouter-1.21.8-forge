package dev.blockcounter;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.block.Block;

/**
 * Compact HUD: world total of mined blocks (big) and this session's count (small).
 *
 * The total comes from vanilla statistics (Stats.BLOCK_MINED) read straight from the
 * integrated server, so it is always the real per-world number. The session is the
 * difference against the value read when the world was opened.
 */
final class BlockCounterHud {

    // ---- Tweak these ------------------------------------------------------
    /** Panel position in GUI pixels (top-left corner). */
    private static final int MARGIN_X = 4;
    private static final int MARGIN_Y = 4;
    /** 0 = always visible. >0 = hide the panel this many seconds after the last mined block. */
    private static final double AUTO_HIDE_SECONDS = 0;
    // -----------------------------------------------------------------------

    private static final long POLL_NANOS = 250_000_000L;
    private static final long ROLL_SINGLE_NANOS = 260_000_000L;
    private static final long ROLL_BATCH_NANOS = 500_000_000L;
    private static final long POP_NANOS = 90_000_000L;
    private static final long GAIN_NANOS = 500_000_000L;

    private static final float TOTAL_SCALE = 2.0F;
    private static final int PAD_X = 6;
    private static final int PAD_Y = 4;

    private static final int COLOR_BG = 0x9E0E0E12;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_GRAY = 0xFF9A9A9A;
    private static final int COLOR_GREEN = 0xFF6EE06E;

    // World-session state
    private static IntegratedServer currentServer;
    private static boolean ready;
    private static long sessionBase;
    private static long total;
    private static long lastPoll;
    private static long lastMineTime;

    // Animation state
    private static double shown;
    private static double rollFrom;
    private static long rollStart;
    private static long rollDuration = ROLL_SINGLE_NANOS;
    private static long popUntil;
    private static long gainStart;
    private static long gainAmount;

    private BlockCounterHud() {}

    static void render(GuiGraphics gg, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            currentServer = null;
            ready = false;
            return;
        }

        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            // Singleplayer only: draw nothing on remote servers.
            currentServer = null;
            ready = false;
            return;
        }

        long now = System.nanoTime();

        // New world opened: take a fresh baseline so the session starts at 0.
        if (server != currentServer) {
            currentServer = server;
            ready = false;
        }

        if (!ready || now - lastPoll >= POLL_NANOS) {
            lastPoll = now;
            long read = readTotal(mc, server);
            if (read < 0) {
                return; // stats not available yet, try again next frame
            }
            if (!ready) {
                ready = true;
                total = read;
                sessionBase = read;
                shown = read;
                rollFrom = read;
                lastMineTime = now - (long) (AUTO_HIDE_SECONDS * 1_000_000_000L) - 1;
            } else if (read != total) {
                long gained = read - total;
                rollFrom = shown;
                rollStart = now;
                rollDuration = gained > 1 ? ROLL_BATCH_NANOS : ROLL_SINGLE_NANOS;
                popUntil = now + POP_NANOS;
                if (gained > 0) {
                    gainAmount = (now - gainStart < GAIN_NANOS) ? gainAmount + gained : gained;
                    gainStart = now;
                    lastMineTime = now;
                }
                total = read;
            }
        }

        if (AUTO_HIDE_SECONDS > 0 && now - lastMineTime > (long) (AUTO_HIDE_SECONDS * 1_000_000_000L)) {
            return;
        }

        // Rolling number (ease-out cubic)
        double p = rollDuration <= 0 ? 1.0 : Math.min(1.0, (double) (now - rollStart) / rollDuration);
        double eased = 1.0 - Math.pow(1.0 - p, 3.0);
        shown = rollFrom + (total - rollFrom) * eased;
        if (p >= 1.0) {
            shown = total;
        }

        draw(gg, mc, now);
    }

    private static void draw(GuiGraphics gg, Minecraft mc, long now) {
        String totalText = format(Math.round(shown));
        long session = total - sessionBase;
        String label = I18n.get("hud.blockcounter.session") + " ";
        String sessionValue = "+" + format(session);

        int totalW = mc.font.width(totalText);
        int labelW = mc.font.width(label);
        int sessionW = labelW + mc.font.width(sessionValue);

        float popScale = now < popUntil ? 1.12F : 1.0F;

        int contentW = Math.max(Math.round(totalW * TOTAL_SCALE), sessionW);
        int totalH = Math.round(8 * TOTAL_SCALE);
        int w = contentW + PAD_X * 2;
        int h = PAD_Y * 2 + totalH + 2 + 8;

        int x = MARGIN_X;
        int y = MARGIN_Y;

        gg.fill(x, y, x + w, y + h, COLOR_BG);

        // Total (big), with a small "pop" on change
        gg.pose().pushMatrix();
        gg.pose().translate(x + PAD_X, y + PAD_Y);
        gg.pose().scale(TOTAL_SCALE * popScale, TOTAL_SCALE * popScale);
        gg.drawString(mc.font, totalText, 0, 0, COLOR_WHITE, true);
        gg.pose().popMatrix();

        // Session (small)
        int sy = y + PAD_Y + totalH + 2;
        gg.drawString(mc.font, label, x + PAD_X, sy, COLOR_GRAY, true);
        gg.drawString(mc.font, sessionValue, x + PAD_X + labelW, sy, COLOR_GREEN, true);

        // Floating "+N" that rises and fades in steps (very Minecraft)
        long gainAge = now - gainStart;
        if (gainAmount > 0 && gainAge < GAIN_NANOS) {
            double t = (double) gainAge / GAIN_NANOS;
            int step = (int) (t * 5);
            int alpha = 255 - step * 50;
            int rise = step;
            String plus = "+" + gainAmount;
            int px = x + w - PAD_X - mc.font.width(plus);
            int py = y + PAD_Y - rise;
            gg.drawString(mc.font, plus, px, py, (alpha << 24) | (COLOR_GREEN & 0x00FFFFFF), true);
        }
    }

    /** Sum of vanilla "blocks mined" for this player, or -1 if unavailable. */
    private static long readTotal(Minecraft mc, IntegratedServer server) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (player == null) {
                return -1;
            }
            ServerStatsCounter stats = player.getStats();
            long sum = 0;
            for (Block block : BuiltInRegistries.BLOCK) {
                sum += stats.getValue(Stats.BLOCK_MINED, block);
            }
            return sum;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** 1284 -> "1.284" (dot as thousands separator, matching the design). */
    private static String format(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder sb = new StringBuilder();
        int first = digits.length() % 3;
        if (first > 0) {
            sb.append(digits, 0, first);
        }
        for (int i = first; i < digits.length(); i += 3) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(digits, i, i + 3);
        }
        return value < 0 ? "-" + sb : sb.toString();
    }
}
