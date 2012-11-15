package net.leetsoft.mangareader;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;
import com.google.ads.*;
import com.mobclix.android.sdk.MobclixAdView;
import com.mobclix.android.sdk.MobclixAdViewListener;
import com.mobclix.android.sdk.MobclixMMABannerXLAdView;
import net.leetsoft.mangareader.activities.*;
import net.leetsoft.mangareader.ui.MangoAdWrapperView;
import net.leetsoft.mangareader.ui.MangoLayout;

import java.util.Map;

public class MangoActivity extends SherlockFragmentActivity implements MobclixAdViewListener, AdListener
{
    private MangoLayout mLayoutManager;
    private Runnable mKillBGCallback;


    // advert stuff
    private int mAdType;
    private MangoAdWrapperView mAdLayout;
    private MobclixAdView mMobclixAdView;
    private AdView mAdMobAdView;
    private WebView mLeadboltAdView;

    private View mVerticalOffsetView;

    private com.actionbarsherlock.view.Menu mMenuObj;

    // tutorial stuff
    protected RelativeLayout mTutorialOverlay;
    protected boolean mTutorialMode;

    // toast overlay stuff
    protected RelativeLayout mToastOverlay;
    protected ImageView mToastCloseButton;
    protected TextView mToastText;
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
        if (Mango.getSharedPreferences().getBoolean("invertTheme", false))
        {
            if (!Mango.getSharedPreferences().getBoolean("disableBackgrounds", false))
                Mango.getSharedPreferences().edit().putBoolean("disableBackgrounds", true).commit();
            this.setTheme(R.style.Theme_Mango_Dark);
        }
        if (!Mango.INITIALIZED)
            Mango.initializeApp(this);

        mAdType = Mango.pickAdProvider();

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
        if (mAdLayout != null)
        {
            switch (mAdType)
            {
                case Mango.PROVIDER_MOBCLIX:
                    disposeMobclixAdview();
                    break;
                case Mango.PROVIDER_LEADBOLT:
                    if (mLeadboltAdView != null)
                        mLeadboltAdView.destroy();
                    break;
                case Mango.PROVIDER_ADMOB:
                    if (mAdMobAdView != null)
                        mAdMobAdView.destroy();
                    break;
            }
        }

        super.onDestroy();
    }

    private void disposeMobclixAdview()
    {
        if (mMobclixAdView != null)
        {
            mMobclixAdView.removeMobclixAdViewListener(this);
            mMobclixAdView.cancelAd();

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
                if (((ViewGroup) ((ViewGroup) mMobclixAdView.getChildAt(0)).getChildAt(0)).getChildAt(0).getClass().getSimpleName().contains("WebView"))
                {
                    wv = (WebView) ((ViewGroup) ((ViewGroup) mMobclixAdView.getChildAt(0)).getChildAt(0)).getChildAt(0);
                    wv.destroy();
                    wv = null;
                }
            }
            catch (Exception e)
            {
                // nope.avi
            }
            mMobclixAdView = null;
            mAdLayout = null;
        }
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

        Mango.log("MangoActivity", "Using ad provider: " + mAdType);

        switch (mAdType)
        {
            case Mango.PROVIDER_MOBCLIX:
            default:
                if (mMobclixAdView != null)
                    return;

                mMobclixAdView = new MobclixMMABannerXLAdView(this);
                mMobclixAdView.setShouldRotate(false);
                mMobclixAdView.addMobclixAdViewListener(this);

                mAdLayout.addView(mMobclixAdView);
                mAdLayout.setAdHitbox(mMobclixAdView);
                break;

            case Mango.PROVIDER_ADMOB:
                if (mAdMobAdView != null)
                    return;

                mAdMobAdView = new AdView(this, AdSize.SMART_BANNER, "a14d419d563ddaf");
                mAdMobAdView.loadAd(new AdRequest());
                mAdLayout.addView(mAdMobAdView);
                mAdLayout.setAdHitbox(mAdMobAdView);
                break;

            case Mango.PROVIDER_LEADBOLT:
                if (mLeadboltAdView != null)
                    return;

                int bannerWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 320, getResources().getDisplayMetrics());
                int bannerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50, getResources().getDisplayMetrics());
                int bannerId = 782282564;
                if ((this.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE)
                {
                    bannerWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 468, getResources().getDisplayMetrics());
                    bannerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60, getResources().getDisplayMetrics());
                    LayoutParams p = mAdLayout.getLayoutParams();
                    p.height = bannerHeight;
                    mAdLayout.setLayoutParams(p);
                    bannerId = 441291409;
                }

                mLeadboltAdView = new WebView(this);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                    mLeadboltAdView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(bannerWidth, bannerHeight);
                params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);

                mLeadboltAdView.setLayoutParams(params);
                mLeadboltAdView.getSettings().setJavaScriptEnabled(true);
                mLeadboltAdView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
//                mLeadboltAdView.getSettings().setSupportMultipleWindows(true);
                String adHtml = "<html><body style=\"margin:0;padding:0\"><script type=\"text/javascript\" src=\"http://ad.leadboltads.net/show_app_ad.js?section_id=" + bannerId + " \"></script></body></html>";
                mLeadboltAdView.loadData(adHtml, "text/html", "utf-8");
                mLeadboltAdView.setBackgroundColor(0x00000000);

                mAdLayout.addView(mLeadboltAdView);
                mAdLayout.setAdHitbox(mLeadboltAdView);
                break;
        }

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
        Mango.log("Mobclix: Failed to load for " + this.toString() + "(" + arg1 + ")");
        hideAdView();
    }

    @Override
    public boolean onOpenAllocationLoad(MobclixAdView arg0, int arg1)
    {
        Mango.log("Mobclix: Using Open Allocation.");
        return false;
    }

    @Override
    public void onSuccessfulLoad(MobclixAdView arg0)
    {
        //Mango.log("Mobclix: Successfully loaded for " + this.toString());
        showAdView();
    }

    @Override
    public String query()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onReceiveAd(Ad ad)
    {
        //Mango.log("AdMob: Successfully loaded for " + this.toString());
    }

    @Override
    public void onFailedToReceiveAd(Ad ad, AdRequest.ErrorCode errorCode)
    {
        Mango.log("AdMob: Failed to loaded for " + this.toString() + "(" + errorCode.name() + ")");
        hideAdView();
    }

    @Override
    public void onPresentScreen(Ad ad)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onDismissScreen(Ad ad)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onLeaveApplication(Ad ad)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
