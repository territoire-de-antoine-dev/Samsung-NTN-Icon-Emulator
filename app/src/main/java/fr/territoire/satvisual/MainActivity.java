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
        // Send only non-sensitive display settings, so SystemUI needn't resolve
        // the app's ContentProvider during early Android startup.
        i.putExtra("enabled", cfg.enabled);
        i.putExtra("mode", cfg.mode);
        i.putExtra("level", cfg.level);
        i.putExtra("offset", cfg.offset);
        i.putExtra("tone", cfg.tone);
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

        page.addView(label(getString(R.string.title),27,true));
        TextView kicker = label(getString(R.string.kicker),12,false);
        kicker.setTextColor(Color.rgb(102,195,239));
        page.addView(kicker);
        gap(page,20);

        TextView explanation = label(
            getString(R.string.explanation),16,false);
        explanation.setTextColor(Color.rgb(207,223,235));
        page.addView(explanation);
        gap(page,14);
        TextView warning=label(getString(R.string.warning),12,true);
        warning.setTextColor(Color.rgb(255,193,108));
        page.addView(warning);
        gap(page,25);

        Switch toggle=new Switch(this);
        toggle.setText(R.string.toggle);
        toggle.setTextSize(16);
        toggle.setTextColor(Color.WHITE);
        toggle.setChecked(cfg.enabled);
        toggle.setOnCheckedChangeListener((v, enabled)->{
            cfg.enabled=enabled;
            broadcastChange();
        });
        page.addView(toggle);

        gap(page,23);
        page.addView(label(getString(R.string.profile_label),15,true));
        Spinner modes=new Spinner(this);
        String[] names = getResources().getStringArray(R.array.simulation_modes);
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

        TextView strength=label(getString(R.string.level_label,cfg.level),15,true);
        page.addView(strength);
        SeekBar slider=new SeekBar(this);
        slider.setMax(4); slider.setProgress(cfg.level);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b,int progress,boolean fromUser) {
                if (fromUser) {cfg.level=progress; strength.setText(getString(R.string.level_label,progress)); broadcastChange();}
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        page.addView(slider);
        gap(page,20);

        Button apply=new Button(this);
        apply.setText(R.string.refresh);
        apply.setOnClickListener(v->broadcastChange());
        page.addView(apply);
        gap(page,12);
        resourceInfo=label("",13,false);
        resourceInfo.setTextColor(Color.rgb(164,207,236));
        page.addView(resourceInfo);
        Button inspect=new Button(this);
        inspect.setText(R.string.check_resources);
        inspect.setOnClickListener(v->inspectResources());
        page.addView(inspect);
        inspectResources();

        gap(page,22);
        TextView help=label(
            getString(R.string.help),13,false);
        help.setTextColor(Color.rgb(163,182,207));
        page.addView(help);
    }

    @Override protected void onResume() {
        super.onResume();
        if (cfg != null) broadcastChange();
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
            if(found.length()==0) resourceInfo.setText(R.string.no_resources);
            else resourceInfo.setText(getString(R.string.resources_found,found.toString()));
        } catch(Exception e) {
            resourceInfo.setText(getString(R.string.resources_error,e.getClass().getSimpleName()));
        }
    }
}
