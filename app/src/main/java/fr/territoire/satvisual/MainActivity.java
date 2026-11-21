package fr.territoire.satvisual;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

/** Interface tactile mobile, aucune autorisation root ou réseau. */
public final class MainActivity extends Activity {
  private VisualConfig cfg;
  private Preview preview;
  private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
  private TextView text(String s,int size,boolean strong) {
    TextView t=new TextView(this);
    t.setText(s); t.setTextSize(size); t.setTextColor(Color.WHITE);
    if (strong) t.setTypeface(null,1);
    return t;
  }
  private void space(LinearLayout column,int size) {
    column.addView(new View(this),new LinearLayout.LayoutParams(-1,dp(size)));
  }
  private void save() {
    cfg.save(this);
    if (preview!=null) preview.invalidate();
    Intent i=new Intent(VisualConfig.REFRESH);
    i.setPackage("com.android.systemui");
    sendBroadcast(i);
  }
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    cfg=VisualConfig.read(this);
    getWindow().setStatusBarColor(Color.rgb(12,20,32));
    getWindow().setNavigationBarColor(Color.rgb(12,20,32));
    ScrollView scroll=new ScrollView(this);
    LinearLayout column=new LinearLayout(this);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setPadding(dp(18),dp(20),dp(18),dp(30));
    column.setBackgroundColor(Color.rgb(15,23,37));
    scroll.addView(column); setContentView(scroll);
    column.addView(text("🛰 SATELLITE VISUAL",23,true));
    column.addView(text("Simulation graphique pour LSPosed et Samsung One UI",13,false));
    space(column,15);
    preview=new Preview();
    column.addView(preview,new LinearLayout.LayoutParams(-1,dp(90)));
    space(column,8);
    TextView warning=text("SIMULATION UNIQUEMENT : PAS DE RÉSEAU NI DE SOS RÉEL",12,true);
    warning.setTextColor(Color.rgb(255,204,116)); column.addView(warning);
    space(column,20);
    Switch toggle=new Switch(this);
    toggle.setText("Afficher l'indicateur simulé");
    toggle.setTextColor(Color.WHITE); toggle.setTextSize(16); toggle.setChecked(cfg.enabled);
    toggle.setOnCheckedChangeListener((button,checked)->{cfg.enabled=checked; save();});
    column.addView(toggle);
    space(column,18);
    column.addView(text("Mode graphique",15,true));
    Spinner modes=new Spinner(this);
    modes.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,
       new String[]{"Satellite NTN fictif","Satellite SOS fictif","Starlink fictif","Recherche fictive"}));
    modes.setSelection(cfg.mode);
    modes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(android.widget.AdapterView<?> a,View v,int pos,long id) { cfg.mode=pos; save(); }
      @Override public void onNothingSelected(android.widget.AdapterView<?> a) {}
    }); column.addView(modes);
    space(column,14);
    TextView levelText=text("Signal simulé : "+cfg.level+"/4",15,true);
    column.addView(levelText);
    SeekBar signal=new SeekBar(this); signal.setMax(4); signal.setProgress(cfg.level);
    signal.setOnSeekBarChangeListener(change((int n)->{
      cfg.level=n; levelText.setText("Signal simulé : "+n+"/4"); save();
    })); column.addView(signal);
    space(column,12);
    TextView offsetText=text("Position : "+cfg.offset+" dp depuis la droite",15,true);
    column.addView(offsetText);
    SeekBar offset=new SeekBar(this); offset.setMax(250); offset.setProgress(cfg.offset);
    offset.setOnSeekBarChangeListener(change((int n)->{
      cfg.offset=n; offsetText.setText("Position : "+n+" dp depuis la droite"); save();
    })); column.addView(offset);
    space(column,13);
    column.addView(text("Contraste",15,true));
    Spinner tone=new Spinner(this);
    tone.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,
       new String[]{"Blanc (fond sombre)","Noir (fond clair)"}));
    tone.setSelection(cfg.tone);
    tone.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(android.widget.AdapterView<?> a,View v,int pos,long id) { cfg.tone=pos; save(); }
      @Override public void onNothingSelected(android.widget.AdapterView<?> a) {}
    }); column.addView(tone);
    space(column,15);
    Button refresh=new Button(this); refresh.setText("ACTUALISER L'AFFICHAGE");
    refresh.setOnClickListener(v->save()); column.addView(refresh);
    space(column,10);
    TextView help=text("Active le module dans LSPosed avec le périmètre « System UI », redémarre, puis active la simulation ici. En cas de problème, désactive LSPosed pour ce module et redémarre. Logs : SatelliteVisual.",13,false);
    help.setTextColor(Color.rgb(175,195,218)); column.addView(help);
  }
  private interface IntChange { void changed(int n); }
  private SeekBar.OnSeekBarChangeListener change(IntChange callback) {
    return new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar b,int n,boolean user) { if(user) callback.changed(n); }
      @Override public void onStartTrackingTouch(SeekBar b) {}
      @Override public void onStopTrackingTouch(SeekBar b) {}
    };
  }
  private final class Preview extends View {
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    Preview(){super(MainActivity.this);}
    @Override protected void onDraw(Canvas c) {
      p.setColor(cfg.tone==1?Color.rgb(230,238,248):Color.rgb(30,48,68));
      c.drawRoundRect(0,0,getWidth(),getHeight(),dp(12),dp(12),p);
      p.setColor(cfg.tone==1?Color.BLACK:Color.WHITE);
      p.setTextSize(dp(12)); c.drawText("12:34",dp(12),dp(25),p);
      c.drawText("APERÇU SIMULÉ",dp(12),dp(65),p);
      SatellitePainter.draw(c,getWidth(),dp(44),getResources().getDisplayMetrics().density,cfg);
    }
  }
}
