package net.leetsoft.mangareader.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import net.leetsoft.mangareader.*;
import net.leetsoft.mangareader.activities.JumpToPageDialog.JumpToPageListener;
import net.leetsoft.mangareader.ui.MangoTutorialHandler;
import net.leetsoft.mangareader.ui.MangoZoomableImageView;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

public class OfflinePagereaderActivity extends MangoActivity
{
    // UX components and views
    private MangoZoomableImageView mPageField;
    private LinearLayout mStatusbar;
    private TextView mStatusText;
    private RelativeLayout mTitlebar;
    private TextView mTitleText;
    private ImageView mAnimatorView;
    private ImageView mStaticView;
    private TextView mTitleStatusText;
    private ImageView mMenuButton;

    // misc helper variables (don't need to be persisted)
    private AnimationSet mPulseAnimation;
    private AnimationSet mSlideInAnimation;
    private AnimationSet mSlideOffAnimation;
    private boolean mAnimatePage;
    private boolean mAnimateSlideIn;
    private long mLastSetTitle;
    private boolean mSkipRestore;
    private boolean mActionBarVisible;

    // state variables (must be persisted)
    private LibraryChapter mActiveLibraryChapter;
    private int mPageIndex;
    private int mChapterIndex;        // don't save this!!! use ID instead! (ie. bookmarks)
    private String mChapterId;
    private int mInitialPage;
    private boolean mInstantiated;
    private String mTitlebarText;
    private boolean mBusy;
    private Page[] mPages;
    private int mTrackReadingProgress;
    private String mSubstringAltStart;
    private String mSubstringStart;
    private String mUrlPrefix;
    private boolean mIsFilesystemChapter;

    // when rotating, pack anything important in the class below and pass it
    // along to the new activity
    private class InstanceBundle
    {
        private LibraryChapter activeLibraryChapter;
        private Page[] pages;
        private int pageIndex;
        private int chapterIndex;
        private String chapterId;
        private int initialPage;
        private boolean instantiated;
        private String titlebarText;
        private boolean busy;
        private String statusText;
        private int trackReadingProgress;
        private String substringAltStart;
        private String substringStart;
        private String urlPrefix;
        private boolean isFilesystemChapter;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        this.getSupportActionBar().setDisplayShowHomeEnabled(false);

        // Show tutorial on first run
        if (Mango.getSharedPreferences().getInt("pagereaderShowTutorial", 0) != 2)
        {
            Intent myIntent = new Intent();
            myIntent.setClass(Mango.CONTEXT, TutorialActivity.class);
            startActivity(myIntent);
        }

        // Start the long and arduous task of initializing the pagereader!
        // Clear the menu background image cache to free up some memory
        Mango.recycleMenuBackgrounds();

