package org.sfcta.cycletracks;

import java.util.Map;
import java.util.Map.Entry;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

public class UserInfoActivity extends Activity {
    public final static int PREF_AGE = 1;
    public final static int PREF_ZIPHOME = 2;
    public final static int PREF_ZIPWORK = 3;
    public final static int PREF_ZIPSCHOOL = 4;
    public final static int PREF_EMAIL = 5;
    public final static int PREF_GENDER = 6;
    public final static int PREF_CYCLEFREQ = 7;

    final String[] freqDesc = {"Less than once a month", "Several times a month", "Several times per week", "Daily"};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.userprefs);

        SeekBar sb = (SeekBar) findViewById(R.id.SeekCycleFreq);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar arg0) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onStartTrackingTouch(SeekBar arg0) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
                TextView tv = (TextView) findViewById(R.id.TextFreq);
                tv.setText(freqDesc[arg1/100]);
            }
        });

        SharedPreferences settings = getSharedPreferences("PREFS", 0);
        Map <String, ?> prefs = settings.getAll();
        for (Entry <String, ?> p : prefs.entrySet()) {
            int key = Integer.parseInt(p.getKey());
            CharSequence value = (CharSequence) p.getValue();

            switch (key) {
            case PREF_AGE:
                ((EditText)findViewById(R.id.TextAge)).setText(value);
                break;
            case PREF_ZIPHOME:
                ((EditText)findViewById(R.id.TextZipHome)).setText(value);
                break;
            case PREF_ZIPWORK:
                ((EditText)findViewById(R.id.TextZipWork)).setText(value);
                break;
            case PREF_ZIPSCHOOL:
                ((EditText)findViewById(R.id.TextZipSchool)).setText(value);
                break;
            case PREF_EMAIL:
                ((EditText)findViewById(R.id.TextEmail)).setText(value);
                break;
            case PREF_CYCLEFREQ:
                ((SeekBar) findViewById(R.id.SeekCycleFreq)).setProgress(Integer.parseInt((String) value));
                break;
            case PREF_GENDER:
                if (value.equals("M")) {
                    ((RadioButton) findViewById(R.id.ButtonMale)).setChecked(true);
                } else if (value.equals("F")) {
                    ((RadioButton) findViewById(R.id.ButtonFemale)).setChecked(true);
                }
                break;
            }
        }

    }

    @Override
    public void onDestroy() {
        // Save user preferences. We need an Editor object to
        // make changes. All objects are from android.context.Context
        SharedPreferences settings = getSharedPreferences("PREFS", 0);
        SharedPreferences.Editor editor = settings.edit();

        editor.putString(""+PREF_AGE,((EditText)findViewById(R.id.TextAge)).getText().toString());
        editor.putString(""+PREF_ZIPHOME,((EditText)findViewById(R.id.TextZipHome)).getText().toString());
        editor.putString(""+PREF_ZIPWORK,((EditText)findViewById(R.id.TextZipWork)).getText().toString());
        editor.putString(""+PREF_ZIPSCHOOL,((EditText)findViewById(R.id.TextZipSchool)).getText().toString());
        editor.putString(""+PREF_EMAIL,((EditText)findViewById(R.id.TextEmail)).getText().toString());
        editor.putString(""+PREF_CYCLEFREQ,""+((SeekBar)findViewById(R.id.SeekCycleFreq)).getProgress());
        RadioGroup rbg = (RadioGroup) findViewById(R.id.RadioGroup01);
        if (rbg.getCheckedRadioButtonId() == R.id.ButtonMale) editor.putString(""+PREF_GENDER,"M");
        if (rbg.getCheckedRadioButtonId() == R.id.ButtonFemale) editor.putString(""+PREF_GENDER,"F");

        // Don't forget to commit your edits!!!
        editor.commit();
        super.onDestroy();
    }
}
