package fr.territoire.satvisual;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
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

/**
 * V2 native-icon controller. The phone's actual network/satellite status is never modified.
 */
public final class MainActivity extends Activity {
    private VisualConfig cfg;
    private TextView resourceInfo;

    private int dp(int p) { return (int)(p * getResources().getDisplayMetrics().density + .5f); }

    private TextView label(String value,int sp,boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        if (bold) t.setTypeface(null,1);
        return t;
    }

    private void gap(LinearLayout container,int p) {
        container.addView(new View(this),new LinearLayout.LayoutParams(-1,dp(p)));
    }

    private void broadcastChange() {
        cfg.save(this);
        Intent i = new Intent(VisualConfig.REFRESH);
        i.setPackage("com.android.systemui");
        sendBroadcast(i);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = VisualConfig.read(this);
        getWindow().setStatusBarColor(Color.rgb(11,20,34));
        getWindow().setNavigationBarColor(Color.rgb(11,20,34));

        ScrollView scroller = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22),dp(24),dp(22),dp(32));
        page.setBackgroundColor(Color.rgb(11,20,34));
        scroller.addView(page);
        setContentView(scroller);

        page.addView(label("SATELLITE NTN",27,true));
        TextView kicker = label("SAMSUNG SYSTEMUI  •  ICÔNE NATIVE  •  V2.1",12,false);
        kicker.setTextColor(Color.rgb(102,195,239));
        page.addView(kicker);
        gap(page,20);

        TextView explanation = label(
            "Cette version crée un emplacement visuel indépendant dans la barre d'état Samsung. "
            + "Elle utilise le dessin satellite natif, sans simuler de connexion réseau.",16,false);
        explanation.setTextColor(Color.rgb(207,223,235));
        page.addView(explanation);
        gap(page,14);
        TextView warning=label("SIMULATION VISUELLE UNIQUEMENT\nAUCUN ACCÈS RÉEL À STARLINK / T-SATELLITE / SOS",12,true);
        warning.setTextColor(Color.rgb(255,193,108));
        page.addView(warning);
        gap(page,25);

        Switch toggle=new Switch(this);
        toggle.setText("Afficher l'icône satellite simulée");
        toggle.setTextSize(16);
        toggle.setTextColor(Color.WHITE);
        toggle.setChecked(cfg.enabled);
        toggle.setOnCheckedChangeListener((v, enabled)->{
            cfg.enabled=enabled;
            broadcastChange();
        });
        page.addView(toggle);

        gap(page,23);
        page.addView(label("Profil (étiquette de simulation)",15,true));
        Spinner modes=new Spinner(this);
        String[] names = {
            "Satellite NTN connecté (fictif)",
            "Satellite SOS (fictif)",
            "T-Satellite / Starlink (fictif)",
            "Recherche de satellite (fictive)"
        };
        modes.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        modes.setSelection(cfg.mode);
        modes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> v,View item,int idx,long id) {
                if (cfg.mode != idx) {cfg.mode=idx;broadcastChange();}
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> v) {}
        });
        page.addView(modes);
        gap(page,16);

        TextView strength=label("Niveau graphique : "+cfg.level+" / 4",15,true);
        page.addView(strength);
        SeekBar slider=new SeekBar(this);
        slider.setMax(4); slider.setProgress(cfg.level);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b,int progress,boolean fromUser) {
                if (fromUser) {cfg.level=progress; strength.setText("Niveau graphique : "+progress+" / 4"); broadcastChange();}
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        page.addView(slider);
        gap(page,20);

        Button apply=new Button(this);
        apply.setText("ACTUALISER SYSTEMUI");
        apply.setOnClickListener(v->broadcastChange());
        page.addView(apply);
        gap(page,12);
        resourceInfo=label("",13,false);
        resourceInfo.setTextColor(Color.rgb(164,207,236));
        page.addView(resourceInfo);
        Button inspect=new Button(this);
        inspect.setText("VÉRIFIER LES RESSOURCES SATELLITE");
        inspect.setOnClickListener(v->inspectResources());
        page.addView(inspect);
        inspectResources();

        gap(page,22);
        TextView help=label(
            "Installation : activer Satellite Visual dans LSPosed, périmètre System UI uniquement, "
            + "puis redémarrer. Si aucun indicateur n'apparaît, ouvrir les journaux LSPosed et "
            + "rechercher « SatelliteVisualNative ». Si SystemUI devient instable, désactiver le module et redémarrer. "
            + "Les versions Samsung utilisent parfois une pipeline différente : l'affichage n'est pas garanti.",13,false);
        help.setTextColor(Color.rgb(163,182,207));
        page.addView(help);
    }

    private void inspectResources() {
        try {
            Resources r=getPackageManager().getResourcesForApplication("com.android.systemui");
            String[] ids={"ic_satellite_connected_0","ic_satellite_connected_1",
                    "ic_satellite_connected_2","ic_satellite_not_connected",
                    "stat_sys_satellite_connected","stat_sys_satellite"};
            StringBuilder found=new StringBuilder();
            for(String name:ids) {
                int id=r.getIdentifier(name,"drawable","com.android.systemui");
                if(id!=0) found.append(name).append("\n");
            }
            if(found.length()==0) resourceInfo.setText("Ressources AOSP classiques introuvables. Un diagnostic des classes One UI est nécessaire.");
            else resourceInfo.setText("Ressources satellites système détectées :\n"+found);
        } catch(Exception e) {
            resourceInfo.setText("Impossible de lire les ressources SystemUI depuis l'app : "+e.getClass().getSimpleName());
        }
    }
}