        // Apply some user prefs that must be set prior to adding UI elements
        if (Mango.getSharedPreferences().getBoolean("fullscreenReading", false))
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Mango.getSharedPreferences().getString("pagereaderOrientation", "-1").equals("1"))
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else if (Mango.getSharedPreferences().getString("pagereaderOrientation", "-1").equals("2"))
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Assign UI member variables to their respective views
        inflateLayoutManager(this, R.layout.pagereader);
        super.setNoBackground(true);
        mPageField = (MangoZoomableImageView) findViewById(R.id.prPagefield2);
        mStatusbar = (LinearLayout) findViewById(R.id.prStatusbar);
        mStatusText = (TextView) findViewById(R.id.prStatusText);
        mTitlebar = (RelativeLayout) findViewById(R.id.prTitlebar);
        mTitleText = (TextView) findViewById(R.id.prTitleText);
        mAnimatorView = (ImageView) findViewById(R.id.prAnimationView);
        mStaticView = (ImageView) findViewById(R.id.prAnimationStaticView);
        mTitleStatusText = (TextView) findViewById(R.id.prTitleSystemText);
        mMenuButton = (ImageView) findViewById(R.id.prMenuSoftButton);
        mMenuButton.bringToFront();
        mMenuButton.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                OfflinePagereaderActivity.this.openOptionsMenu();
            }
        });

        if (android.os.Build.VERSION.SDK_INT >= 11)
            mPageField.setSystemUiVisibility(1);
        mActionBarVisible = true;
        mMenuButton.setVisibility(View.VISIBLE);
        this.getSupportActionBar().show();

        if (android.os.Build.VERSION.SDK_INT < 11)
        {
            // Pre-Honeycomb
            mActionBarVisible = false;
            this.getSupportActionBar().hide();
            mMenuButton.setVisibility(View.GONE);
        }
        else
        {
            // Post-Honeycomb
            // Tablets
            if ((this.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE)
            {
                if (Mango.getSharedPreferences().getBoolean("fullscreenReading", false))
                {
                    mActionBarVisible = false;
                    this.getSupportActionBar().hide();
                    mMenuButton.setVisibility(View.VISIBLE);
                    if (Build.VERSION.SDK_INT >= 14)
                        mMenuButton.setVisibility((ViewConfiguration.get(this).hasPermanentMenuKey() ? View.GONE : View.VISIBLE));
                }
                else
                    mMenuButton.setVisibility(View.GONE);
            }
            else
            // Phones
            {
                mActionBarVisible = false;
                this.getSupportActionBar().hide();
                mMenuButton.setVisibility(View.VISIBLE);
                if (Build.VERSION.SDK_INT >= 14)
                    mMenuButton.setVisibility((ViewConfiguration.get(this).hasPermanentMenuKey() ? View.GONE : View.VISIBLE));
            }
        }

        if (Mango.getSharedPreferences().getBoolean("suppressMenuButton", false))
            mMenuButton.setVisibility(View.GONE);

        // Title bar and status bar setup
        mStatusbar.setVisibility(View.INVISIBLE);
        mStatusbar.bringToFront();
        mTitlebar.setVisibility(View.INVISIBLE);

        // Set up animations and their respective views
        mPulseAnimation = (AnimationSet) AnimationUtils.loadAnimation(this, R.anim.anim_pulse);
        mPulseAnimation.setRepeatMode(Animation.REVERSE);
        mPulseAnimation.setRepeatCount(Animation.INFINITE);
        mSlideInAnimation = (AnimationSet) AnimationUtils.loadAnimation(this, R.anim.pageslidein);
        mSlideOffAnimation = (AnimationSet) AnimationUtils.loadAnimation(this, R.anim.pageslideoff);
        mAnimatorView.setVisibility(View.INVISIBLE);
        mStaticView.setVisibility(View.INVISIBLE);
        mAnimatorView.setAnimation(mSlideInAnimation);

        // Initialize touchscreen control code
        setTouchControlCallbacks();

        // Set next page callback for animations and zoom initialization
        mPageField.setOnUpdateCallback(new Runnable()
        {
            @Override
            public void run()
            {
                newPageCallback();
            }
        });

        if (Mango.getSharedPreferences().getBoolean("keepScreenOn", false))
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Apply some saved state variables in the case of a screen rotation
        if (getLastCustomNonConfigurationInstance() != null)
        {
            mSkipRestore = true;
            InstanceBundle save = (InstanceBundle) getLastCustomNonConfigurationInstance();
            mActiveLibraryChapter = save.activeLibraryChapter;
            mPages = save.pages;
            mBusy = save.busy;
            mChapterIndex = save.chapterIndex;
            mChapterId = save.chapterId;
            mInitialPage = save.initialPage;
            mInstantiated = save.instantiated;
            mPageIndex = save.pageIndex;
            mTitlebarText = save.titlebarText;
            mTrackReadingProgress = save.trackReadingProgress;
            mSubstringStart = save.substringStart;
            mSubstringAltStart = save.substringAltStart;
            mUrlPrefix = save.urlPrefix;
            mIsFilesystemChapter = save.isFilesystemChapter;
            setTitle(mActiveLibraryChapter.chapter.title);

            checkReadingProgress();

            if (save.statusText != null && save.statusText.length() > 0)
                setStatus(save.statusText);

            save = null;

            mAnimatePage = false;

            if (mInstantiated)
            {
                Bitmap bm = MangoLibraryIO.readBitmapFromDisk(mActiveLibraryChapter.path, (mIsFilesystemChapter ? mPages[mPageIndex].url : mPages[mPageIndex].id), 1, !mIsFilesystemChapter);
                if (bm == null)
                {
                    Intent intent = new Intent(getIntent());
                    intent.putExtra("initialpage", (mPageIndex == -1 ? mInitialPage : mPageIndex));
                    intent.putExtra("chapterid", mChapterId);
                    setIntent(intent);
                    restartApp();
                    return;
                }
                mPageField.setImageBitmap(bm);
            }
            return;
        }

        // Warn if cache is not accessible
        String state = Environment.getExternalStorageState();
        if (!state.startsWith(Environment.MEDIA_MOUNTED))
        {
            Mango.log("SD card isn't available, cannot display any images. (state=" + state + ")");
            Mango.alert(
                    "Mango cannot access the SD card. This might be because you have connected your device to a computer and turned on USB Mass Storage mode.\n\nMango cannot display any pages until it can access the SD card again.",
                    "Warning", this);
        }

        if (savedInstanceState != null)
            return;

        // Finally, open our chapter, but only if we are not initializing from a saved bundle
        Bundle arguments = getIntent().getExtras();

        LibraryChapter lc = (LibraryChapter) arguments.getSerializable("lcSerializable");
        openChapter(lc, lc.path, arguments.getInt("initialpage"));

        super.logEvent("Read Offline Chapter", null);
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        setIntent(intent);
        Bundle arguments = getIntent().getExtras();
        LibraryChapter lc = (LibraryChapter) arguments.getSerializable("lcSerializable");
        openChapter(lc, lc.path, arguments.getInt("initialpage"));
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance()
    {
        InstanceBundle save = new InstanceBundle();
        save.activeLibraryChapter = mActiveLibraryChapter;
        save.activeLibraryChapter.siteId = mActiveLibraryChapter.siteId;
        save.activeLibraryChapter.manga = mActiveLibraryChapter.manga;
        save.activeLibraryChapter.chapter = mActiveLibraryChapter.chapter;
        save.pages = mPages;
        save.busy = mBusy;
        save.chapterIndex = mChapterIndex;
        save.chapterId = mChapterId;
        save.initialPage = mInitialPage;
        save.instantiated = mInstantiated;
        save.pageIndex = mPageIndex;
        save.titlebarText = mTitlebarText;
        save.trackReadingProgress = mTrackReadingProgress;
        save.substringStart = mSubstringStart;
        save.substringAltStart = mSubstringAltStart;
        save.urlPrefix = mUrlPrefix;
        save.isFilesystemChapter = mIsFilesystemChapter;
        if (mStatusbar.getVisibility() == View.VISIBLE)
            save.statusText = (String) mStatusText.getText();
        mActiveLibraryChapter = null;
        return save;
    }

    @Override
    public void onSaveInstanceState(Bundle save)
    {
        super.onSaveInstanceState(save);
        save.putSerializable("lcSerializable", mActiveLibraryChapter);
        save.putInt("page", mPageIndex);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);
        if (mSkipRestore)
            return;
        LibraryChapter lc = (LibraryChapter) savedInstanceState.getSerializable("lcSerializable");
        openChapter(lc, lc.path, savedInstanceState.getInt("page"));
    }

    private void openFilesystemChapter(LibraryChapter lc, int pageIndex)
    {
        try
        {
            mIsFilesystemChapter = true;
            mInitialPage = pageIndex;
            mActiveLibraryChapter = lc;
            mChapterId = "local";
            mChapterIndex = -1;
            mPageIndex = pageIndex;
            mPageField.setImageBitmap(null);
            mPages = lc.filesystemChapter.generatePageArray();
            mTrackReadingProgress = 3;
            clearStatus();
            setTitle(mActiveLibraryChapter.chapter.title);
            setTitlebarText(mActiveLibraryChapter.manga.title + " " + mActiveLibraryChapter.chapter.id, true);
            System.gc();

            if (mInitialPage > mPages.length)
                mInitialPage = mPages.length - 1;

            Mango.log("openFilesystemChapter: " + mActiveLibraryChapter.manga.title + " " + mActiveLibraryChapter.chapter.title);
            goToPage(mInitialPage);
        }
        catch (Exception e)
        {
            Mango.alert(
                    "Mango was not able to load this folder.<br><br><small><b>Stack Trace:</b><br>" + Log.getStackTraceString(e),
                    "Error", this, new DialogInterface.OnClickListener()
            {

                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    OfflinePagereaderActivity.this.finish();
                }
            });
            Mango.log("openFilesystemChapter error: " + Log.getStackTraceString(e));
        }
    }

    private void openChapter(LibraryChapter lc, String path, int pageIndex)
    {
        Mango.log("openChapter: " + path);
        // load filesystemchapter
        if (lc.filesystemChapter != null)
        {
            openFilesystemChapter(lc, pageIndex);
            return;
        }
        try
        {
            mInitialPage = pageIndex;
            mActiveLibraryChapter = lc;
            if (mActiveLibraryChapter.path.startsWith("/Mango/"))
                mActiveLibraryChapter.path = "/Mango/" + mActiveLibraryChapter.path.substring(7);
            mChapterId = lc.chapter.id;
            mChapterIndex = lc.chapterIndex;
            mPageIndex = -1;
            mPageField.setImageBitmap(null);
            mPages = new Page[0];
            addRecent();
            checkReadingProgress();
            mInstantiated = false;
            clearStatus();
            setTitle(mActiveLibraryChapter.chapter.title);
            setTitlebarText(mActiveLibraryChapter.manga.title + " " + mActiveLibraryChapter.chapter.id, true);
            System.gc();

            parseXml(MangoLibraryIO.readIndexData(mActiveLibraryChapter.path));
        }
        catch (NullPointerException ex)
        {
            Mango.alert(
                    "Mango was not able to open this chapter.  If this happens repeatedly, try re-downloading the chapter.<br><br><small><b>Stack Trace:</b><br>" + Log.getStackTraceString(ex),
                    "Error", this, new DialogInterface.OnClickListener()
            {

                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    OfflinePagereaderActivity.this.finish();
                }
            });
            Mango.log("openChapter error: " + Log.getStackTraceString(ex));
        }
        catch (Exception ex)
        {
            Mango.log(ex.toString() + " in openChapter (offlinereader)");
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
    }

    @Override
    public void onResume()
    {
        super.onResume();
    }

    @Override
    public void onDestroy()
    {
        cleanup();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.offlinepagereadermenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void openOptionsMenu()
    {
        if (mInstantiated)
            setTitlebarText(mActiveLibraryChapter.manga.title + " " + mActiveLibraryChapter.chapter.id + " page " + mPages[mPageIndex].id + " (" + (mPageIndex + 1) + "/" + mPages.length + ")", true);
        super.openOptionsMenu();
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu)
    {
        if (menu == null || mActiveLibraryChapter == null)
            return false;

        if (mTrackReadingProgress == 3)
        {
            menu.getItem(0).setIcon(R.drawable.ic_action_add);
            menu.getItem(0).setTitle("Add Favorite");
            menu.getItem(0).setTitleCondensed("Add Favorite");
        }
        else if (mTrackReadingProgress == 2)
        {
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    MangoSqlite db = new MangoSqlite(OfflinePagereaderActivity.this);
                    db.open();
                    final Favorite f = db.getFavoriteForManga(mActiveLibraryChapter.manga);
                    db.close();
                    mPageField.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            if (!isReadingProgressBehind(f))
                            {
                                menu.getItem(0).setIcon(R.drawable.ic_action_reset);
                                menu.getItem(0).setTitle("Set Progress Here");
                                menu.getItem(0).setTitleCondensed("Reset Progress");
                            }
                        }
                    });
                }
            }).start();
        }
        else if (mTrackReadingProgress == 1)

        {
            menu.getItem(0).setIcon(R.drawable.ic_action_remove);
            menu.getItem(0).setTitle("Remove Favorite");
            menu.getItem(0).setTitleCondensed("Remove Favorite");
            menu.getItem(0).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER); // DOES NOT WORK PROPERLY!!!
        }

        if (!mActionBarVisible)

        {
            for (int i = 0; i < menu.size(); i++)
            {
                menu.getItem(i).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
        }

        return super.

                onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.menuOfflineRedownloadPage)
        {
            if (mIsFilesystemChapter)
            {
                Mango.alert("This feature is not available for content which has not been downloaded through Mango.", OfflinePagereaderActivity.this);
                return super.onOptionsItemSelected(item);
            }
            AlertDialog alert = new AlertDialog.Builder(OfflinePagereaderActivity.this).create();
            alert.setTitle("Repair this page?");
            alert.setMessage("If this page is missing or damaged, you can have Mango attempt to repair it by re-downloading it.\n\nYou'll need to be connected to the internet for this to work.\n\nContinue?");
            alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yep", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    doRepairPage();
                }
            });
            alert.setButton(DialogInterface.BUTTON_NEGATIVE, "No", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {}
            });
            alert.show();
        }
        if (item.getItemId() == R.id.menuOfflineChapterList)
        {

            Intent chaptersIntent = new Intent();
            chaptersIntent.setClassName(Mango.CONTEXT, LibraryBrowserActivity.class.getName());
            if (!mIsFilesystemChapter)
            {
                Manga argManga = new Manga();
                argManga.id = mActiveLibraryChapter.manga.id;
                argManga.title = mActiveLibraryChapter.manga.title;
                argManga.generateSimpleName(null);
                chaptersIntent.putExtra("manga", argManga);
            }
            startActivity(chaptersIntent);
        }
        if (item.getItemId() == R.id.menuOfflineAddBookmark)
        {
            addRevertFavorite();
            return true;
        }
        else if (item.getItemId() == R.id.menuOfflineJumpToPage)
        {
            super.logEvent("Jump-to-Page", null);
            JumpToPageDialog jumptopage = new JumpToPageDialog(OfflinePagereaderActivity.this, new JumpToPageListener()
            {
                @Override
                public void jumpToPageCallback(int selection)
                {
                    OfflinePagereaderActivity.this.goToPage(selection);
                }
            });
            jumptopage.show();
            if (mIsFilesystemChapter)
                jumptopage.initializeAdapter(mActiveLibraryChapter.path, "", mPages, mPageIndex, true, true);
            else
                jumptopage.initializeAdapter(mActiveLibraryChapter.manga.id, mChapterId, mPages, mPageIndex, true, false);
            return true;
        }
        else if (item.getItemId() == R.id.menuOfflineBookmarksLst)
        {
            Intent myIntent = new Intent();
            myIntent.setClass(Mango.CONTEXT, FavoritesActivity.class);
            myIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(myIntent);
        }
        else if (item.getItemId() == R.id.menuOfflineNextChapter)
        {
            if (mIsFilesystemChapter)
            {
                FilesystemChapter fsf = getNextFilesystemChapter();
                LibraryChapter lc = new LibraryChapter();
                if (fsf == null)
                    return super.onOptionsItemSelected(item);

                Manga m = new Manga();
                m.id = fsf.fileObj.getParent();
                // Find second to last path seperator
                int firstSlash = m.id.lastIndexOf("/");
                m.title = m.id.substring(m.id.lastIndexOf("/", firstSlash - 1));
                Chapter c = new Chapter();
                c.id = fsf.fileObj.getName();
                c.title = fsf.fileObj.getName();
                lc.chapterCount = 1;
                lc.chapterIndex = 1;
                lc.manga = m;
                lc.path = fsf.fileObj.getAbsolutePath() + "/";
                lc.siteId = 1;
                lc.filesystemChapter = fsf;
                lc.chapter = c;
                openFilesystemChapter(lc, 0);
            }
            else
            {
                LibraryChapter lc = getNextLibraryChapter();
                if (lc == null)
                    return super.onOptionsItemSelected(item);
                openChapter(lc, lc.path, 0);
            }
        }
        else if (item.getItemId() == R.id.menuOfflinePreviousChapter)
        {
            if (mIsFilesystemChapter)
            {
                FilesystemChapter fsf = getPreviousFilesystemChapter();
                LibraryChapter lc = new LibraryChapter();
                if (fsf == null)
                    return super.onOptionsItemSelected(item);

                Manga m = new Manga();
                m.id = fsf.fileObj.getParent();
                // Find second to last path seperator
                int firstSlash = m.id.lastIndexOf("/");
                m.title = m.id.substring(m.id.lastIndexOf("/", firstSlash - 1));
                Chapter c = new Chapter();
                c.id = fsf.fileObj.getName();
                c.title = fsf.fileObj.getName();
                lc.chapterCount = 1;
                lc.chapterIndex = 1;
                lc.manga = m;
                lc.path = fsf.fileObj.getAbsolutePath() + "/";
                lc.siteId = 1;
                lc.filesystemChapter = fsf;
                lc.chapter = c;
                openFilesystemChapter(lc, 0);
            }
            else
            {
                LibraryChapter lc = getPreviousLibraryChapter();
                if (lc == null)
                    return super.onOptionsItemSelected(item);
                openChapter(lc, lc.path, 0);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void parseXml(String data)
    {
        ArrayList<Page> pageArrayList = new ArrayList<Page>();

        try
        {
            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            SAXParser parser = saxFactory.newSAXParser();
            XMLReader reader = parser.getXMLReader();
            PagelistSaxHandler handler = new PagelistSaxHandler();
            reader.setContentHandler(handler);
            reader.parse(new InputSource(new StringReader(data)));
            pageArrayList.addAll(handler.getAllPages());
            mSubstringStart = handler.getSubstringStart();
            mSubstringAltStart = handler.getSubstringAltStart();
            mUrlPrefix = handler.getImageUrlPrefix();
        }
        catch (SAXException ex)
        {
            Mango.alert("An error occurred while parsing the xml data. Please try re-downloading the chapter.\n\n" + ex.toString(), "Invalid Response", this);
            return;
        }
        catch (ParserConfigurationException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        mPages = new Page[pageArrayList.size()];
        pageArrayList.toArray(mPages);
        pageArrayList = null;
        if (mInitialPage > mPages.length)
            mInitialPage = mPages.length - 1;

        goToPage(mInitialPage);

        if (!Mango.getSharedPreferences().getBoolean("tutorial" + MangoTutorialHandler.READER + "Done", false))
            MangoTutorialHandler.startTutorial(MangoTutorialHandler.READER, this);
    }

    public class PagelistSaxHandler extends DefaultHandler
    {
        ArrayList<Page> allPages;
        Page currentPage;
        String baseUrl;
        String substringStart = "";
        String substringAltStart = "";

        public ArrayList<Page> getAllPages()
        {
            return this.allPages;
        }

        public String getImageUrlPrefix()
        {
            return baseUrl;
        }

        public String getSubstringStart()
        {
            return substringStart;
        }

        public String getSubstringAltStart()
        {
            return substringAltStart;
        }

        @Override
        public void startDocument() throws SAXException
        {
            super.startDocument();
            allPages = new ArrayList<Page>();
        }

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException
        {
            super.startElement(uri, localName, name, attributes);
            if (localName.equalsIgnoreCase("page"))
            {
                currentPage = new Page();
            }
            else if (localName.equalsIgnoreCase("url"))
            {
                if (baseUrl == null)
                    baseUrl = attributes.getValue(0);
                else
                    currentPage.id = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("alturl"))
            {
                currentPage.url = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("string"))
            {
                substringStart = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("string2"))
            {
                substringAltStart = attributes.getValue(0);
            }
        }

        @Override
        public void endElement(String uri, String localName, String name) throws SAXException
        {
            super.endElement(uri, localName, name);
            if (currentPage != null)
            {
                if (localName.equalsIgnoreCase("page"))
                {
                    allPages.add(currentPage);
                }
            }
        }
    }

    public void cleanup()
    {
        mPageField.setOnCreateContextMenuListener(null);
        mPageField.removeCallbacks(mTitlebarHideRunnable);
        mTitlebarHideRunnable = null;
        mPageField.setImageBitmap(null);
        mPageField.unregisterCallbacks();
        mPageField = null;
        mBusy = false;
        System.gc();
    }

    private FilesystemChapter[] getAllFilesystemChapters()
    {
        File path = new File(mActiveLibraryChapter.filesystemChapter.fileObj.getParent());
        ArrayList<FilesystemChapter> folders = new ArrayList<FilesystemChapter>();

        try
        {
            if (!path.exists())
                return null;

            File[] files = path.listFiles(new FileFilter()
            {
                @Override
                public boolean accept(File pathname)
                {
                    if (pathname.isDirectory() && !pathname.isHidden())
                        return true;

                    return false;
                }
            });
            if (files == null || files.length == 0)
                return null;

            for (int i = 0; i < files.length; i++)
            {
                FilesystemChapter fsf = new FilesystemChapter();
                fsf.fileObj = files[i];
                fsf.isChapter = false;
                fsf.pages = 0;
                fsf.isValidChapterFolder();
                if (fsf.isChapter)
                    folders.add(fsf);
            }

            FilesystemChapter[] chapters = new FilesystemChapter[folders.size()];
            folders.toArray(chapters);
            Arrays.sort(chapters, new FolderComparator());
            return chapters;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private FilesystemChapter getNextFilesystemChapter()
    {
        FilesystemChapter[] chapters = getAllFilesystemChapters();
        int curIndex = -1;
        for (int i = 0; i < chapters.length; i++)
        {
            if (chapters[i].fileObj.getAbsolutePath().equals(mActiveLibraryChapter.filesystemChapter.fileObj.getAbsolutePath()))
                curIndex = i;
        }

        if (curIndex == -1)
        {
            Mango.alert("There was a problem scanning for the next chapter.", OfflinePagereaderActivity.this);
            return null;
        }

        if (curIndex + 1 >= chapters.length)
        {
            Mango.alert("This is the last chapter Mango can find in this folder.", OfflinePagereaderActivity.this);
            return null;
        }

        return chapters[curIndex + 1];
    }

    private FilesystemChapter getPreviousFilesystemChapter()
    {
        FilesystemChapter[] chapters = getAllFilesystemChapters();
        int curIndex = -1;
        for (int i = 0; i < chapters.length; i++)
        {
            if (chapters[i].fileObj.getAbsolutePath().equals(mActiveLibraryChapter.filesystemChapter.fileObj.getAbsolutePath()))
                curIndex = i;
        }

        if (curIndex == -1)
        {
            Mango.alert("There was a problem scanning for the next chapter.", OfflinePagereaderActivity.this);
            return null;
        }

        if (curIndex - 1 < 0)
        {
            Mango.alert("This is the first chapter Mango can find in this folder.", OfflinePagereaderActivity.this);
            return null;
        }

        return chapters[curIndex - 1];
    }

    private LibraryChapter getNextLibraryChapter()
    {
        MangoSqlite db = new MangoSqlite(this);
        int targetIndex = -1;
        try
        {
            db.open();
            LibraryChapter[] lcArray = db.getLibraryChaptersForManga(mActiveLibraryChapter.manga);
            for (int i = 0; i < lcArray.length; i++)
            {
                LibraryChapter lc = lcArray[i];
                if (lc.chapterIndex > mChapterIndex)
                {
                    if (targetIndex == -1)
                        targetIndex = i;
                    else
                    {
                        if (lc.chapterIndex < lcArray[targetIndex].chapterIndex)
                            targetIndex = i;
                    }
                }
            }
            if (targetIndex != -1)
            {
                LibraryChapter lc = lcArray[targetIndex];
                if (lc.chapterIndex - mChapterIndex > 1)
                {
                    Mango.alert("The next chapter isn't saved in your Library. Mango will instead jump to the nearest next chapter.", OfflinePagereaderActivity.this);
                }
                return lc;
            }
            else
            {
                Mango.alert("This is the last chapter of " + mActiveLibraryChapter.manga.title + " you have in your Library.", OfflinePagereaderActivity.this);
                return null;
            }
        }
        catch (Exception e)
        {
            Mango.alert("There was a problem scanning your Library for the next chapter.", OfflinePagereaderActivity.this);
            return null;
        }
    }

    private LibraryChapter getPreviousLibraryChapter()
    {
        MangoSqlite db = new MangoSqlite(this);
        int targetIndex = -1;
        try
        {
            db.open();
            LibraryChapter[] lcArray = db.getLibraryChaptersForManga(mActiveLibraryChapter.manga);
            for (int i = 0; i < lcArray.length; i++)
            {
                LibraryChapter lc = lcArray[i];
                if (lc.chapterIndex < mChapterIndex)
                {
                    if (targetIndex == -1)
                        targetIndex = i;
                    else
                    {
                        if (lc.chapterIndex > lcArray[targetIndex].chapterIndex)
                            targetIndex = i;
                    }
                }
            }
            if (targetIndex != -1)
            {
                LibraryChapter lc = lcArray[targetIndex];
                if (mChapterIndex - lc.chapterIndex > 1)
                {
                    Mango.alert("The previous chapter isn't saved in your Library. Mango will instead jump to the nearest previous chapter.", OfflinePagereaderActivity.this);
                }
                return lc;
            }
            else
            {
                Mango.alert("This is the first chapter of " + mActiveLibraryChapter.manga.title + " you have in your Library.", OfflinePagereaderActivity.this);
                return null;
            }
        }
        catch (Exception e)
        {
            Mango.alert("There was a problem scanning your Library for the previous chapter.", OfflinePagereaderActivity.this);
            return null;
        }
    }

    private void showPreviousPage()
    {
        if (!mInstantiated)
            return;
        if (mPageIndex == 0)
        {
            LibraryChapter lc = null;
            if (mIsFilesystemChapter)
            {
                FilesystemChapter fsf = getPreviousFilesystemChapter();
                lc = new LibraryChapter();
                if (fsf == null)
                    return;

                Manga m = new Manga();
                m.id = fsf.fileObj.getParent();
                // Find second to last path seperator
                int firstSlash = m.id.lastIndexOf("/");
                m.title = m.id.substring(m.id.lastIndexOf("/", firstSlash - 1));
                Chapter c = new Chapter();
                c.id = fsf.fileObj.getName();
                c.title = fsf.fileObj.getName();
                lc.chapterCount = 1;
                lc.chapterIndex = 1;
                lc.manga = m;
                lc.path = fsf.fileObj.getAbsolutePath() + "/";
                lc.siteId = 1;
                lc.filesystemChapter = fsf;
                lc.chapter = c;
                openFilesystemChapter(lc, 9999);
            }
            else
            {
                lc = getPreviousLibraryChapter();
                if (lc == null)
                    return;
                openChapter(lc, lc.path, 9999);
            }
        }
        else
            goToPage(mPageIndex - 1);
    }

    private void showNextPage()
    {
        if (!mInstantiated)
            return;
        if (mPageIndex == mPages.length - 1)
        {
            LibraryChapter lc = null;
            if (mIsFilesystemChapter)
            {
                FilesystemChapter fsf = getNextFilesystemChapter();
                lc = new LibraryChapter();
                if (fsf == null)
                    return;

                Manga m = new Manga();
                m.id = fsf.fileObj.getParent();
                // Find second to last path seperator
                int firstSlash = m.id.lastIndexOf("/");
                m.title = m.id.substring(m.id.lastIndexOf("/", firstSlash - 1));
                Chapter c = new Chapter();
                c.id = fsf.fileObj.getName();
                c.title = fsf.fileObj.getName();
                lc.chapterCount = 1;
                lc.chapterIndex = 1;
                lc.manga = m;
                lc.path = fsf.fileObj.getAbsolutePath() + "/";
                lc.siteId = 1;
                lc.filesystemChapter = fsf;
                lc.chapter = c;
                openFilesystemChapter(lc, 0);
            }
            else
            {
                lc = getNextLibraryChapter();
                if (lc == null)
                    return;
                openChapter(lc, lc.path, 0);
            }
        }
        else
            goToPage(mPageIndex + 1);
    }

    public void goToPage(int index)
    {
        if (mBusy)
            return;
        mBusy = true;

        try
        {
            Bitmap bm = MangoLibraryIO.readBitmapFromDisk(mActiveLibraryChapter.path, (mIsFilesystemChapter ? mPages[index].url : mPages[index].id), 1, !mIsFilesystemChapter);
            if (bm == null)
            {
                Intent intent = new Intent(getIntent());
                intent.putExtra("initialpage", (index == -1 ? mInitialPage : index));
                intent.putExtra("lcSerializable", mActiveLibraryChapter);
                setIntent(intent);
                restartApp();
                return;
            }
            displayImage(bm, index);
        }
        catch (Exception e)
        {
            Mango.alert("Exception in goToPage.  Try re-downloading the chapter.\n\n" + Log.getStackTraceString(e), OfflinePagereaderActivity.this);
            Mango.log("Attempted URI: " + mActiveLibraryChapter.path + (mIsFilesystemChapter ? mPages[index].url : mPages[index].id));
            Mango.log("Stack trace: " + Log.getStackTraceString(e));
        }
    }

    private void displayImage(Bitmap bitmap, int index)
    {
        if (bitmap == null)
            bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.img_decodefailure);

        if (index == mInitialPage)
            mInstantiated = true;
        mBusy = false;

        if (index < mPageIndex)
            mAnimateSlideIn = false;
        else
            mAnimateSlideIn = true;

        if (Mango.getSharedPreferences().getBoolean("leftRightReading", false))
            mAnimateSlideIn = !mAnimateSlideIn;

        mPageIndex = index;

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                updateReadingProgress();
            }
        }).start();
        clearStatus();

        try
        {
            setTitlebarText(mActiveLibraryChapter.manga.title + " " + mActiveLibraryChapter.chapter.id + " page " + mPages[index].id + " (" + (index + 1) + "/" + mPages.length + ")", false);
        }
        catch (NullPointerException ex)
        {
            Mango.log("oops! Nullpointerexception at setTitlebarText in displayImage (offline). Killing activity.");
            cleanup();
            finish();
            return;
        }

        if (!Mango.getSharedPreferences().getBoolean("disableAnimation", false))
        {
            try
            {
                if (mPageField.getImageBitmap() != null)
                {
                    Bitmap bm = Bitmap.createBitmap(mPageField.getWidth(), mPageField.getHeight(), Config.ARGB_8888);
                    Canvas c = new Canvas(bm);
                    mPageField.onDraw(c);
                    if (mAnimateSlideIn)
                        mStaticView.setImageBitmap(bm);
                    else
                        mAnimatorView.setImageBitmap(bm);
                    mStaticView.setVisibility(View.VISIBLE);
                    mAnimatePage = true;
                    mStaticView.invalidate();
                    mPageField.invalidate();
                }
            }
            catch (OutOfMemoryError oom)
            {
                Mango.log("Skipping animation because we're out of memory. :>");
            }
            catch (NullPointerException ex)
            {
                Mango.log("Pagefield is null, probably due to orientation change. Returning.");
                return;
            }
        }

        float scale = mPageField.getScale();

        try
        {
            mPageField.setImageBitmap(bitmap);

            if (Mango.getSharedPreferences().getBoolean("stickyZoom", false))
                mPageField.zoomTo(scale, 0, 0);

            if (Mango.getSharedPreferences().getBoolean("leftRightReading", false))
                mPageField.scrollToTopLeftCorner();
            else
                mPageField.scrollToTopRightCorner();

            newPageCallback();
        }
        catch (NullPointerException ex)
        {
            Mango.log("Oops! NullPointerException around mPageField.setImage() in displayImage. Killing activity.");
            cleanup();
            finish();
            return;
        }
        catch (OutOfMemoryError oom)
        {
            Mango.log("PagereaderActivity ran out of memory.");
            restartApp();
        }
    }

    public void setTitlebarText(CharSequence title, boolean override)
    {
        if (Mango.getSharedPreferences().getBoolean("disablePageBar", false) && !override)
            return;

        try
        {
            mTitlebar.bringToFront();
            mLastSetTitle = System.currentTimeMillis();
            mTitlebarText = (String) title;

            if (Mango.getSharedPreferences().getBoolean("fullscreenReading", false))
            {
                Intent bat = OfflinePagereaderActivity.this.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                String time = DateFormat.getTimeFormat(getApplicationContext()).format(new Date());
                String battery = String.valueOf((level * 100 / scale));
                mTitleStatusText.setText(battery + "%  " + time);
            }

            mTitlebar.bringToFront();
            mTitleText.setText(title);
            if (mTitlebar.getVisibility() != View.VISIBLE)
            {
                mAnimatorView.postDelayed(mTitlebarHideRunnable, 4000);
                mTitlebar.clearAnimation();
                mTitlebar.startAnimation(AnimationUtils.loadAnimation(this, R.anim.titlebarin));
                mTitlebar.setVisibility(View.VISIBLE);
            }
        }
        catch (Exception e)
        {
            String techdata = String.valueOf(mPageField) + ", " + String.valueOf(mTitlebarHideRunnable);
            Mango.log(e.toString() + " at setTitlebarText (" + techdata + ")");
        }
    }

    private Runnable mTitlebarHideRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            try
            {
                long elapsed = System.currentTimeMillis() - mLastSetTitle;
                if (elapsed < 4000)
                {
                    mAnimatorView.postDelayed(mTitlebarHideRunnable, 4000 - elapsed);
                    return;
                }
                //mTitlebar.bringToFront();
                mTitlebar.clearAnimation();
                mTitlebar.startAnimation(AnimationUtils.loadAnimation(OfflinePagereaderActivity.this, R.anim.titlebarout));
                mTitlebar.setVisibility(View.INVISIBLE);
            }
            catch (NullPointerException e)
            {
                // activity closed
            }
        }
    };

    private void setStatus(String text)
    {
        mStatusText.setText(text);
        if (mStatusbar.getVisibility() == View.INVISIBLE)
        {
            mStatusText.startAnimation(mPulseAnimation);
        }
        mStatusbar.setVisibility(View.VISIBLE);
    }

    private void clearStatus()
    {
        mStatusText.setText("");
        mStatusText.clearAnimation();
        mStatusbar.setVisibility(View.INVISIBLE);
    }

    private void addRevertFavorite()
    {
        if (mIsFilesystemChapter)
        {
            Mango.alert("Favorites tracking is not available for content which has not been downloaded through Mango.", OfflinePagereaderActivity.this);
            return;
        }

        MangoSqlite db = new MangoSqlite(this);
        db.open();
        Favorite f = db.getFavoriteForManga(mActiveLibraryChapter.manga);
        if (f == null)
        {
            f = new Favorite();
            f.coverArtUrl = mActiveLibraryChapter.manga.coverart;
            f.isOngoing = !mActiveLibraryChapter.manga.completed;
            f.mangaId = mActiveLibraryChapter.manga.id;
            f.mangaTitle = mActiveLibraryChapter.manga.title;
            f.generateSimpleName();
            f.notificationsEnabled = false;
            f.siteId = mActiveLibraryChapter.siteId;
            db.insertFavorite(f);
            mTrackReadingProgress = 0;
            checkReadingProgress();
            updateReadingProgress();
            Mango.alert(
                    f.mangaTitle
                            + " has been added to your Favorites!\n\nMango will keep track of your progress as you read. Later, you can quickly resume where you left off by going to the Favorites screen.",
                    OfflinePagereaderActivity.this);
        }
        else
        {
            if (mTrackReadingProgress == 2 || !isReadingProgressBehind(f))
            {
                String reason = "oops, reason text is uninitialized! :o";
                if (f.siteId != mActiveLibraryChapter.siteId)
                    reason = "Mango isn't updating your progress because you're reading on a different manga site.";
                else if (isReadingProgressBehind(f))
                    reason = "Mango isn't updating your progress because you've skipped too far ahead of your saved progress.";
                else if (!isReadingProgressBehind(f))
                    reason = "Mango isn't updating your progress because you're reading behind your saved progress.";
                AlertDialog alert = new AlertDialog.Builder(OfflinePagereaderActivity.this).create();
                alert.setTitle("Set Reading Progress Here?");
                alert.setMessage(reason + "\n\nWould you like to manually set your reading progress to this page?");
                alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yes", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        updateReadingProgress(true);
                        mTrackReadingProgress = 0;
                        checkReadingProgress();
                    }
                });
                alert.setButton(DialogInterface.BUTTON_NEGATIVE, "No", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {}
                });
                alert.show();
            }
            else
            {
                AlertDialog alert = new AlertDialog.Builder(OfflinePagereaderActivity.this).create();
                alert.setTitle("Remove Favorite?");
                alert.setMessage("This manga will be deleted from your Favorites and Mango will stop tracking your reading progress.\n\nAre you certain you wish to delete this favorite?");
                alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yes, delete", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        MangoSqlite db = new MangoSqlite(OfflinePagereaderActivity.this);
                        db.open();
                        db.deleteFavorite(db.getFavoriteForManga(mActiveLibraryChapter.manga).rowId);
                        db.close();
                        Mango.alert("This manga has been removed from your favorites. Mango will not track your reading progress.", OfflinePagereaderActivity.this);
                        mTrackReadingProgress = 0;
                        checkReadingProgress();
                    }
                });
                alert.setButton(DialogInterface.BUTTON_NEGATIVE, "No, cancel", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {}
                });
                alert.show();
            }
        }
        db.close();
    }

    private void checkReadingProgress()
    {
        if (mIsFilesystemChapter)
            return;
        if (mTrackReadingProgress > 1)
            return;

        try
        {
            MangoSqlite db = new MangoSqlite(this);
            db.open();
            final Favorite f = db.getFavoriteForManga(mActiveLibraryChapter.manga);
            db.close();
            if (f.siteId == mActiveLibraryChapter.siteId && f.progressChapterId != null)
            {
                if (f.progressChapterIndex < mChapterIndex - 1)
                {
                    AlertDialog alert = new AlertDialog.Builder(OfflinePagereaderActivity.this).create();
                    alert.setTitle("Set Reading Progress Ahead?");
                    alert.setMessage("Your reading progress for this manga is Chapter " + f.progressChapterId
                            + ", which is pretty far behind this chapter.\n\nWould you like to set your reading progress this far ahead?");
                    alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yep", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            mTrackReadingProgress = 1;
                            updateReadingProgress(true);
                            Mango.alert("Okay. Mango has set your reading progress to this chapter.", OfflinePagereaderActivity.this);
                        }
                    });
                    alert.setButton(DialogInterface.BUTTON_NEGATIVE, "No", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            mTrackReadingProgress = 2;
                            Mango.alert("Okay. Mango won't update your progress while you read ahead.", OfflinePagereaderActivity.this);
                        }
                    });
                    alert.show();
                }
                else
                    mTrackReadingProgress = 1;
            }
            else if (f.siteId == mActiveLibraryChapter.siteId && f.progressChapterId == null)
            {
                mTrackReadingProgress = 1;
            }
            else
            {
                AlertDialog alert = new AlertDialog.Builder(OfflinePagereaderActivity.this).create();
                alert.setTitle("Track Reading Progress?");
                alert.setMessage("You're already reading this manga on " + Mango.getSiteName(f.siteId) + ", but this chapter was downloaded from " + Mango.getSiteName(mActiveLibraryChapter.siteId)
                        + ". Mango can only track your progress for one manga source at a time. Would you like to start tracking your reading progress for this manga on "
                        + Mango.getSiteName(mActiveLibraryChapter.siteId) + " instead?");
                alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yes", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        mTrackReadingProgress = 1;
                        MangoSqlite db = new MangoSqlite(OfflinePagereaderActivity.this);
                        db.open();
                        db.clearFavoriteProgress(f.rowId);
                        db.close();
                        updateReadingProgress();
                        Mango.alert("Okay. Mango is now tracking your reading progress for this manga on " + Mango.getSiteName(mActiveLibraryChapter.siteId) + ".",
                                OfflinePagereaderActivity.this);
                    }
                });
                alert.setButton(DialogInterface.BUTTON_NEGATIVE, "No", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        mTrackReadingProgress = 2;
                        Mango.alert("Okay. Mango won't track your progress for this manga while you read on " + Mango.getSiteName(mActiveLibraryChapter.siteId), OfflinePagereaderActivity.this);
                    }
                });
                alert.show();
            }
        }
        catch (Exception e)
        {
            mTrackReadingProgress = 3;
        }
    }

    private boolean isReadingProgressBehind(Favorite f)
    {
        try
        {
            if (mChapterIndex >= f.progressChapterIndex)
            {
                if ((mChapterIndex == f.progressChapterIndex && mPageIndex >= f.progressPageIndex) || mChapterIndex > f.progressChapterIndex)
                {
                    return true;
                }
            }
        }
        catch (Exception e)
        {
        }
        return false;
    }

    private void updateReadingProgress()
    {
        updateReadingProgress(false);
    }

    private void updateReadingProgress(boolean forceUpdate)
    {
        if (mIsFilesystemChapter)
            return;

        if (mTrackReadingProgress != 1 && !forceUpdate)
            return;

        MangoSqlite db = new MangoSqlite(OfflinePagereaderActivity.this);
        try
        {
            db.open();
            Favorite f = db.getFavoriteForManga(mActiveLibraryChapter.manga);
            f.mangaId = mActiveLibraryChapter.manga.id;
            f.siteId = mActiveLibraryChapter.siteId;

            if (isReadingProgressBehind(f) || forceUpdate)
            {
                f.progressChapterId = mActiveLibraryChapter.chapter.id;
                f.progressChapterIndex = mChapterIndex;
                f.progressChapterName = mActiveLibraryChapter.chapter.title;
                f.progressChapterUrl = mActiveLibraryChapter.chapter.url;
                f.progressPageIndex = mPageIndex;
                f.readDate = System.currentTimeMillis();
            }
            db.updateFavorite(f);
        }
        catch (Exception e)
        {
            return;
        }
        finally
        {
            db.close();
        }
    }

    private void addRecent()
    {
        if (Mango.getSharedPreferences().getBoolean("disableHistory", false))
            return;
        if (mIsFilesystemChapter)
            return;
        ArrayList<Bookmark> recentArrayList = new ArrayList<Bookmark>();
        MangoSqlite db = new MangoSqlite(OfflinePagereaderActivity.this);
        long updateRecentRowId = -1;
        try
        {
            db.open();
            Bookmark[] bmarray = db.getAllHistoryArray(MangoSqlite.KEY_UPDATETIME + " ASC", MangoSqlite.KEY_MANGAID + " = '" + mActiveLibraryChapter.manga.id + "'", true);
            for (int i = 0; i < bmarray.length; i++)
            {
                Bookmark bm = bmarray[i];
                if (bm.bookmarkType == Bookmark.RECENT && bm.siteId == Mango.SITE_LOCAL)
                {
                    recentArrayList.add(bm);
                    if (bm.mangaId.equals(mActiveLibraryChapter.manga.id) && bm.chapterIndex == mChapterIndex)
                        updateRecentRowId = bm.rowId;
                }
                if (recentArrayList.size() >= 10000 && updateRecentRowId == -1)
                {
                    db.deleteBookmark(recentArrayList.get(0).rowId);
                    recentArrayList.remove(0);
                }
            }
            if (updateRecentRowId != -1)
                db.deleteBookmark(updateRecentRowId);
            db.insertRecentBookmark(mActiveLibraryChapter.manga.id, mActiveLibraryChapter.manga.title, mChapterIndex, mActiveLibraryChapter.chapter.title, mActiveLibraryChapter.chapter.id,
                    mActiveLibraryChapter.chapterCount, Mango.SITE_LOCAL, false);
        }
        catch (SQLException e)
        {
            Mango.alert("Mango encountered an error while trying to create a bookmark.\n\n" + e.toString(), OfflinePagereaderActivity.this);
        }
        finally
        {
            db.close();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event)
    {
        if (!mInstantiated)
            return super.dispatchKeyEvent(event);
        // Sony Xperia PLAY L1 shoulder button
        if (event.getKeyCode() == 102 && event.getAction() == KeyEvent.ACTION_UP)
        {
            if (Mango.getSharedPreferences().getBoolean("leftRightReading", false))
                showPreviousPage();
            else
                showNextPage();
            return true;
        }
        // Sony Xperia PLAY R1 shoulder button
        if (event.getKeyCode() == 103 && event.getAction() == KeyEvent.ACTION_UP)
        {
            if (Mango.getSharedPreferences().getBoolean("leftRightReading", false))
                showNextPage();
            else
                showPreviousPage();
            return true;
        }
        // For volume rocker control, we need to consume both the up and down key events.
        // But we only want to change pages once.
        // Volume Up
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP)
        {
            if (Mango.getSharedPreferences().getBoolean("volumeRockerControls", false))
            {
                if (event.getAction() == KeyEvent.ACTION_UP)
                {
                    if (Mango.getSharedPreferences().getBoolean("leftRightReading", false))
                        showPreviousPage();
                    else
                        showNextPage();
                }
                return true;
            }
        }
        // Volume down
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN)
        {
            if (Mango.getSharedPreferences().getBoolean("volumeRockerControls", false))
            {
                if (event.getAction() == KeyEvent.ACTION_UP)
                {
                    if (Mango.getSharedPreferences().getBoolean("leftRightReading", false))
                        showNextPage();
                    else
                        showPreviousPage();
                }
                return true;
            }
        }
        // Enter or Dpad Center
        if ((event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER) && event.getAction() == KeyEvent.ACTION_UP)
        {
            showNextPage();
            return true;
        }        // Enter or Dpad Center
        if ((event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER) && event.getAction() == KeyEvent.ACTION_UP)
        {
            showNextPage();
            return true;
        }
        // Dpad down or S
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN || event.getKeyCode() == KeyEvent.KEYCODE_S ||
                event.getKeyCode() == KeyEvent.KEYCODE_SPACE
                        && (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_MULTIPLE))
        {
            mPageField.scrollDown();
        }
        // Dpad up or W
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP || event.getKeyCode() == KeyEvent.KEYCODE_W &&
                (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_MULTIPLE))
        {
            mPageField.scrollUp();
        }
        // Dpad left or A
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT || event.getKeyCode() == KeyEvent.KEYCODE_A
                && (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_MULTIPLE))
        {
            mPageField.scrollLeft();
        }
        // Dpad right or D
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT || event.getKeyCode() == KeyEvent.KEYCODE_D
                && (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_MULTIPLE))
        {
            mPageField.scrollRight();
        }
        return super.dispatchKeyEvent(event);
    }

    protected void newPageCallback()
    {

        // if (Mango.getSharedPreferences().getBoolean("stickyZoom", false))
        // mZoomControl.getZoomState().setPanX(mZoomControl.getMaxPanX());
        // else
        // mZoomControl.getZoomState().setPanX(0.5f);

        if (!mAnimatePage)
            return;

        mAnimatePage = false;
        mAnimatorView.setVisibility(View.VISIBLE);
        Bitmap bm = Bitmap.createBitmap(mPageField.getWidth(), mPageField.getHeight(), Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        mPageField.onDraw(c);

        if (mAnimateSlideIn)
            mAnimatorView.setImageBitmap(bm);
        else
            mStaticView.setImageBitmap(bm);
        mStaticView.bringToFront();
        mAnimatorView.bringToFront();
        mTitlebar.bringToFront();
        mAnimatorView.clearAnimation();
        if (mAnimateSlideIn)
        {
            mAnimatorView.setAnimation(mSlideInAnimation);
            mSlideInAnimation.reset();
            mSlideInAnimation.startNow();
        }
        else
        {
            mAnimatorView.setAnimation(mSlideOffAnimation);
            mSlideOffAnimation.reset();
            mSlideOffAnimation.startNow();
        }
        mAnimatorView.postDelayed(new Runnable()
        {

            @Override
            public void run()
            {
                // clean up and free resources used by animator
                try
                {
                    mPageField.bringToFront();
                    mAnimatorView.setVisibility(View.INVISIBLE);
                    mStaticView.setVisibility(View.INVISIBLE);

                    mAnimatorView.postDelayed(new Runnable()
                    {

                        @Override
                        public void run()
                        {
                            if (mAnimateSlideIn)
                            {
                                if (mSlideInAnimation.hasEnded())
                                {
                                    mAnimatorView.setImageBitmap(null);
                                    mStaticView.setImageBitmap(null);
                                }
                            }
                            else
                            {
                                if (mSlideOffAnimation.hasEnded())
                                {
                                    mAnimatorView.setImageBitmap(null);
                                    mStaticView.setImageBitmap(null);
                                }
                            }
                        }
                    }, 500);
                }
                catch (NullPointerException e)
                {
                    // cleanup() has already been called
                }
            }
        }, 350);
    }

    protected void setTouchControlCallbacks()
    {
        mPageField.setOnTapCallback(new Runnable()
        {
            @Override
            public void run()
            {
                if (Mango.getSharedPreferences().getBoolean("disableTapToAdvance", false))
                    return;
                showNextPage();
            }

        });
        mPageField.setOnBackTapCallback(new Runnable()
        {
            @Override
            public void run()
            {
                if (Mango.getSharedPreferences().getBoolean("disableTapToAdvance", false))
                    return;
                showPreviousPage();
            }
        });
        mPageField.setOnLeftFlingCallback(new Runnable()
        {
            @Override
            public void run()
            {
                if (Mango.getSharedPreferences().getBoolean("disableSwipeControls", false))
                    return;
                if (Mango.getSharedPreferences().getBoolean("leftRightReading", false))
                    showNextPage();
                else
                    showPreviousPage();
            }

        });
        mPageField.setOnRightFlingCallback(new Runnable()
        {
            @Override
            public void run()
            {
                if (Mango.getSharedPreferences().getBoolean("disableSwipeControls", false))
                    return;
                if (Mango.getSharedPreferences().getBoolean("leftRightReading", false))
                    showPreviousPage();
                else
                    showNextPage();
            }

        });
    }

    private void doRepairPage()
    {
        Thread t = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                try
                {
                    final boolean retval = downloadPage(mUrlPrefix + mPages[mPageIndex].id, mActiveLibraryChapter.path, mPages[mPageIndex]);
                    mPageField.post(new Runnable()
                    {

                        @Override
                        public void run()
                        {
                            if (retval)
                            {
                                Mango.alert(
                                        "The page was successfully re-downloaded.  If the image still looks damaged to you, then the problem is with "
                                                + Mango.getSiteName(mActiveLibraryChapter.siteId) + ", not your phone or Mango.", OfflinePagereaderActivity.this);
                                Bitmap bm = MangoLibraryIO.readBitmapFromDisk(mActiveLibraryChapter.path, mPages[mPageIndex].id, 1, true);
                                displayImage(bm, mPageIndex);
                            }
                            else
                            {
                                Mango.alert(
                                        "Mango wasn't able to re-download the page.  If your internet connection is fine, the page is probably just missing on "
                                                + Mango.getSiteName(mActiveLibraryChapter.siteId) + ".", OfflinePagereaderActivity.this);
                            }
                        }
                    });
                }
                catch (Exception e)
                {
                    Mango.log(e.toString() + " when trying to repair offline page.");
                }
            }
        });
        t.start();
    }

    private boolean downloadPage(String url, String path, Page page)
    {
        if (!mSubstringStart.equals(""))
            url = mUrlPrefix + page.url;
        else
            url += ".jpg";

        MangoHttpResponse response = MangoHttp.downloadData(url, this);
        if (response.exception)
            return false;

        if (response.contentType.contains("text"))
        {
            url = magic(response.toString());

            if (url.contains("Exception"))
                return false;

            response = MangoHttp.downloadData(url, this);
            if (response.exception)
                return false;
        }
        response.writeEncodedImageToCache(1, path, page.id);

        Mango.log(path + ", " + page.id);

        return true;
    }

    private String magic(String data)
    {
        try
        {
            if (mSubstringAltStart.equals(""))
            {
                int srcStart = data.indexOf(mSubstringStart) + mSubstringStart.length();
                int srcEnd = data.indexOf("\"", srcStart);
                return data.substring(srcStart, srcEnd);
            }
            int substringOffset = data.indexOf(mSubstringAltStart) + mSubstringAltStart.length();
            substringOffset -= 150; // lolmagic literal
            int srcStart = data.indexOf(mSubstringStart, substringOffset) + mSubstringStart.length();
            int srcEnd = data.indexOf("\"", srcStart);
            return data.substring(srcStart, srcEnd);
        }
        catch (Exception ex)
        {
            return ex.getClass().getSimpleName();
        }
    }

    private class FolderComparator implements Comparator<FilesystemChapter>
    {

        @Override
        public int compare(FilesystemChapter o1, FilesystemChapter o2)
        {
            if (o1.isParentFolder)
                return -1;
            if (o2.isParentFolder)
                return 1;
            String tmp1 = o1.fileObj.getName().toLowerCase();
            String tmp2 = o2.fileObj.getName().toLowerCase();
            return tmp1.compareTo(tmp2);
        }
    }
}
