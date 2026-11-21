package fr.territoire.satvisual;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

/** Dessin de l'icône satellite et des barres artificielles. Aucune API modem. */
public final class SatellitePainter {
  private SatellitePainter() {}
  public static void draw(Canvas c,int width,int height,float density,VisualConfig cfg) {
    if (!cfg.enabled || width<1 || height<1) return;
    final float d = density;
    float start = width - cfg.offset*d - 72*d;
    c.save(); c.translate(start,height*0.5f);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(cfg.tone==1 ? Color.BLACK : Color.WHITE);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeWidth(1.5f*d);
    p.setStyle(Paint.Style.STROKE);
    c.save(); c.rotate(-35,10*d,0);
    c.drawRoundRect(6*d,-4*d,14*d,4*d,2*d,2*d,p);
    c.drawRect(-2*d,-3*d,3*d,3*d,p);
    c.drawRect(17*d,-3*d,22*d,3*d,p);
    c.drawLine(3*d,0,6*d,0,p); c.drawLine(14*d,0,17*d,0,p);
    c.drawLine(10*d,-4*d,13*d,-9*d,p);
    c.restore();
    p.setStyle(Paint.Style.FILL);
    p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
    p.setTextSize(9*d);
    String name = cfg.mode==1 ? "SOS" : cfg.mode==2 ? "SL" : cfg.mode==3 ? "..." : "NTN";
    c.drawText(name,26*d,0,dummy(p));
    p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
    p.setTextSize(6.5f*d);
    c.drawText("SIM",26*d,8*d,p);
    for(int i=0;i<4;i++) {
      p.setAlpha(i<cfg.level ? 255 : 65);
      float x = (50+i*5)*d;
      float h = (3+i*2)*d;
      c.drawRoundRect(x,8*d-h,x+3*d,8*d,d*0.5f,d*0.5f,p);
    }
    c.restore();
  }
  private static Paint dummy(Paint p) { return p; }
}
