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
    private Board shown = new Board(), target = new Board();
    private final long[] flipStart = new long[Board.ROWS * Board.COLS];
    private final int[] flipFrom = new int[Board.ROWS * Board.COLS];
    private final int[] flipSteps = new int[Board.ROWS * Board.COLS];
    private boolean animating;
    private float dim = 1f;

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
                RectF rect = new RectF(x + 1, y, x + bitW - gap - 1, y + bitH);
                if (Board.isChip(ch)) {
                    tile.setColor(blend(Board.chipColour(ch), dim));
                    c.drawRoundRect(rect, radius, radius, tile);
                } else {
                    tile.setColor(tileColour);
                    c.drawRoundRect(rect, radius, radius, tile);
                    if (ch != ' ') {
                        glyph.setColor(glyphColour);
                        String s = String.valueOf(ch);
                        glyph.getTextBounds(s, 0, 1, bounds);
                        float baseline = y + bitH / 2f - (bounds.top + bounds.bottom) / 2f;
                        c.drawText(s, rect.centerX(), baseline, glyph);
                    }
                }
                c.drawRect(rect.left, y + bitH / 2f - 2, rect.right, y + bitH / 2f + 2, split);
            }
        }
        if (animating) { if (still) animating = false; else postInvalidateOnAnimation(); }
    }

    private static int blend(int colour, float k) {
        if (k >= 1f) return colour;
        int r = (int) (Color.red(colour) * k), g = (int) (Color.green(colour) * k), b = (int) (Color.blue(colour) * k);
        return Color.argb(255, r, g, b);
    }
}
