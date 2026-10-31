package app.departures.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/**
 * Draws a Board full screen and flips bits from one board to the next.
 *
 * Geometry: a 2.5% side margin and 4.5% top and bottom; bit width from the columns, bit height from the rows,
 * so the board fills a 16:9 screen with tall flaps. Each bit is a dark rounded tile with the split line across
 * its middle; a character is one glyph in the bundled condensed face; a chip is a flat colour.
 *
 * Flips: when a new board is shown, every bit whose character differs runs through intermediate flaps, starting
 * a little later for each column (a wave from the left). Only then does the view redraw at 60 fps; at rest it
 * draws once per change. No bitmaps are cached: a frame is about 132 rounded rectangles and glyphs.
 */
public final class BoardView extends View {
    private static final long FLAP_MS = 38;          // one intermediate flap
    private static final long COLUMN_DELAY_MS = 22;  // wave from the left
    private static final int MAX_STEPS = 14;         // flaps shown between two characters, at most

    private final Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint split = new Paint();
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect bounds = new Rect();
    private final RectF cell = new RectF();   // reused for every bit: no per-frame allocations during a flip
    private Board shown = new Board(), target = new Board();
    private final long[] flipStart = new long[Board.ROWS * Board.COLS];
    private final int[] flipFrom = new int[Board.ROWS * Board.COLS];
    private final int[] flipSteps = new int[Board.ROWS * Board.COLS];
    private boolean animating;
    private float dim = 1f;
    private boolean flips = true;

    /** Off: changes appear at once, for anyone who finds the motion too much. */
    public void setFlips(boolean on) { flips = on; }

