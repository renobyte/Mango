package net.leetsoft.mangareader;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.webkit.WebView;
import android.widget.*;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;
import net.leetsoft.mangareader.activities.*;
import net.leetsoft.mangareader.ui.MangoAdWrapperView;
import net.leetsoft.mangareader.ui.MangoLayout;
import com.mobclix.android.sdk.MobclixAdView;
import com.mobclix.android.sdk.MobclixAdViewListener;
import com.mobclix.android.sdk.MobclixMMABannerXLAdView;

import java.util.Map;

public class MangoActivity extends SherlockFragmentActivity implements MobclixAdViewListener
{
    private MangoLayout mLayoutManager;
    private Runnable mKillBGCallback;

    private MangoAdWrapperView mAdLayout;
    private MobclixAdView mAd;

    private View mVerticalOffsetView;

    private com.actionbarsherlock.view.Menu mMenuObj;

    // tutorial stuff
    protected RelativeLayout mTutorialOverlay;
    protected Button mTutorialButton;
    protected boolean mTutorialMode;

    // toast overlay stuff
    protected RelativeLayout mToastOverlay;
    // protected RelativeLayout mToastContainer;
    // protected Button mToastOkButton;
    protected ImageView mToastCloseButton;
    protected TextView mToastText;
    protected boolean mShowClose = true;
    protected Runnable mCloseRunnable = new Runnable()
    {

        @Override
        public void run()
        {
            closeToast();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState)
    {

        // if (android.os.Build.VERSION.SDK_INT >= 11)
        // this.setTheme(R.style.Theme_Mango_Light_Honeycomb);

        if (Mango.getSharedPreferences().getBoolean("invertTheme", false))
        {
            if (!Mango.getSharedPreferences().getBoolean("disableBackgrounds", false))
                Mango.getSharedPreferences().edit().putBoolean("disableBackgrounds", true).commit();
            this.setTheme(R.style.Theme_Mango_Dark);
        }
        if (!Mango.INITIALIZED)
            Mango.initializeApp(this);

        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.basemenu, menu);
        mMenuObj = menu;
        return super.onCreateOptionsMenu(menu);
    }

    protected void refreshMenu()
    {
        this.onPrepareOptionsMenu(mMenuObj);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
        {
            if (this.getClass() != MainMenuActivity.class)
            {
                // Intent myIntent = new Intent();
                // myIntent.setClassName("com.ls.manga", "com.ls.manga.activities.MainMenuActivity");
                // myIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                // startActivity(myIntent);
                finish();
            }
        }
        else if (item.getItemId() == R.id.menuSendFeedback)
        {
            Intent myIntent = new Intent();
            myIntent.setClass(Mango.CONTEXT, ContactActivity.class);
            startActivity(myIntent);
            return true;
        }
        else if (item.getItemId() == R.id.menuSettings)
        {
            Intent myIntent = new Intent();
            myIntent.setClass(Mango.CONTEXT, PreferencesActivity.class);
            startActivity(myIntent);
            return true;
        }
        return false;
    }

