package fr.territoire.satvisual;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public final class SatelliteDrawable extends Drawable {
  private final float density;
  private VisualConfig cfg;
  SatelliteDrawable(float density, VisualConfig cfg) { this.density=density; this.cfg=cfg; }
  void setConfig(VisualConfig c) { cfg=c; invalidateSelf(); }
  @Override public void draw(Canvas canvas) {
    SatellitePainter.draw(canvas,getBounds().width(),getBounds().height(),density,cfg);
  }
  @Override public void setAlpha(int alpha) {}
  @Override public void setColorFilter(ColorFilter filter) {}
  @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
