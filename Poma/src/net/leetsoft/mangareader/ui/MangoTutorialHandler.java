package net.leetsoft.mangareader.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.RelativeLayout;
import android.widget.TextView;
import net.leetsoft.mangareader.Mango;
import net.leetsoft.mangareader.MangoActivity;
import net.leetsoft.mangareader.R;
import net.leetsoft.mangareader.activities.AllMangaActivity;
import net.leetsoft.mangareader.activities.BrowseByActivity;
import net.leetsoft.mangareader.activities.FavoritesActivity;
import net.leetsoft.mangareader.activities.MainMenuActivity;

import java.io.File;

public class MangoTutorialHandler
{
    public static final int MAIN_MENU = 1;
    public static final int SITE_SELECTION = 2;
    public static final int BROWSE_BY = 3;
    public static final int FAVORITES = 5;
    public static final int READER = 6;
    public static final int HISTORY = 7;
    public static final int LIBRARY = 8;
    public static final int MANGA_LIST = 9;

    public static int mTutorialSet = -1;
    public static int mTutorialStep = -1;
    public static MangoActivity mActivity = null;
    public static RelativeLayout mLayout = null;

    // used for overlap detection
    private static TextView mMainTextView = null;
    private static View mHighlightView = null;

    public static void startTutorial(final int set, MangoActivity context)
    {
        File f = new File(Mango.getDataDirectory() + "/Mango/notutorials.txt");
        if (f.exists())
            return;

        mTutorialSet = set;
        mTutorialStep = 0;
        mActivity = context;
        mLayout = mActivity.getTutorialOverlay();
        mActivity.displayTutorialOverlay();

        mLayout.postDelayed(new Runnable()
        {

            @Override
            public void run()
            {
                switch (set)
                {
                    case MAIN_MENU:
                        mainMenuTutorialStep(0);
                        break;
                    case SITE_SELECTION:
                        siteSelectTutorialStep(0);
                        break;
                    case BROWSE_BY:
                        browseByTutorialStep(0);
                        break;
                    case MANGA_LIST:
                        allMangaListTutorialStep(0);
                        break;
                    case READER:
                        readerTutorialStep(0);
                        break;
                    case FAVORITES:
                        favoritesTutorialStep(0);
                        break;
                }
            }
        }, 1);
    }

