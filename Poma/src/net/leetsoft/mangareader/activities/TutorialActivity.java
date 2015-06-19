package net.leetsoft.mangareader.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import net.leetsoft.mangareader.Mango;
import net.leetsoft.mangareader.MangoActivity;
import net.leetsoft.mangareader.R;

import java.io.File;

public class TutorialActivity extends MangoActivity
{
    private ImageView mFinger;
    private ImageView mDemoPage;
    private TextView mTitle;
    private TextView mNote;
    private Button mPrevButton;
    private Button mNextButton;

    private Animation mAnimationFinger;
    private Animation mAnimationDemoPage;

    private Runnable mAnimationDelay = new Runnable()
    {
        @Override
        public void run()
        {
            if (mCurrentTutorial == Tutorials.ZOOM)
                zoomEndHandler();
            else if (mCurrentTutorial == Tutorials.NEXT_PAGE_FLING)
                nextFlingEndHandler();
            else if (mCurrentTutorial == Tutorials.PREV_PAGE_FLING)
                prevFlingEndHandler();
        }
    };

    private int mCurrentTutorial = -1;
    private int mCurrentStage;

    private Drawable FINGER_UP;
    private Drawable FINGER_DOWN;
    private Animation DUMMY_INVISIBLE;
    private Animation DUMMY_VISIBLE;

    private boolean pressedBackOnce = false;

    private static class Tutorials
    {
        final static int ZOOM = 0;
        final static int NEXT_PAGE_FLING = 1;
        final static int PREV_PAGE_FLING = 2;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        File f = new File(Mango.getDataDirectory() + "/Mango/notutorials.txt");
        if (f.exists())
        {
            Mango.getSharedPreferences().edit().putInt("pagereaderShowTutorial", 2).commit();
            finish();
            return;
        }

        inflateLayoutManager(this, R.layout.tutorialscreen);
        super.setNoBackground(true);
        setTitle("Controls Tutorial", null);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mFinger = (ImageView) findViewById(R.id.tutorialFinger);
        mDemoPage = (ImageView) findViewById(R.id.tutorialDemopage);
        mTitle = (TextView) findViewById(R.id.tutorialTitle);
        mNote = (TextView) findViewById(R.id.tutorialNote);
        mPrevButton = (Button) findViewById(R.id.tutorialBack);
        mNextButton = (Button) findViewById(R.id.tutorialNext);