    public BoardView(Context ctx) {
        super(ctx);
        setBackgroundColor(Color.BLACK);
        glyph.setColor(0xFFEEEAE2);
        glyph.setTextAlign(Paint.Align.CENTER);
        try { glyph.setTypeface(Typeface.createFromAsset(ctx.getAssets(), "fonts/BarlowCondensed-SemiBold.ttf")); }
        catch (Exception e) { glyph.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); }
        split.setColor(0xFF070708);
    }

    /** Shows a board at once, no flips (first frame, or after quiet hours). */
    public void setBoard(Board b) { shown = b.copy(); target = b.copy(); animating = false; invalidate(); }

    /** Flips from the current board to this one. */
    public void flipTo(Board b) {
        if (b.equals(target)) return;
        if (!flips) { setBoard(b); return; }
        long now = SystemClock.uptimeMillis();
        Board from = target.copy();   // flips that are still running start from where they were heading
        target = b.copy();
        boolean any = false;
        for (int i = 0; i < Board.ROWS * Board.COLS; i++) {
            char a = from.cells[i], z = target.cells[i];
            if (a == z) continue;
            int steps = Math.floorMod(Board.flapIndex(z) - Board.flapIndex(a), Board.flapCount());
            flipFrom[i] = Board.flapIndex(a);
            flipSteps[i] = Math.min(steps, MAX_STEPS);
            flipStart[i] = now + (i % Board.COLS) * COLUMN_DELAY_MS;
            any = true;
        }
        if (any) { animating = true; postInvalidateOnAnimation(); } else { shown = target.copy(); invalidate(); }
    }

    /** 1 is normal; quiet hours use about 0.3. */
    public void setDim(float d) { dim = Math.max(0.1f, Math.min(1f, d)); invalidate(); }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        // True black behind the board: on an OLED those pixels are off (no wear), and the tiles get the contrast.
        c.drawColor(Color.BLACK);

        float mx = w * 0.025f, my = h * 0.045f;
        float bitW = (w - 2 * mx) / Board.COLS;
        float gap = bitW * 0.09f;
        float bitH = (h - 2 * my - (Board.ROWS - 1) * gap) / Board.ROWS;
        float x0 = (w - bitW * Board.COLS) / 2f, y0 = (h - (Board.ROWS * bitH + (Board.ROWS - 1) * gap)) / 2f;
        float radius = bitW * 0.085f;
        glyph.setTextSize(bitH * 0.80f);
        long now = SystemClock.uptimeMillis();
        boolean still = true;
        int tileColour = blend(0xFF171719, dim), glyphColour = blend(0xFFEEEAE2, dim);

        for (int r = 0; r < Board.ROWS; r++) {
            for (int col = 0; col < Board.COLS; col++) {
                int i = r * Board.COLS + col;
                float x = x0 + col * bitW, y = y0 + r * (bitH + gap);
                char ch = shown.cells[i];
                if (animating && shown.cells[i] != target.cells[i]) {
                    long t = now - flipStart[i];
                    if (t < 0) { still = false; }
                    else {
                        int step = (int) (t / FLAP_MS);
                        if (step >= flipSteps[i]) { shown.cells[i] = target.cells[i]; ch = shown.cells[i]; }
                        else { ch = Board.flapAt(flipFrom[i] + step * Math.max(1, Math.floorMod(Board.flapIndex(target.cells[i]) - flipFrom[i], Board.flapCount()) / Math.max(1, flipSteps[i]))); still = false; }
                    }
                }
                RectF rect = cell; rect.set(x + 1, y, x + bitW - gap - 1, y + bitH);
                if (Board.isChip(ch)) {
                    tile.setColor(blend(Board.chipColour(ch), dim));
                    c.drawRoundRect(rect, radius, radius, tile);
                } else if (Board.isWeather(ch)) {
                    tile.setColor(tileColour);
                    c.drawRoundRect(rect, radius, radius, tile);
                    weatherIcon(c, ch, rect, dim);
                } else {
                    tile.setColor(tileColour);
                    c.drawRoundRect(rect, radius, radius, tile);
                    if (ch != ' ') {
                        glyph.setColor(glyphColour);
                        String s = String.valueOf(ch);
                        glyph.getTextBounds(s, 0, 1, bounds);
                        // Centre each glyph in the bit, except the marks that live on the baseline: a centred
                        // full stop or comma reads as a middle dot ("7,580" became "7 · 580").
                        float baseline = (ch == '.' || ch == ',') ? y + bitH * 0.78f : y + bitH / 2f - (bounds.top + bounds.bottom) / 2f;
                        c.drawText(s, rect.centerX(), baseline, glyph);
                    }
                }
                c.drawRect(rect.left, y + bitH / 2f - 2, rect.right, y + bitH / 2f + 2, split);
            }
        }
        if (animating) { if (still) animating = false; else postInvalidateOnAnimation(); }
    }

    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Simple flat icons in the bit: a sun, a cloud, a cloud with drops, a snowflake. */
    private void weatherIcon(Canvas c, char ch, RectF r, float k) {
        float cx = r.centerX(), cy = r.centerY(), s = Math.min(r.width(), r.height()) * 0.62f;
        icon.setStyle(Paint.Style.FILL);
        if (ch == Board.WX_SUN) {
            icon.setColor(blend(0xFFF1C426, k)); icon.setStrokeWidth(s * 0.18f);
            c.drawCircle(cx, cy, s * 0.5f, icon);
            for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; c.drawLine(cx + (float) Math.cos(a) * s * 0.68f, cy + (float) Math.sin(a) * s * 0.68f, cx + (float) Math.cos(a) * s * 0.95f, cy + (float) Math.sin(a) * s * 0.95f, icon); }
            return;
        }
        int cloud = blend(ch == Board.WX_CLOUD ? 0xFFEBEBEB : 0xFFBFC6D1, k);
        icon.setColor(cloud);
        float top = ch == Board.WX_CLOUD ? cy : cy - s * 0.25f;
        c.drawCircle(cx - s * 0.35f, top, s * 0.38f, icon); c.drawCircle(cx + s * 0.1f, top - s * 0.25f, s * 0.48f, icon); c.drawCircle(cx + s * 0.5f, top + s * 0.02f, s * 0.36f, icon);
        c.drawRect(cx - s * 0.35f, top, cx + s * 0.5f, top + s * 0.38f, icon);
        if (ch == Board.WX_RAIN) {
            icon.setColor(blend(0xFF3278DC, k)); icon.setStrokeWidth(s * 0.16f);
            for (int i = -1; i <= 1; i++) c.drawLine(cx + i * s * 0.35f, top + s * 0.6f, cx + i * s * 0.35f - s * 0.12f, top + s * 1.0f, icon);
        } else if (ch == Board.WX_SNOW) {
            icon.setColor(blend(0xFFEBEBEB, k)); icon.setStrokeWidth(s * 0.12f);
            float sy = top + s * 0.85f;
            for (int i = 0; i < 3; i++) { double a = i * Math.PI / 3; c.drawLine(cx - (float) Math.cos(a) * s * 0.3f, sy - (float) Math.sin(a) * s * 0.3f, cx + (float) Math.cos(a) * s * 0.3f, sy + (float) Math.sin(a) * s * 0.3f, icon); }
        }
    }

    private static int blend(int colour, float k) {
        if (k >= 1f) return colour;
        int r = (int) (Color.red(colour) * k), g = (int) (Color.green(colour) * k), b = (int) (Color.blue(colour) * k);
        return Color.argb(255, r, g, b);
    }
}