    protected void restartApp()
    {
        Toast.makeText(this, "Mango ran out of memory and can't show this screen.  Trying to auto-restart now...", Toast.LENGTH_LONG).show();
        Mango.log("Mango is out of memory and cannot continue. Killing process and restarting with last intent.");
        PendingIntent intent = PendingIntent.getActivity(this.getBaseContext(), 0, new Intent(getIntent()), getIntent().getFlags());
        AlarmManager mgr = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 2000, intent);
        System.exit(1);
    }

    public MangoLayout getLayoutManager()
    {
        return mLayoutManager;
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        if (Mango.getSharedPreferences().getBoolean("analyticsEnabled", false) && MangoHttp.checkConnectivity(getApplicationContext()))
            FlurryAgent.onStartSession(this, "AD7A4MA54PHHGYQN8TWW");
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        if (Mango.getSharedPreferences().getBoolean("analyticsEnabled", false) && MangoHttp.checkConnectivity(getApplicationContext()))
            FlurryAgent.onEndSession(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        // mAd.pause();
        if (mLayoutManager != null)
        {
            mLayoutManager.mBgSet = false;
            if (mKillBGCallback == null)
            {
                mKillBGCallback = new Runnable()
                {

                    @Override
                    public void run()
                    {
                        if (mLayoutManager != null)
                        {
                            mLayoutManager.clearBackground();
                            mKillBGCallback = null;
                        }
                    }
                };
                mLayoutManager.postDelayed(mKillBGCallback, 500);
            }
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        // mAd.resume();
        if (mLayoutManager != null)
        {
            mLayoutManager.removeCallbacks(mKillBGCallback);
            mKillBGCallback = null;
            mLayoutManager.setBackground();
        }
    }

    @Override
    public void onDestroy()
    {
        if (mAd != null)
        {
            mAd.removeMobclixAdViewListener(this);
            mAd.cancelAd();
            mAdLayout.removeAllViews();

            try
            {
                /* HACK HACK HACK HACK HACK
                     * There appears to be a bug or some sort of implementation oddity
                     * with WebView on devices running HTC Sense which causes the WebView
                     * to leak its parent's context.  So here we traverse down the
                     * MobclixAdView's view hierarchy and try to find the WebView,
                     * then manually call its destroy() method and set it to null.
                     */
                WebView wv;
                if (((ViewGroup) ((ViewGroup) mAd.getChildAt(0)).getChildAt(0)).getChildAt(0).getClass().getSimpleName().contains("WebView"))
                {
                    wv = (WebView) ((ViewGroup) ((ViewGroup) mAd.getChildAt(0)).getChildAt(0)).getChildAt(0);
                    wv.destroy();
                    wv = null;
                }
            }
            catch (Exception e)
            {
                // nope.avi
            }
            mAd = null;
            mAdLayout = null;
        }
        super.onDestroy();
    }

    public void setToastText(String text)
    {
        mToastText.setText(text);
    }

    public void setToast(String text, OnClickListener listener, boolean showClose)
    {
        // mToastOkButton.setOnClickListener(listener);
        mToastOverlay.setOnClickListener(listener);
        setToastText(text);
    }

    public void closeToast()
    {
        mToastOverlay = (RelativeLayout) findViewById(R.id.menuToastOverlay);
        AnimationSet a = (AnimationSet) AnimationUtils.loadAnimation(this, R.anim.toastslidedown);
        mToastOverlay.startAnimation(a);
        mToastOverlay.setVisibility(View.GONE);
    }

    public void showToast(int timeout)
    {
        mToastOverlay = (RelativeLayout) findViewById(R.id.menuToastOverlay);
        AnimationSet a = (AnimationSet) AnimationUtils.loadAnimation(this, R.anim.toastslideup);
        mToastOverlay.setVisibility(View.VISIBLE);
        mToastOverlay.setAnimation(a);
        a.start();
        mToastOverlay.removeCallbacks(mCloseRunnable);
        if (timeout > 0)
            mToastOverlay.postDelayed(mCloseRunnable, timeout);
    }

    public RelativeLayout getTutorialOverlay()
    {
        return mTutorialOverlay;
    }

    public void displayTutorialOverlay()
    {
        mTutorialMode = true;
        mTutorialOverlay.setVisibility(View.VISIBLE);
        Animation fadein = AnimationUtils.loadAnimation(this, R.anim.fadein);
        fadein.setDuration(300);
        mTutorialOverlay.startAnimation(fadein);
    }

    public void hideTutorialOverlay()
    {
        mTutorialOverlay.setOnClickListener(null);
        mTutorialMode = false;
        Animation fadeout = AnimationUtils.loadAnimation(this, R.anim.fadeout);
        fadeout.setDuration(300);
        mTutorialOverlay.startAnimation(fadeout);
        mTutorialOverlay.postDelayed(new Runnable()
        {

            @Override
            public void run()
            {
                while (mTutorialOverlay.getChildCount() != 0)
                {
                    View v = mTutorialOverlay.getChildAt(0);
                    v.clearAnimation();
                    mTutorialOverlay.removeView(v);
                    v = null;
                }
                mTutorialOverlay.setVisibility(View.GONE);
            }
        }, 180);
    }

    protected void logEvent(String event, Map<String, String> params)
    {
        if (Mango.getSharedPreferences().getBoolean("analyticsEnabled", false) && !Mango.getSharedPreferences().getBoolean("offlineMode", false))
        {
            if (params == null)
                FlurryAgent.onEvent(event);
            else
                FlurryAgent.onEvent(event, params);
        }
    }

    public void inflateLayoutManager(Activity activity, int layoutResourceId)
    {
        mLayoutManager = (MangoLayout) getLayoutInflater().inflate(layoutResourceId, null);
        activity.addContentView(mLayoutManager, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

        try
        {
            mTutorialOverlay = (RelativeLayout) findViewById(R.id.tutOverlayLayout);
            mTutorialOverlay.setVisibility(View.GONE);

            mToastOverlay = (RelativeLayout) findViewById(R.id.menuToastOverlay);
            mToastText = (TextView) findViewById(R.id.toastText);
            mToastCloseButton = (ImageView) findViewById(R.id.toastClose);
            mToastOverlay.setClipChildren(false);

            mToastCloseButton.setOnClickListener(new OnClickListener()
            {

                @Override
                public void onClick(View v)
                {
                    long nag = System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 15);
                    Mango.getSharedPreferences().edit().putLong("nextNag", nag).commit();
                    closeToast();
                }
            });
        }
        catch (Exception e)
        {
        }
    }

    public int getRealHeight()
    {
        return mLayoutManager.getRealHeight();
    }

    public int getRealWidth()
    {
        return mLayoutManager.getRealWidth();
    }

    public void setNoBackground(boolean bg)
    {
        mLayoutManager.setNoBackground(bg);
    }

    public void setJpBackground(int id)
    {
        mLayoutManager.setJpResourceId(id);
    }

    public View getJpVerticalOffsetView()
    {
        return mLayoutManager.getJpVerticalOffsetView();
    }

    public void setJpVerticalOffsetView(View v)
    {
        if (v != null)
            mVerticalOffsetView = v;
        mLayoutManager.setJpVerticalOffsetView(v);
    }

    public void showAdView()
    {
        long timeSinceAdHide = System.currentTimeMillis() - Mango.getSharedPreferences().getLong("hideAdTimer", 1);
        if (Math.abs(timeSinceAdHide) < 1000 * 30) // 60 seconds
        {
            hideAdView();
            return;
        }

        mAdLayout.setVisibility(View.VISIBLE);
        setJpVerticalOffsetView(mVerticalOffsetView);
        mLayoutManager.mBgSet = false;
        mLayoutManager.setBackground();
    }

    public void hideAdView()
    {
        mAdLayout.setVisibility(View.GONE);
        if (getJpVerticalOffsetView() == mAdLayout)
            setJpVerticalOffsetView(null);
        mLayoutManager.mBgSet = false;
        mLayoutManager.setBackground();
    }

    public void instantiateAdView()
    {
        mAdLayout.setVisibility(View.VISIBLE);
        mAd = new MobclixMMABannerXLAdView(this);
        mAd.setShouldRotate(false);
        mAd.addMobclixAdViewListener(this);

        mAdLayout.addView(mAd);
        mAdLayout.setAdHitbox(mAd);
        mAdLayout.setCloseListener(new Runnable()
        {

            @Override
            public void run()
            {
                MangoActivity.this.hideAdView();
                Mango.getSharedPreferences().edit().putLong("hideAdTimer", System.currentTimeMillis()).commit();
                if (!Mango.getSharedPreferences().getBoolean("hideAdToast", false))
                {
                    Toast.makeText(MangoActivity.this, "Ads will be suppressed for one minute.", Toast.LENGTH_LONG).show();
                    Mango.getSharedPreferences().edit().putBoolean("hideAdToast", true).commit();
                }
            }
        });

        showAdView();
    }

    public void setAdLayout(MangoAdWrapperView v)
    {
        mAdLayout = v;

        Class c = this.getClass();

        if (c == MainMenuActivity.class || c == AboutActivity.class || c == BrowseByActivity.class || c == SiteSelectorActivity.class || c == SettingsMenuActivity.class || c == GenreActivity.class)
        {
            hideAdView();
            return;
        }

        if (Mango.DISABLE_ADS || !MangoHttp.checkConnectivity(getApplicationContext()))
        {
            hideAdView();
            return;
        }

        if (mAd == null)
            instantiateAdView();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (mTutorialMode)
            {
                hideTutorialOverlay();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public void setTitle(String title, String subtitle)
    {
        this.getSupportActionBar().setTitle(title);
        if (subtitle != null)
            this.getSupportActionBar().setSubtitle(subtitle);
        else
            this.getSupportActionBar().setSubtitle(null);
        this.getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME);
        try
        {
            if (this.getClass() != MainMenuActivity.class)
                this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        catch (Exception e)
        {
            Mango.log("inflateLayoutManager: " + Log.getStackTraceString(e));
        }
    }

    @Override
    public String keywords()
    {
        return "anime, manga, video games, movies";
    }

    @Override
    public void onAdClick(MobclixAdView arg0)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onCustomAdTouchThrough(MobclixAdView arg0, String arg1)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onFailedLoad(MobclixAdView arg0, int arg1)
    {
        Mango.log("Ad failed to load for " + this.toString());
        hideAdView();
    }

    @Override
    public boolean onOpenAllocationLoad(MobclixAdView arg0, int arg1)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onSuccessfulLoad(MobclixAdView arg0)
    {
        showAdView();
    }

    @Override
    public String query()
    {
        // TODO Auto-generated method stub
        return null;
    }
}
