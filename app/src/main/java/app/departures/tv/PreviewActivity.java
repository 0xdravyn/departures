package app.departures.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

/**
 * The board on demand, with real data when a configuration is present and samples when not.
 * Left/Right switch faces, OK redraws the Now face, Down/Up test the quiet-hours dim, Back leaves.
 */
public final class PreviewActivity extends Activity implements Data.Listener {
    private BoardView view;
    private Config cfg;
    private Data data;
    private boolean upNext;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        view = new BoardView(this);
        setContentView(view);
        cfg = Config.load(this);
        if (cfg.present) { data = new Data(this, cfg, this); view.setBoard(Faces.now(System.currentTimeMillis(), cfg.clock24, null, null)); data.start(); }
        else view.setBoard(sample(false));
    }

    @Override protected void onDestroy() { if (data != null) data.stop(); super.onDestroy(); }

    @Override public void onData(Data d) { show(view.getWidth() > 0); }

    private Board current() {
        long now = System.currentTimeMillis();
        if (data == null) return sample(upNext);
        return upNext && data.resume != null ? Faces.upNext(data.resume) : Faces.now(now, cfg.clock24, data.weather, data.nextEvent(now));
    }

    private Board sample(boolean up) {
        long now = System.currentTimeMillis();
        return up ? Faces.upNext(Faces.sampleResume()) : Faces.now(now, false, Faces.sampleWeather(), Faces.sampleEvent(now));
    }

    private void show(boolean animate) { Board b = current(); if (animate) view.flipTo(b); else view.setBoard(b); }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_DPAD_LEFT: upNext = !upNext; show(true); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: show(true); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: view.setDim(cfg.quietDim); return true;
            case KeyEvent.KEYCODE_DPAD_UP: view.setDim(1f); return true;
            default: return super.onKeyDown(code, e);
        }
    }
}