        mPrevButton.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                setCurrentTutorial(mCurrentTutorial - 1);
            }
        });
        mNextButton.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                setCurrentTutorial(mCurrentTutorial + 1);
            }
        });

        FINGER_UP = getResources().getDrawable(R.drawable.img_finger_open);
        FINGER_DOWN = getResources().getDrawable(R.drawable.img_finger_close);
        DUMMY_VISIBLE = AnimationUtils.loadAnimation(this, R.anim.dummy_vis);
        DUMMY_INVISIBLE = AnimationUtils.loadAnimation(this, R.anim.dummy_invis);

        mTitle.setText("Mango Tutorial");
        mNote.setText("Learn Mango's controls");
        AlertDialog alert = new AlertDialog.Builder(this).create();
        alert.setTitle("Welcome to Mango!");
        alert.setMessage("Please view this short tutorial to learn how to use the Mango Pagereader. Click Next to move to the next diagram.\n\nEnjoy the app!");
        alert.setButton("Okay", new DialogInterface.OnClickListener()
        {

            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                setCurrentTutorial(0);
            }
        });
        alert.setCancelable(false);
        alert.show();
    }

    @Override
    public void onDestroy()
    {
        getWindow().getDecorView().removeCallbacks(mAnimationDelay);
        super.onDestroy();
    }

    private void setCurrentTutorial(int tutorial)
    {
        if (tutorial != mCurrentTutorial)
            mNextButton.setEnabled(false);

        mCurrentTutorial = tutorial;
        mCurrentStage = 0;

        getWindow().getDecorView().removeCallbacks(mAnimationDelay);

        // reset state
        mAnimationDemoPage = null;
        mAnimationFinger = null;
        mFinger.clearAnimation();
        mDemoPage.clearAnimation();
        mFinger.setVisibility(View.INVISIBLE);
        mDemoPage.setVisibility(View.INVISIBLE);
        mFinger.setImageDrawable(FINGER_UP);
        mFinger.invalidate();
        mDemoPage.invalidate();

        if (tutorial == 0)
            mPrevButton.setEnabled(false);
        else
            mPrevButton.setEnabled(true);

        if (tutorial == 2)
            mNextButton.setText("Finish");
        else
            mNextButton.setText("Next");

        mNote.setText("");

        if (tutorial == Tutorials.ZOOM)
            startZoomTutorial();
        else if (tutorial == Tutorials.NEXT_PAGE_FLING)
            startNextFlingTutorial();
        else if (tutorial == Tutorials.PREV_PAGE_FLING)
            startPrevFlingTutorial();
        else
        {
            Mango.getSharedPreferences().edit().putInt("pagereaderShowTutorial", 2).commit();
            finish();
        }
    }

    private void startFingerAnimation()
    {
        if (mAnimationFinger == null)
            return;
        getWindow().getDecorView().postDelayed(mAnimationDelay, mAnimationFinger.computeDurationHint());
        // mAnimationFinger.setAnimationListener(new
        // FinishedAnimationListener());
        mFinger.startAnimation(mAnimationFinger);
    }

    private void startPageAnimation()
    {
        if (mAnimationDemoPage == null)
            return;
        mDemoPage.startAnimation(mAnimationDemoPage);
    }

    // Zoom in/out animation

    private void startZoomTutorial()
    {
        mTitle.setText("Zooming in and out");
        mNote.setText("Double-tap to zoom in.\nDouble-tap again to zoom out.\nYou can also use pinch-zoom.");
        zoomEndHandler();
    }

    private void zoomEndHandler()
    {
        mAnimationFinger = null;
        mAnimationDemoPage = null;
        switch (mCurrentStage)
        {
            case 0:
                mAnimationFinger = AnimationUtils.loadAnimation(this, R.anim.fadein);
                mAnimationDemoPage = AnimationUtils.loadAnimation(this, R.anim.fadein);
                break;
            case 1:
                mFinger.setVisibility(View.VISIBLE);
                mDemoPage.setVisibility(View.VISIBLE);
                mAnimationDemoPage = null;
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationFinger.setDuration(1000);
                break;
            case 2:
                mFinger.setImageDrawable(FINGER_DOWN);
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationFinger.setDuration(100);
                break;
            case 3:
                mFinger.setImageDrawable(FINGER_UP);
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationFinger.setDuration(100);
                break;
            case 4:
                mFinger.setImageDrawable(FINGER_DOWN);
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationFinger.setDuration(100);
                break;
            case 5:
                mFinger.setImageDrawable(FINGER_UP);
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationFinger.setDuration(100);
                break;
            case 6:
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationDemoPage = AnimationUtils.loadAnimation(this, R.anim.tutorial_zoomin_page);
                mAnimationFinger.setDuration(700);
                break;
            case 7:
                mFinger.setImageDrawable(FINGER_DOWN);
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationFinger.setDuration(100);
                break;
            case 8:
                mFinger.setImageDrawable(FINGER_UP);
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationFinger.setDuration(100);
                break;
            case 9:
                mFinger.setImageDrawable(FINGER_DOWN);
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationFinger.setDuration(100);
                break;
            case 10:
                mFinger.setImageDrawable(FINGER_UP);
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationFinger.setDuration(100);
                break;
            case 11:
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationDemoPage = AnimationUtils.loadAnimation(this, R.anim.tutorial_zoomout_page);
                mAnimationFinger.setDuration(700);
                break;
            case 12:
                mFinger.setImageDrawable(FINGER_UP);
                mAnimationFinger = AnimationUtils.loadAnimation(this, R.anim.fadeout);
                mAnimationDemoPage = AnimationUtils.loadAnimation(this, R.anim.fadeout);
                break;
            case 13:
                mFinger.setVisibility(View.INVISIBLE);
                mDemoPage.setVisibility(View.INVISIBLE);
                mAnimationFinger = DUMMY_INVISIBLE;
                mAnimationFinger.setDuration(1000);
                break;
            default:
                mNextButton.setEnabled(true);
                setCurrentTutorial(Tutorials.ZOOM);
                return;
        }
        startFingerAnimation();
        startPageAnimation();
        mCurrentStage++;
    }

    // Next page fling tutorial

    private void startNextFlingTutorial()
    {
        mTitle.setText("Going to the next page");
        mNote.setText("Remember, manga is read from right-to-left.\nFling the page as if you were flipping back a page in a novel.\nYou can also simply tap the screen.");
        mFinger.setVisibility(View.VISIBLE);
        nextFlingEndHandler();
    }

    private void nextFlingEndHandler()
    {
        mAnimationFinger = null;
        mAnimationDemoPage = null;
        switch (mCurrentStage)
        {
            case 0:
                mAnimationFinger = DUMMY_INVISIBLE;
                mAnimationFinger.setDuration(100);
                break;
            case 1:
                mAnimationFinger = AnimationUtils.loadAnimation(this, R.anim.fadein);
                break;
            case 2:
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationFinger.setDuration(1000);
                break;
            case 3:
                mFinger.setImageDrawable(FINGER_DOWN);
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationFinger.setDuration(300);
                break;
            case 4:
                mAnimationFinger = AnimationUtils.loadAnimation(this, R.anim.tutorial_nextfling_finger);
                break;
            case 5:
                mFinger.setVisibility(View.INVISIBLE);
                mDemoPage.setVisibility(View.VISIBLE);
                mAnimationDemoPage = AnimationUtils.loadAnimation(this, R.anim.tutorial_nextfling_page);
                mAnimationFinger = DUMMY_INVISIBLE;
                mAnimationFinger.setDuration(mAnimationDemoPage.getDuration());
                break;
            case 6:
                mAnimationFinger = DUMMY_INVISIBLE;
                mAnimationFinger.setDuration(600);
                break;
            case 7:
                mFinger.setVisibility(View.INVISIBLE);
                mDemoPage.setVisibility(View.INVISIBLE);
                mFinger.setImageDrawable(FINGER_UP);
                mAnimationDemoPage = AnimationUtils.loadAnimation(this, R.anim.fadeout);
                mAnimationFinger = DUMMY_INVISIBLE;
                mAnimationFinger.setDuration(mAnimationDemoPage.getDuration());
                break;
            case 8:
                mAnimationFinger = DUMMY_INVISIBLE;
                mAnimationFinger.setDuration(1000);
                break;
            default:
                mNextButton.setEnabled(true);
                setCurrentTutorial(Tutorials.NEXT_PAGE_FLING);
                return;
        }
        startFingerAnimation();
        startPageAnimation();
        mCurrentStage++;
    }

    // Previous page fling tutorial

    private void startPrevFlingTutorial()
    {
        mTitle.setText("Going to the previous page");
        mNote.setText("Remember, manga is read from right-to-left.\nFling the page as if you were flipping to the next page in a novel.");
        mFinger.setVisibility(View.VISIBLE);
        nextFlingEndHandler();
    }

    private void prevFlingEndHandler()
    {
        mAnimationFinger = null;
        mAnimationDemoPage = null;
        switch (mCurrentStage)
        {
            case 0:
                mAnimationFinger = DUMMY_INVISIBLE;
                mAnimationFinger.setDuration(100);
                break;
            case 1:
                mAnimationFinger = AnimationUtils.loadAnimation(this, R.anim.fadein);
                break;
            case 2:
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationFinger.setDuration(1000);
                break;
            case 3:
                mFinger.setImageDrawable(FINGER_DOWN);
                mAnimationFinger = DUMMY_VISIBLE;
                mAnimationFinger.setDuration(300);
                break;
            case 4:
                mAnimationFinger = AnimationUtils.loadAnimation(this, R.anim.tutorial_prevfling_finger);
                break;
            case 5:
                mFinger.setVisibility(View.INVISIBLE);
                mDemoPage.setVisibility(View.VISIBLE);
                mAnimationDemoPage = AnimationUtils.loadAnimation(this, R.anim.tutorial_prevfling_page);
                mAnimationFinger = DUMMY_INVISIBLE;
                mAnimationFinger.setDuration(mAnimationDemoPage.getDuration());
                break;
            case 6:
                mAnimationFinger = DUMMY_INVISIBLE;
                mAnimationFinger.setDuration(600);
                break;
            case 7:
                mFinger.setVisibility(View.INVISIBLE);
                mDemoPage.setVisibility(View.INVISIBLE);
                mFinger.setImageDrawable(FINGER_UP);
                mAnimationDemoPage = AnimationUtils.loadAnimation(this, R.anim.fadeout);
                mAnimationFinger = DUMMY_INVISIBLE;
                mAnimationFinger.setDuration(mAnimationDemoPage.getDuration());
                break;
            case 8:
                mAnimationFinger = DUMMY_INVISIBLE;
                mAnimationFinger.setDuration(1000);
                break;
            default:
                mNextButton.setEnabled(true);
                setCurrentTutorial(Tutorials.PREV_PAGE_FLING);
                return;
        }
        startFingerAnimation();
        startPageAnimation();
        mCurrentStage++;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK && Mango.getSharedPreferences().getInt("pagereaderShowTutorial", 0) != 2 && !pressedBackOnce)
        {
            pressedBackOnce = true;
            Mango.alert("Please view the tutorial to the end.  It takes just 20 seconds and will make your reading experience more enjoyable, I promise.", this);
            return false;
        }
        else if (keyCode == KeyEvent.KEYCODE_BACK && Mango.getSharedPreferences().getInt("pagereaderShowTutorial", 0) != 2 && pressedBackOnce)
        {
            Mango.getSharedPreferences().edit().putInt("pagereaderShowTutorial", 2).commit();
            Mango.alert("Alright.  If you really want to skip the tutorial, press the Back key once more.", this);
            return false;
        }
        else
            return super.onKeyDown(keyCode, event);
    }
}
