package fr.territoire.satvisual;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

/** Paramètres purement graphiques. Ne modifie aucun statut de connexion. */
public final class VisualConfig {
  static final String URI = "content://fr.territoire.satvisual.settings";
  static final String REFRESH = "fr.territoire.satvisual.REFRESH";
  boolean enabled = false;
  int mode = 0; // NTN, SOS, Starlink, Recherche (tous fictifs)
  int level = 3;
  int offset = 105;
  int tone = 0;
  static int cap(int n,int lo,int hi) { return Math.max(lo, Math.min(hi,n)); }
  static VisualConfig read(Context ctx) {
    SharedPreferences p = ctx.getSharedPreferences("visual",Context.MODE_PRIVATE);
    VisualConfig c = new VisualConfig();
    c.enabled = p.getBoolean("enabled",false);
    c.mode = cap(p.getInt("mode",0),0,3);
    c.level = cap(p.getInt("level",3),0,4);
    c.offset = cap(p.getInt("offset",105),0,250);
    c.tone = cap(p.getInt("tone",0),0,1);
    return c;
  }
  void save(Context ctx) {
    ctx.getSharedPreferences("visual",Context.MODE_PRIVATE).edit()
      .putBoolean("enabled",enabled).putInt("mode",mode)
      .putInt("level",level).putInt("offset",offset)
      .putInt("tone",tone).apply();
  }
  Bundle toBundle() {
    Bundle b = new Bundle();
    b.putBoolean("enabled",enabled); b.putInt("mode",mode); b.putInt("level",level);
    b.putInt("offset",offset); b.putInt("tone",tone);
    return b;
  }
  static VisualConfig fromBundle(Bundle b) {
    VisualConfig c = new VisualConfig();
    if (b == null) return c;
    c.enabled = b.getBoolean("enabled",false);
    c.mode = cap(b.getInt("mode",0),0,3);
    c.level = cap(b.getInt("level",3),0,4);
    c.offset = cap(b.getInt("offset",105),0,250);
    c.tone = cap(b.getInt("tone",0),0,1);
    return c;
  }
}