    public static void mainMenuTutorialStep(int step)
    {
        if (step == 6)
        {
            flagTutorialAsFinished();
            mActivity.hideTutorialOverlay();
            return;
        }

        wipeAllViews();

        mTutorialStep = step;

        TextView t;
        RelativeLayout.LayoutParams params;

        switch (step)
        {
            case 0:
                highlightView(null);
                t = makeDefaultTextView();
                t.setText(Html.fromHtml("Welcome to Mango!<br><br>Tutorial screens such as these will occasionally appear to help you along.<br><br><small>Don't want tutorials?  They can be disabled from Settings and Help >> Advanced Options.</small>"));

                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 1:
                highlightView(((MainMenuActivity) mActivity).getTutorialHighlightView(0));
                t = makeDefaultTextView();
                t.setText("'Browse Manga' will be your best friend.  Using it, you can look up your favorite manga, or find a new manga to read based on genre, popularity, or artist!");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 2:
                highlightView(((MainMenuActivity) mActivity).getTutorialHighlightView(1));
                t = makeDefaultTextView();
                t.setText("'My Library' is your own personal collection of manga.  Save chapters to your Library to read them whenever, even without an internet connection!");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 3:
                highlightView(((MainMenuActivity) mActivity).getTutorialHighlightView(2));
                t = makeDefaultTextView();
                t.setText("'Favorites' will be your other best friend.  Add a manga to your Favorites and Mango will automatically track your reading progress, so you'll never lose your place!");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 4:
                highlightView(((MainMenuActivity) mActivity).getTutorialHighlightView(3));
                t = makeDefaultTextView();
                t.setText("'History' keeps track of each chapter you open.  You can use it to resume reading a manga that you don't have in your Favorites list.");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 5:
                highlightView(((MainMenuActivity) mActivity).getTutorialHighlightView(4));
                t = makeDefaultTextView();
                t.setText("'Settings and Help' contains settings and help options, of course!  From there, tap on 'Preferences' to customize tons of settings to make Mango work just the way you want!");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
        }
        mLayout.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                checkForOverlap();
                mMainTextView.setVisibility(View.VISIBLE);
            }
        }, 1);
        addTapToContinue();
    }

    public static void siteSelectTutorialStep(int step)
    {
        if (step == 3)
        {
            flagTutorialAsFinished();
            mActivity.hideTutorialOverlay();
            return;
        }

        wipeAllViews();

        mTutorialStep = step;

        TextView t;
        RelativeLayout.LayoutParams params;

        switch (step)
        {
            case 0:
                highlightView(null);
                t = makeDefaultTextView();
                t.setText("This screen allows you to choose which website Mango downloads manga from.  Tap the blue 'i' icon to learn more about that source.");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 1:
                highlightView(null);
                t = makeDefaultTextView();
                t.setText("Sometimes, you won't be able to find the manga you want on a particular manga source.  In these cases, just try another source from this list!");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 2:
                highlightView(null);
                t = makeDefaultTextView();
                t.setText("By the way, the developer of Mango isn't associated with these websites.  If you're having an issue with missing chapters or pages, please contact the website, not us!");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
        }
        mLayout.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                checkForOverlap();
                mMainTextView.setVisibility(View.VISIBLE);
            }
        }, 1);
        addTapToContinue();
    }

    public static void browseByTutorialStep(int step)
    {
        if (step == 6)
        {
            flagTutorialAsFinished();
            mActivity.hideTutorialOverlay();
            return;
        }

        wipeAllViews();

        mTutorialStep = step;

        TextView t;
        RelativeLayout.LayoutParams params;

        switch (step)
        {
            case 0:
                highlightView(((BrowseByActivity) mActivity).getTutorialHighlightView(0));
                t = makeDefaultTextView();
                t.setText("'All Manga' displays the entire list of manga for this source, sorted alphabetically.");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 1:
                highlightView(((BrowseByActivity) mActivity).getTutorialHighlightView(1));
                t = makeDefaultTextView();
                t.setText("'Genre' allows you to pick a genre, such as Action or Romance, and see all manga in that genre.");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 2:
                highlightView(((BrowseByActivity) mActivity).getTutorialHighlightView(2));
                t = makeDefaultTextView();
                t.setText("'Popularity' displays the entire list of manga, sorted by how many times other Mango users have opened them.");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 3:
                highlightView(((BrowseByActivity) mActivity).getTutorialHighlightView(3));
                t = makeDefaultTextView();
                t.setText("'Latest Updates' shows the newest chapters added to this website in the past few days.");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 4:
                highlightView(((BrowseByActivity) mActivity).getTutorialHighlightView(4));
                t = makeDefaultTextView();
                t.setText("'Artist' allows you to pick a mangaka and see all of their works.");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 5:
                highlightView(((BrowseByActivity) mActivity).getTutorialHighlightView(5));
                t = makeDefaultTextView();
                t.setText("'Advanced Search' allows you to search specific genres and keywords.\n\nFor example, if you want to read a romantic comedy manga, but not if it has action, Advanced Search can help!");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
        }
        mLayout.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                checkForOverlap();
                mMainTextView.setVisibility(View.VISIBLE);
            }
        }, 1);
        addTapToContinue();
    }

    public static void allMangaListTutorialStep(int step)
    {
        if (step == 3)
        {
            flagTutorialAsFinished();
            mActivity.hideTutorialOverlay();
            return;
        }

        wipeAllViews();

        mTutorialStep = step;

        TextView t;
        RelativeLayout.LayoutParams params;

        switch (step)
        {
            case 0:
                highlightView(((AllMangaActivity) mActivity).getTutorialHighlightView(-1));
                t = makeDefaultTextView();
                t.setText("Start typing up here to quickly jump to a manga you know the name of...");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 1:
                highlightView(((AllMangaActivity) mActivity).getTutorialHighlightView(0));
                t = makeDefaultTextView();
                t.setText("...or, tap on Random Manga to spin the wheel and try something new!");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 2:
                highlightView(((AllMangaActivity) mActivity).getTutorialHighlightView(1));
                t = makeDefaultTextView();
                t.setText("Once you find a manga you like, tap on the star icon to add it to your Favorites list.  Then, Mango will automatically track your progress as you read!");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
        }
        mLayout.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                checkForOverlap();
                mMainTextView.setVisibility(View.VISIBLE);
            }
        }, 1);
        addTapToContinue();
    }

    public static void readerTutorialStep(int step)
    {
        if (step == 3)
        {
            flagTutorialAsFinished();
            mActivity.hideTutorialOverlay();
            return;
        }

        wipeAllViews();

        mTutorialStep = step;

        TextView t;
        RelativeLayout.LayoutParams params;

        switch (step)
        {
            case 0:
                highlightView(null);
                t = makeDefaultTextView();
                t.setText("This is Mango's Pagereader!  You've already learned the controls in the last Tutorial screen, so you're nearly good to go.\n\n(just remember, manga is read from right-to-left, so swipe as if you were reading a book in reverse!)");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 1:
                highlightView(null);
                t = makeDefaultTextView();
                t.setText("Mango 1.5 adds 'Touch Zones'.  This feature means you can tap on the left half of the screen to go to the next page, or the right half to go to the previous page.\n\nIf you don't like this feature, you can turn it off from the Preferences menu!");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 2:
                highlightView(null);
                mLayout.postDelayed(new Runnable()
                {

                    @Override
                    public void run()
                    {
                        mActivity.openOptionsMenu();
                    }
                }, 1000);
                mLayout.postDelayed(new Runnable()
                {

                    @Override
                    public void run()
                    {
                        mActivity.closeOptionsMenu();
                    }
                }, 3000);
                mLayout.bringToFront();
                t = makeDefaultTextView();
                t.setText("Ah, one more thing: be sure to press your phone's Menu key below to access more features like Add or Remove Favorite, Jump-to-Page, Chapter Navigation, and more!");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
        }
        mLayout.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                checkForOverlap();
                mMainTextView.setVisibility(View.VISIBLE);
            }
        }, 1);
        addTapToContinue();
    }

    public static void favoritesTutorialStep(int step)
    {
        if (step == 6)
        {
            flagTutorialAsFinished();
            mActivity.hideTutorialOverlay();
            return;
        }

        wipeAllViews();

        mTutorialStep = step;

        TextView t;
        RelativeLayout.LayoutParams params;

        switch (step)
        {
            case 0:
                highlightView(null);
                t = makeDefaultTextView();
                t.setText("This is the Favorites screen!  When you favorite a manga, Mango will display it here in this list.");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 1:
                highlightView(((FavoritesActivity) mActivity).getTutorialHighlightView(0));
                t = makeDefaultTextView();
                t.setText("Mango will automatically track your reading progress for manga you have favorited.  To resume reading, just tap on a favorite!");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 2:

                highlightView(null);
                mLayout.postDelayed(new Runnable()
                {

                    @Override
                    public void run()
                    {
                        mActivity.openOptionsMenu();
                    }
                }, 1000);
                mLayout.postDelayed(new Runnable()
                {

                    @Override
                    public void run()
                    {
                        mActivity.closeOptionsMenu();
                    }
                }, 3000);
                mLayout.bringToFront();
                t = makeDefaultTextView();
                t.setText("Use the Action Bar at the top to sort, filter, and search.  Press Menu to set tags and notifications.\n\nWhen Notifications are enabled, Mango will periodically check for new chapters and notify you when they're available!");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 3:
                highlightView(((FavoritesActivity) mActivity).getTutorialHighlightView(0));
                t = makeDefaultTextView();
                t.setText("Tap and hold on a favorite to access more options, such as View Chapters, Delete, Set Tag (more on that next), or Reset Progress.");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 4:
                highlightView(null);
                t = makeDefaultTextView();
                t.setText("If you have lots of favorites, Mango has ways to help you organize them.\n\nYou can assign tags (such as 'Reading' or 'Plan to Read') to favorites, and then press Menu >> Filter to filter by tag.");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
            case 5:
                ((FavoritesActivity) mActivity).toggleSearch();
                highlightView(((FavoritesActivity) mActivity).getTutorialHighlightView(-1));
                mLayout.bringToFront();
                InputMethodManager mgr = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                mgr.hideSoftInputFromWindow(mLayout.getWindowToken(), 0);
                t = makeDefaultTextView();
                t.setText("And finally, tap your phone's Search button to bring up the search box.  Just type the first few letters of a manga and tap on the search icon to find it quickly!");
                params = makeDefaultParams();
                mLayout.addView(t, params);
                break;
        }
        mLayout.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                checkForOverlap();
                mMainTextView.setVisibility(View.VISIBLE);
            }
        }, 1);
        addTapToContinue();
    }

    private static void highlightView(View v)
    {
        mHighlightView = null;
        if (v == null)
        {
            View view = new View(mActivity);
            view.setBackgroundColor(Color.argb(130, 0, 0, 0));
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(1, 1);
            params.height = LayoutParams.FILL_PARENT;
            params.width = LayoutParams.FILL_PARENT;
            mLayout.addView(view, params);
            return;
        }

        DisplayMetrics dm = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int topOffset = dm.heightPixels - mLayout.getMeasuredHeight();
        int[] coords = new int[2];
        v.getLocationInWindow(coords);
        coords[1] = coords[1] - topOffset;

        View a;
        RelativeLayout.LayoutParams params;

        // view over
        a = new View(mActivity);
        a.setBackgroundColor(Color.argb(40, 103, 178, 15));
        params = new RelativeLayout.LayoutParams(1, 1);
        params.height = v.getHeight();
        params.width = v.getWidth();
        params.setMargins(coords[0], coords[1], 0, 0);
        mLayout.addView(a, params);
        a.setId(999);
        mHighlightView = a;

        // view above
        a = new View(mActivity);
        a.setBackgroundColor(Color.argb(130, 0, 0, 0));
        params = new RelativeLayout.LayoutParams(1, 1);
        params.height = coords[1];
        params.width = LayoutParams.FILL_PARENT;
        mLayout.addView(a, params);

        // view below
        a = new View(mActivity);
        a.setBackgroundColor(Color.argb(130, 0, 0, 0));
        params = new RelativeLayout.LayoutParams(1, 1);
        params.height = mLayout.getHeight() - (coords[1] + v.getHeight());
        params.width = LayoutParams.FILL_PARENT;
        params.topMargin = (coords[1] + v.getHeight());
        mLayout.addView(a, params);

        // view to the right
        a = new View(mActivity);
        a.setBackgroundColor(Color.argb(130, 0, 0, 0));
        params = new RelativeLayout.LayoutParams(1, 1);
        params.height = v.getHeight();
        params.width = mLayout.getWidth() - (v.getWidth() + coords[0]);
        params.topMargin = coords[1];
        params.leftMargin = coords[0] + v.getWidth();
        mLayout.addView(a, params);

        // view to the left
        a = new View(mActivity);
        a.setBackgroundColor(Color.argb(130, 0, 0, 0));
        params = new RelativeLayout.LayoutParams(1, 1);
        params.height = v.getHeight();
        params.width = coords[0];
        params.topMargin = coords[1];
        params.leftMargin = 0;
        mLayout.addView(a, params);
    }

    private static void wipeAllViews()
    {
        while (mLayout.getChildCount() != 0)
        {
            View v = mLayout.getChildAt(0);
            v.clearAnimation();
            mLayout.removeView(v);
            v = null;
        }
        mLayout.setOnClickListener(null);
    }

    private static void addTapToContinue()
    {
        mLayout.postDelayed(new Runnable()
        {

            @Override
            public void run()
            {
                TextView t = makeDefaultTextView();
                t.setText("Tap the screen to continue!");
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(1, 1);
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                params.addRule(RelativeLayout.CENTER_HORIZONTAL);
                params.height = LayoutParams.WRAP_CONTENT;
                params.width = LayoutParams.WRAP_CONTENT;
                params.bottomMargin = 5;
                mLayout.addView(t, params);
                AnimationSet pulse = (AnimationSet) AnimationUtils.loadAnimation(mActivity, R.anim.anim_pulse);
                pulse.setRepeatMode(Animation.REVERSE);
                pulse.setRepeatCount(Animation.INFINITE);
                pulse.scaleCurrentDuration(3);
                t.startAnimation(pulse);
                mLayout.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        switch (mTutorialSet)
                        {
                            case MAIN_MENU:
                                mainMenuTutorialStep(mTutorialStep + 1);
                                break;
                            case SITE_SELECTION:
                                siteSelectTutorialStep(mTutorialStep + 1);
                                break;
                            case BROWSE_BY:
                                browseByTutorialStep(mTutorialStep + 1);
                                break;
                            case MANGA_LIST:
                                allMangaListTutorialStep(mTutorialStep + 1);
                                break;
                            case READER:
                                readerTutorialStep(mTutorialStep + 1);
                                break;
                            case FAVORITES:
                                favoritesTutorialStep(mTutorialStep + 1);
                                break;
                        }
                    }
                });
            }
        }, 300);
    }

    private static TextView makeDefaultTextView()
    {
        TextView t = new TextView(mActivity);
        t.setText("no text!");
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setShadowLayer(2, 0, 0, Color.BLACK);
        t.setVisibility(View.INVISIBLE);
        mMainTextView = t;
        return t;
    }

    private static RelativeLayout.LayoutParams makeDefaultParams()
    {
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(1, 1);
        params.setMargins(6, 0, 6, 0);
        params.height = LayoutParams.WRAP_CONTENT;
        params.width = LayoutParams.FILL_PARENT;
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        return params;
    }

    private static void checkForOverlap()
    {
        if (mHighlightView != null)
        {
            Rect rect1 = new Rect();
            Rect rect2 = new Rect();
            mHighlightView.getLocalVisibleRect(rect1);
            mMainTextView.getLocalVisibleRect(rect2);

            rect1.top = mHighlightView.getTop();
            rect1.bottom = mHighlightView.getBottom();
            rect2.top = mMainTextView.getTop();
            rect2.bottom = mMainTextView.getBottom();
            if (Rect.intersects(rect1, rect2))
            {
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(1, 1);
                params.setMargins(6, 0, 6, 0);
                params.height = LayoutParams.WRAP_CONTENT;
                params.width = LayoutParams.FILL_PARENT;
                params.addRule(RelativeLayout.BELOW, mHighlightView.getId());
                mLayout.removeView(mMainTextView);
                mLayout.addView(mMainTextView, params);
            }
        }
    }

    private static void flagTutorialAsFinished()
    {
        Mango.getSharedPreferences().edit().putBoolean("tutorial" + mTutorialSet + "Done", true).commit();
    }
}
