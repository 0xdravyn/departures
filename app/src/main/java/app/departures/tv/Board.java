package app.departures.tv;

import java.util.Arrays;

/**
 * A split-flap board: ROWS x COLS bits. Each bit shows one character of the flap set, or a colour chip.
 * The model is plain data; BoardView draws it and animates changes between two boards.
 */
public final class Board {
    public static final int ROWS = 6, COLS = 22;

    /** The flap order on each bit, as on the real thing: blank, letters, digits, punctuation, then the colour chips. */
    public static final String FLAPS = " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$&()-+=;:'\"%,.?/°";
    public static final char CHIP_RED = '\u0001', CHIP_ORANGE = '\u0002', CHIP_YELLOW = '\u0003', CHIP_GREEN = '\u0004',
            CHIP_BLUE = '\u0005', CHIP_VIOLET = '\u0006', CHIP_WHITE = '\u0007';
    public static final char[] CHIPS = {CHIP_RED, CHIP_ORANGE, CHIP_YELLOW, CHIP_GREEN, CHIP_BLUE, CHIP_VIOLET, CHIP_WHITE};
    public static final int[] CHIP_COLOURS = {0xFFD63431, 0xFFE8781E, 0xFFF1C426, 0xFF3CAA5A, 0xFF3278DC, 0xFF8C50C8, 0xFFEBEBEB};

    final char[] cells = new char[ROWS * COLS];

    public Board() { Arrays.fill(cells, ' '); }

    public char get(int row, int col) { return cells[row * COLS + col]; }

    public void set(int row, int col, char c) { if (row >= 0 && row < ROWS && col >= 0 && col < COLS) cells[row * COLS + col] = c; }

    /** Writes text on a row from a column, upper-cased, clipped at the right edge; unknown characters become spaces. */
    public void text(int row, int col, String s) {
        if (s == null) return;
        String up = s.toUpperCase();
        for (int i = 0; i < up.length() && col + i < COLS; i++) {
            char c = up.charAt(i);
            set(row, col + i, FLAPS.indexOf(c) >= 0 || isChip(c) ? c : ' ');
        }
    }

    /** Right-aligned text ending at the last column. */
    public void textRight(int row, String s) { if (s != null) text(row, Math.max(0, COLS - s.length()), s); }

    /** Centred on the row. */
    public void textCenter(int row, String s) { if (s != null) text(row, Math.max(0, (COLS - s.length()) / 2), s); }

    public void chip(int row, int col, char chip) { set(row, col, chip); }

    public static boolean isChip(char c) { return c >= CHIP_RED && c <= CHIP_WHITE; }

    public static int chipColour(char c) { return CHIP_COLOURS[c - CHIP_RED]; }

    /** Index of a character in the flap order; chips sit after the printable flaps. */
    public static int flapIndex(char c) {
        if (isChip(c)) return FLAPS.length() + (c - CHIP_RED);
        int i = FLAPS.indexOf(c);
        return i < 0 ? 0 : i;
    }

    public static int flapCount() { return FLAPS.length() + CHIPS.length; }

    public static char flapAt(int index) {
        int n = flapCount(); index = ((index % n) + n) % n;
        return index < FLAPS.length() ? FLAPS.charAt(index) : CHIPS[index - FLAPS.length()];
    }

    public Board copy() { Board b = new Board(); System.arraycopy(cells, 0, b.cells, 0, cells.length); return b; }

    @Override public boolean equals(Object o) { return o instanceof Board && Arrays.equals(cells, ((Board) o).cells); }

    @Override public int hashCode() { return Arrays.hashCode(cells); }
}
