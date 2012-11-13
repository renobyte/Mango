package net.leetsoft.mangareader.activities;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.widget.Toast;
import com.flurry.android.FlurryAgent;
import net.leetsoft.mangareader.Mango;
import net.leetsoft.mangareader.MangoHttp;
import net.leetsoft.mangareader.R;

import java.io.File;
import java.util.HashMap;

public class PreferencesActivity extends PreferenceActivity
{
    private EditTextPreference mDataFolderLoc;
    private CheckBoxPreference mOverrideDataFolder;

    private CheckBoxPreference mTapZones;
    private CheckBoxPreference mDisableTap;
    private CheckBoxPreference mDisableAnims;

    private String mDefaultMsg;

    @Override
    public void onStop()
    {
        if (Mango.getSharedPreferences().getBoolean("analyticsEnabled", false) && MangoHttp.checkConnectivity(getApplicationContext()))
        {
            FlurryAgent.onStartSession(this, "AD7A4MA54PHHGYQN8TWW");
            HashMap<String, String> parameters = new HashMap<String, String>();
            parameters.put("ReduceMomentum", Boolean.toString(Mango.getSharedPreferences().getBoolean("reducedMomentum", false)));
            parameters.put("LTRReading", Boolean.toString(Mango.getSharedPreferences().getBoolean("leftRightReading", false)));
            parameters.put("StickyZoom", Boolean.toString(Mango.getSharedPreferences().getBoolean("stickyZoom", false)));
            parameters.put("DisableTap", Boolean.toString(Mango.getSharedPreferences().getBoolean("disableTapToAdvance", false)));
            parameters.put("DisableSwipe", Boolean.toString(Mango.getSharedPreferences().getBoolean("disableSwipeControls", false)));
            parameters.put("Preloading", Mango.getSharedPreferences().getString("preloaders", "3"));
            parameters.put("ReverseChapters", Boolean.toString(Mango.getSharedPreferences().getBoolean("reverseChapterList", false)));
            parameters.put("DisableZones", Boolean.toString(Mango.getSharedPreferences().getBoolean("disableTapZones", false)));
            parameters.put("DisableAnimations", Boolean.toString(Mango.getSharedPreferences().getBoolean("disableAnimation", false)));
            parameters.put("DisableBackgrounds", Boolean.toString(Mango.getSharedPreferences().getBoolean("disableBackgrounds", false)));
            FlurryAgent.onEvent("Close Preferences", parameters);
            FlurryAgent.onEndSession(this);
        }
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.mangoprefs);
        setTitle("Preferences");

        mDataFolderLoc = (EditTextPreference) this.getPreferenceScreen().findPreference("customDataFolder");
        mOverrideDataFolder = (CheckBoxPreference) this.getPreferenceScreen().findPreference("useCustomDataFolder");
        mDisableAnims = (CheckBoxPreference) this.getPreferenceScreen().findPreference("disableAnimation");
        mDisableTap = (CheckBoxPreference) this.getPreferenceScreen().findPreference("disableTapToAdvance");
        mTapZones = (CheckBoxPreference) this.getPreferenceScreen().findPreference("disableTapZones");

        if (java.lang.Runtime.getRuntime().maxMemory() < 24 * 1024 * 1024)
        {
            mDisableAnims.setSummary(mDisableAnims.getSummary() + "\n[Forced on lower-end devices]");
            mDisableAnims.setEnabled(false);
        }


        mDefaultMsg = mDataFolderLoc.getSummary().toString();

        if (!Mango.getSharedPreferences().getBoolean("useCustomDataFolder", false))
            mDataFolderLoc.setEnabled(false);
        else
            mDataFolderLoc.setSummary(mDefaultMsg + "\nCurrent: " + Mango.getSharedPreferences().getString("customDataFolder", "~"));

        if (Mango.getSharedPreferences().getBoolean("disableTapToAdvance", false))
            mTapZones.setEnabled(false);

        mOverrideDataFolder.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
        {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                boolean val = ((Boolean) newValue).booleanValue();

                mDataFolderLoc.setEnabled(val);

                if (!val)
                    Mango.getSharedPreferences().edit().putString("customDataFolder", Mango.getDataDirectory().getAbsolutePath()).commit();

                return true;
            }
        });

        mDisableTap.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
        {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                boolean val = ((Boolean) newValue).booleanValue();

                mTapZones.setEnabled(!val);

                return true;
            }
        });

        mDataFolderLoc.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
        {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                String s = (String) newValue;
                s = s.trim();
                if (s.endsWith("/"))
                    s = s.substring(0, s.length() - 1);
                if (s.toLowerCase().endsWith("/Mango"))
                    s = s.substring(0, s.length() - "/Mango".length());
                File f = new File(s + "/Mango");

                if (!f.exists())
                {
                    try
                    {
                        if (!f.mkdir())
                        {
                            Toast.makeText(PreferencesActivity.this, "Mango wasn't able to create a folder at that location.  Select another.", Toast.LENGTH_LONG).show();
                            return false;
                        }
                        if (!f.canWrite())
                        {
                            Toast.makeText(PreferencesActivity.this, "Mango cannot write to that location.  Select another.", Toast.LENGTH_LONG).show();
                            return false;
                        }
                    } catch (Exception e)
                    {
                        Toast.makeText(PreferencesActivity.this, "Mango encountered an error attempting to write to that location.  Select another.", Toast.LENGTH_LONG).show();
                        Mango.log("onPreferenceChanged: " + e.toString());
                        return false;
                    }
                }

                Mango.alert("The data folder has been set to:\n\n" + f.getAbsolutePath() + "\n\nYou must manually move any existing data from the old Mango folder to the new one above.",
                        "Important!", PreferencesActivity.this);
                mDataFolderLoc.setSummary(mDefaultMsg + "\nCurrent: " + Mango.getSharedPreferences().getString("customDataFolder", "~"));
                if (newValue.toString().equals(s))
                    return true;
                else
                    Mango.getSharedPreferences().edit().putString("customDataFolder", s.toString()).commit();
                return false;
            }
        });
    }
}
