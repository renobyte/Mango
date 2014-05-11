package net.leetsoft.mangareader.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.gson.Gson;
import net.leetsoft.mangareader.*;
import net.leetsoft.mangareader.services.NotifierService;
import net.leetsoft.mangareader.ui.MangoAdWrapperView;
import net.leetsoft.mangareader.ui.MangoFileSelector;
import net.leetsoft.mangareader.ui.MangoTutorialHandler;

import java.io.*;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

public class FavoritesActivity extends MangoActivity
{
    private ListView mListview;
    private TextView mEmptyView;
    private Favorite[] mFavorites;
    private FavoritesAdapter mAdapter;
    private EditText mFilterEdit;
    private RelativeLayout mFilterOverlay;
    private ImageButton mFilterButton;
    private CoverArtLoader mLoader;
    private CoverArtDownloader mDownloader;
    private Bitmap mOverlay;
    private HashMap<String, SoftReference<Bitmap>> mCoverCache = new HashMap<String, SoftReference<Bitmap>>();
    private volatile ArrayList<Integer> mPendingThumbnails;
    private volatile ArrayList<Integer> mPendingDownloads;
    private boolean[] mQueued;
    private boolean mDisableCovers;
    private Runnable mLoaderRunnable;
    private Bitmap mNoCoverArt;
    private int mMenuPosition;
    private int mMenuType;
    private String mCoverArtSelector;                                         // cover
    // art
    // selector
    // path
    private int mTapMode;                                                  // 0=normal,
    // 1=tag
    private String mSearchString;
    private boolean mEventsSent;
    private ServiceConnection mConnection = new ServiceConnection()
    {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {}

        @Override
        public void onServiceDisconnected(ComponentName arg0)
        {}
    };

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        this.setTitle("Favorites", null);
        inflateLayoutManager(this, R.layout.favoritesscreen);
        mListview = (ListView) findViewById(R.id.favoritesList);
        mEmptyView = (TextView) findViewById(R.id.favoritesEmpty);
        mFilterEdit = (EditText) findViewById(R.id.favoritesFilterEdit);
        mFilterOverlay = (RelativeLayout) findViewById(R.id.favoritesFilterOverlay);
        mFilterButton = (ImageButton) findViewById(R.id.favoritesFilterButton);
        mFilterOverlay.setVisibility(View.INVISIBLE);
        mFilterButton.setOnClickListener(new View.OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                searchClicked();
            }
        });
        mFilterEdit.addTextChangedListener(new TextWatcher()
        {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                searchTextChanged();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {}

            @Override
            public void afterTextChanged(Editable s)
            {}
        });

        registerForContextMenu(mListview);
        mListview.setOnCreateContextMenuListener(this);

        super.setAdLayout((MangoAdWrapperView) findViewById(R.id.favoritesAdLayout));
        super.setJpBackground(R.drawable.jp_bg_bookmarks);


        mNoCoverArt = createCoverArt(BitmapFactory.decodeResource(getResources(), R.drawable.placeholder_error));
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        if (Mango.DIALOG_DOWNLOADING != null)
            Mango.DIALOG_DOWNLOADING.dismiss();
        if (id == 0)
        {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle("Hang tight...");
            dialog.setMessage("Mango is downloading some more information about this favorite...");
            dialog.setIndeterminate(true);
            dialog.setCancelable(true);
            Mango.DIALOG_DOWNLOADING = dialog;
            return dialog;
        }
        return super.onCreateDialog(id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.favoritesmenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.menuFavoriteSearch)
        {
            toggleSearch();
            return true;
        }
        if (item.getItemId() == R.id.menuFavoriteRefresh)
        {
            int len = 0;
            try
            {
                MangoSqlite db = new MangoSqlite(FavoritesActivity.this);
                db.open();
                len = db.getAllFavorites("notificationsEnabled = 1").length;
                db.close();
            }
            catch (Exception e)
            {
                Mango.log("Unable to check notificationsEnabled count! " + e.toString());
                len = 0;
            }

            if (len == 0)
            {
                Mango.alert(
                        "Before Mango can check for new chapters, you need to tell it which favorites you want it to check!\n\nPress Menu, select Notifications, then mark favorites you want Mango to check by tapping on them!",
                        "No favorites to check!", FavoritesActivity.this);
                return true;
            }
            Intent intent = new Intent(this, NotifierService.class);
            intent.setAction("MANUALCHECK");
            startService(intent);
            return true;
        }
        if (item.getItemId() == R.id.menuFavoriteNotifications)
        {
            if (mTapMode == 2)
            {
                mTapMode = 0;
                Toast.makeText(this, "You're no longer in Notification Mode.", Toast.LENGTH_SHORT).show();
                if (!Mango.getSharedPreferences().getBoolean("notifierEnabled", false))
                    Mango.alert("Remember to go to Settings and Help >> Notification Preferences to enable notifications!", "Don't forget!", FavoritesActivity.this);
                return true;
            }

            if (!Mango.getSharedPreferences().getBoolean("notifierEnabled", false))
            {
                AlertDialog alert = new AlertDialog.Builder(FavoritesActivity.this).create();
                alert.setTitle("Notifications are disabled!");
                alert.setMessage("Notications are not enabled.  Would you like to go to the Notification Preferences screen now?");
                alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yes!", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        Intent myIntent = new Intent();
                        myIntent.setClass(Mango.CONTEXT, NotifierPrefsActivity.class);
                        startActivity(myIntent);
                    }
                });
                alert.setButton(DialogInterface.BUTTON_NEGATIVE, "No, later.", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        Mango.alert(
                                "Okay.  You can enable notifications later.\n\nYou're now in Notification Mode.  Tap on a Favorite to toggle notifications for that Favorite.\n\nPress Back when finished.",
                                FavoritesActivity.this);
                        mTapMode = 2;
                        return;
                    }
                });
                alert.show();
            }
            else
            {
                mTapMode = 2;
                Toast.makeText(this, "You're now in Notification Mode. Tap on a Favorite to toggle notifications for that Favorite.\n\nPress Back when finished.", Toast.LENGTH_LONG).show();
            }
            return true;
        }
        if (item.getItemId() == R.id.menuFavoriteClearCovers)
        {
            MangoCache.wipeCoverArtCache();
            initializeFavorites(true);
            return true;
        }
        if (item.getItemId() == R.id.menuFavoriteTags)
        {
            if (mTapMode == 1)
            {
                mTapMode = 0;
                Toast.makeText(this, "You're no longer in Tag Mode.", Toast.LENGTH_SHORT).show();
                return true;
            }
            mTapMode = 1;
            Toast.makeText(this, "You're now in Tag Mode. Tap on a favorite to quickly change its tag.\n\nPress Back when finished.", Toast.LENGTH_LONG).show();
            return true;
        }
        if (item.getItemId() == R.id.menuFavoriteSort)
        {
            mMenuType = 3;
            showMenu();
            return true;
        }
        if (item.getItemId() == R.id.menuFavoriteFilter)
        {
            mMenuType = 4;
            showMenu();
            return true;
        }
        if (item.getItemId() == R.id.menuFavoriteEditTags)
        {
            mMenuType = 2;
            showMenu();
            return true;
        }
        if (item.getItemId() == R.id.menuFavoriteRestore)
        {
            popupRestoreFavorites();
            return true;
        }
        if (item.getItemId() == R.id.menuFavoriteBackup)
        {
            popupBackupFavorites();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        menu.setHeaderTitle(mFavorites[info.position].mangaTitle);
        mMenuPosition = info.position;
        if (mFavorites[info.position].progressChapterId != null)
            menu.add(Menu.NONE, 0, 0, "Resume Reading");
        menu.add(Menu.NONE, 1, 1, "View Chapter List");
        menu.add(Menu.NONE, 2, 2, "Set Tag");
        menu.add(Menu.NONE, 3, 3, "Clear Progress");
        menu.add(Menu.NONE, 4, 4, "Delete Favorite");
        menu.add(Menu.NONE, 5, 5, "Set Cover Art");
        if (mFavorites[info.position].lastChapterId != null)
            menu.add(Menu.NONE, 6, 6, "Clear Latest Chapter");
        menu.add(Menu.NONE, 7, 7, (mFavorites[info.position].newChapterAvailable ? "Unm" : "M") + "ark as New Chapter");
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item)
    {
        switch (item.getItemId())
        {
            case 0:
                openFavorite(mMenuPosition);
                return true;
            case 1:
                openFavoriteChapters(mMenuPosition, false);
                return true;
            case 2:
                mMenuType = 1;
                showMenu();
                return true;
            case 3:
                MangoSqlite db = new MangoSqlite(this);
                db.open();
                db.clearFavoriteProgress(mFavorites[mMenuPosition].rowId);
                db.close();
                initializeFavorites(true);
                return true;
            case 4:
                deleteFavorite(mMenuPosition);
                return true;
            case 5:
                changeCoverArt(mMenuPosition);
                return true;
            case 6:
                MangoSqlite db2 = new MangoSqlite(this);
                db2.open();
                db2.clearFavoriteLatest(mFavorites[mMenuPosition].rowId);
                db2.close();
                initializeFavorites(true);
                return true;
            case 7:
                MangoSqlite db3 = new MangoSqlite(this);
                db3.open();
                mFavorites[mMenuPosition].newChapterAvailable = !mFavorites[mMenuPosition].newChapterAvailable;
                db3.updateFavorite(mFavorites[mMenuPosition]);
                db3.close();
                initializeFavorites(true);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void changeCoverArt(int index)
    {
        mMenuPosition = index;
        mMenuType = 5;
        showMenu();
    }

    private void showMenu()
    {
        if (mMenuType == 1) // edit tag
        {
            AlertDialog.Builder a = new AlertDialog.Builder(this);
            a.setTitle("Set tag for " + mFavorites[mMenuPosition].mangaTitle + ":");
            if (mFavorites[mMenuPosition].tagId != 0)
                a.setIcon(Mango.getTagDrawable(mFavorites[mMenuPosition].tagId, false));
            String[] options = new String[]{"None",
                    "[Green]\t\t" + Mango.getTagName(1),
                    "[Red]\t\t\t" + Mango.getTagName(2),
                    "[Blue]\t\t\t" + Mango.getTagName(3),
                    "[Yellow]\t\t" + Mango.getTagName(4),
                    "[Purple]\t" + Mango.getTagName(5),
                    "[Gray]\t\t" + Mango.getTagName(6)};
            a.setItems(options, new DialogInterface.OnClickListener()
            {

                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    setTag(mFavorites[mMenuPosition], which);
                }
            });
            a.show();
        }
        else if (mMenuType == 2) // edit tag
        {
            AlertDialog.Builder a = new AlertDialog.Builder(this);
            a.setTitle("Which tag would you like to edit?");
            String[] options = new String[]{"[Green]\t\t" + Mango.getTagName(1),
                    "[Red]\t\t\t" + Mango.getTagName(2),
                    "[Blue]\t\t\t" + Mango.getTagName(3),
                    "[Yellow]\t\t" + Mango.getTagName(4),
                    "[Purple]\t" + Mango.getTagName(5),
                    "[Gray]\t\t" + Mango.getTagName(6)};
            a.setItems(options, new DialogInterface.OnClickListener()
            {

                @Override
                public void onClick(DialogInterface dialog, final int which)
                {
                    AlertDialog.Builder alert = new AlertDialog.Builder(FavoritesActivity.this);
                    alert.setIcon(Mango.getTagDrawable(which + 1, false));
                    alert.setTitle("Edit Tag Name");
                    alert.setMessage("Type a new name for the '" + Mango.getTagName(which + 1) + "' tag.");
                    final EditText input = new EditText(FavoritesActivity.this);
                    alert.setView(input);
                    alert.setPositiveButton("Rename!", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton)
                        {
                            String value = input.getText().toString();
                            if (value.length() > 20)
                                value = value.substring(0, 20);
                            Mango.getSharedPreferences().edit().putString("tag" + (which + 1) + "Name", value).commit();
                        }
                    });
                    alert.setNegativeButton("Never mind", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton)
                        {}
                    });
                    alert.show();
                }
            });
            a.show();
        }
        else if (mMenuType == 3) // sort
        {
            AlertDialog.Builder a = new AlertDialog.Builder(this);
            a.setTitle("Sort Order");
            String[] options = new String[]{"Alphabetically",
                    "By Last Viewed Date",
                    "By Tag",
                    "By Site",
                    "By Last Updated Date"};
            int selection = Mango.getSharedPreferences().getInt("favoritesSortType", 0);
            a.setSingleChoiceItems(options, selection, new DialogInterface.OnClickListener()
            {

                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    Mango.getSharedPreferences().edit().putInt("favoritesSortType", which).commit();
                    dialog.dismiss();
                    initializeFavorites(true);
                }
            });
            a.show();
        }
        else if (mMenuType == 4) // filter
        {
            AlertDialog.Builder a = new AlertDialog.Builder(this);
            a.setTitle("Tag Filtering");
            String[] options = new String[]{"Untagged",
                    "[Green]\t\t" + Mango.getTagName(1),
                    "[Red]\t\t\t" + Mango.getTagName(2),
                    "[Blue]\t\t\t" + Mango.getTagName(3),
                    "[Yellow]\t\t" + Mango.getTagName(4),
                    "[Purple]\t" + Mango.getTagName(5),
                    "[Gray]\t\t" + Mango.getTagName(6)};
            boolean[] selection = new boolean[]{Mango.getSharedPreferences().getBoolean("favoritesFilterTag0", true),
                    Mango.getSharedPreferences().getBoolean("favoritesFilterTag1", true),
                    Mango.getSharedPreferences().getBoolean("favoritesFilterTag2", true),
                    Mango.getSharedPreferences().getBoolean("favoritesFilterTag3", true),
                    Mango.getSharedPreferences().getBoolean("favoritesFilterTag4", true),
                    Mango.getSharedPreferences().getBoolean("favoritesFilterTag5", true),
                    Mango.getSharedPreferences().getBoolean("favoritesFilterTag6", true)};
            a.setMultiChoiceItems(options, selection, new DialogInterface.OnMultiChoiceClickListener()
            {

                @Override
                public void onClick(DialogInterface dialog, int which, boolean isChecked)
                {
                    Mango.getSharedPreferences().edit().putBoolean("favoritesFilterTag" + which, isChecked).commit();
                }
            });
            a.setPositiveButton("Apply", new OnClickListener()
            {

                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    initializeFavorites(true);
                    dialog.dismiss();
                }
            });
            a.show();
        }
        else if (mMenuType == 5) // change cover art step 1
        {
            AlertDialog.Builder a = new AlertDialog.Builder(this);
            a.setTitle("Change cover art for " + mFavorites[mMenuPosition].mangaTitle + ":");
            String[] options = new String[]{"Use Default Cover Art",
                    "Use Custom Cover Art"};
            a.setItems(options, new DialogInterface.OnClickListener()
            {

                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    if (which == 0)
                    {
                        if (mFavorites[mMenuPosition].coverArtUrl.contains("file@"))
                            mFavorites[mMenuPosition].coverArtUrl = mFavorites[mMenuPosition].coverArtUrl.substring(5, mFavorites[mMenuPosition].coverArtUrl.indexOf("@", 6));

                        if (!mFavorites[mMenuPosition].coverArtUrl.contains("http"))
                        {
                            mFavorites[mMenuPosition].coverArtUrl = ".";
                            Toast.makeText(FavoritesActivity.this, "Open the All Manga list to reload the default cover art.", Toast.LENGTH_SHORT).show();
                        }
                        MangoSqlite db = new MangoSqlite(FavoritesActivity.this);
                        db.open();
                        db.updateFavorite(mFavorites[mMenuPosition]);
                        db.close();
                        refreshCovers();
                    }
                    else
                    {
                        if (mFavorites[mMenuPosition].coverArtUrl.contains("file@"))
                        {
                            File f = new File(mFavorites[mMenuPosition].coverArtUrl.substring(mFavorites[mMenuPosition].coverArtUrl.indexOf("@", 6) + 1));
                            mCoverArtSelector = f.getParent();

                        }
                        else
                            mCoverArtSelector = Mango.getDataDirectory().getAbsolutePath();
                        mMenuType = 6;
                        showMenu();
                    }
                }
            });
            a.show();
        }
        else if (mMenuType == 6)
        {
            AlertDialog.Builder a = new AlertDialog.Builder(this);
            a.setTitle(mCoverArtSelector);
            File f = new File(mCoverArtSelector);
            FileFilter filter = new FileFilter()
            {

                @Override
                public boolean accept(File pathname)
                {
                    if (pathname.isDirectory())
                        return true;
                    if (pathname.getAbsolutePath().endsWith("jpg") || pathname.getAbsolutePath().endsWith("png"))
                        return true;
                    return false;
                }
            };
            File[] files = f.listFiles(filter);
            int sizeAddition = 0;
            if (!mCoverArtSelector.equals("/"))
                sizeAddition = 1;
            if (files == null)
                files = new File[0];

            final String[] fStrings = new String[files.length + sizeAddition];

            if (files.length == 0)
            {
                fStrings[0] = " Parent Folder";
            }
            else
            {
                for (int i = 0; i < fStrings.length; i++)
                {

                    if (i == 0 && !mCoverArtSelector.equals("/"))
                    {
                        fStrings[i] = " Parent Folder";
                        continue;
                    }
                    if (files[i - sizeAddition].isDirectory())
                        fStrings[i] = "/" + files[i - sizeAddition].getName();
                    else
                        fStrings[i] = files[i - sizeAddition].getName();
                }
                Arrays.sort(fStrings);
            }
            a.setItems(fStrings, new DialogInterface.OnClickListener()
            {

                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    String fileName = fStrings[which];
                    if (fStrings[which].startsWith("/"))
                        fStrings[which] = fStrings[which].substring(1);
                    if (fileName.equals(" Parent Folder"))
                    {
                        File file = new File(mCoverArtSelector);
                        mCoverArtSelector = file.getParent();
                        showMenu();
                    }
                    else
                    {
                        File file = new File(mCoverArtSelector + "/" + fileName);
                        if (file.isDirectory())
                        {
                            mCoverArtSelector = file.getAbsolutePath();
                            showMenu();
                        }
                        else
                        {
                            String s = mFavorites[mMenuPosition].coverArtUrl;
                            if (s.contains("file@"))
                                s = s.substring(5, s.indexOf("@", 6));

                            MangoSqlite db = new MangoSqlite(FavoritesActivity.this);
                            db.open();
                            s = "file@" + s + "@" + file.getAbsolutePath();
                            mFavorites[mMenuPosition].coverArtUrl = s;
                            db.updateFavorite(mFavorites[mMenuPosition]);
                            db.close();
                            refreshCovers();
                        }
                    }
                }
            });
            a.show();
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        initializeFavorites(true);
    }

    private void initializeFavorites(boolean clearCoverArtCache)
    {
        try
        {
            mDisableCovers = Mango.getSharedPreferences().getBoolean("disableFavoritesMenuCovers", false);
            File f = new File(getApplicationContext().getFilesDir().getPath() + "/notifierreport.txt");
            if (f.exists())
            {
                try
                {
                    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                    StringBuilder builder = new StringBuilder();
                    char[] buffer = new char[8192];
                    int charsRead = 0;
                    while ((charsRead = br.read(buffer)) > 0)
                    {
                        builder.append(buffer, 0, charsRead);
                        buffer = new char[8192];
                    }
                    Mango.alert("The following manga have new chapters!\n\n" + builder.toString().trim(), "New Chapters Available", this);
                    f.delete();
                }
                catch (Exception e)
                {
                    // TODO: handle exception
                }
            }

            int scroll = mListview.getFirstVisiblePosition();

            String whereClause = "";
            for (int i = 0; i < 7; i++)
            {
                if (!Mango.getSharedPreferences().getBoolean("favoritesFilterTag" + i, true))
                {
                    if (whereClause.length() > 0)
                        whereClause += " AND ";
                    whereClause += "NOT tagId=" + i;
                }
            }

            if (mSearchString != null)
            {
                if (whereClause.length() > 0)
                    whereClause += " AND ";
                whereClause += "mangaTitle LIKE '" + mSearchString + "%'";
            }

            if (whereClause.length() == 0)
                whereClause = null;

            MangoSqlite db = new MangoSqlite(this);
            db.open();
            long size = db.getFavoriteCount(null);
            Favorite[] temp = db.getAllFavorites(whereClause);
            Arrays.sort(temp, new FavoriteComparator());

            mFavorites = temp;
            mPendingThumbnails = new ArrayList<Integer>();
            mPendingDownloads = new ArrayList<Integer>();
            mQueued = new boolean[mFavorites.length];

            Runnable r = new Runnable()
            {

                @Override
                public void run()
                {
                    MangoSqlite database = null;
                    try
                    {
                        database = new MangoSqlite(FavoritesActivity.this);
                        database.open();
                        for (int i = 0; i < mFavorites.length; i++)
                        {
                            Favorite f = mFavorites[i];
                            if (database.getLibrarySize("WHERE lower(mangaId) = '" + f.mangaId.toLowerCase() + "' OR lower(mangaSimpleName) = '" + f.mangaSimpleName.toLowerCase() + "'") > 0)
                                f.savedInLibrary = true;
                            if (f.progressChapterId != null)
                            {
                                if (database.getLibrarySize("WHERE (lower(mangaId) = '" + f.mangaId.toLowerCase() + "' OR lower(mangaSimpleName) = '" + f.mangaSimpleName.toLowerCase()
                                        + "') AND chapterId = '" + f.progressChapterId + "'") > 0)
                                    f.resumeFromLibrary = true;
                            }
                        }

                        mListview.post(new Runnable()
                        {

                            @Override
                            public void run()
                            {
                                mAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                    catch (Exception e)
                    {
                        // TODO: handle exception
                    }
                    finally
                    {
                        database.close();
                    }
                }
            };
            Thread t = new Thread(r);
            if (mFavorites.length > 0)
                t.start();

            mAdapter = new FavoritesAdapter(this);
            mListview.setAdapter(mAdapter);
            mListview.setOnItemClickListener(new OnItemClickListener()
            {
                @Override
                public void onItemClick(AdapterView<?> parent, View v, final int position, long id)
                {
                    openFavorite(position);
                }
            });
            mListview.setSelection(scroll);
            if (mFavorites.length > 50)
                mListview.setFastScrollEnabled(true);

            mEmptyView.setVisibility(View.GONE);
            if (mFavorites.length == 0)
            {
                mEmptyView.setVisibility(View.VISIBLE);
                if (size == 0)
                    mEmptyView.setText("You have no favorites. What are you waiting for?\n\n(To add a favorite, tap the star icon next to a manga.)");
                else
                    mEmptyView.setText("No favorites to display!\n\nPress Menu >> Filter to change the display filter, or press Menu >> Search to change the search criteria.");
            }

            if (size == 0)
            {
                File file = new File(Mango.getDataDirectory() + "/Mango/user/");
                FileFilter filter = new FileFilter()
                {
                    @Override
                    public boolean accept(File file)
                    {
                        return file.getAbsolutePath().endsWith("ser") || file.getAbsolutePath().endsWith("json");
                    }
                };
                File[] files = file.listFiles(filter);
                if (files != null && files.length != 0)
                {
                    AlertDialog alert = new AlertDialog.Builder(FavoritesActivity.this).create();
                    alert.setTitle("Restore Favorites Backup?");
                    alert.setMessage("Your Favorites List seems to be empty, but there is a backup file available.  Would you like to restore from the backup?");
                    alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yes, restore", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            popupRestoreFavorites();
                        }
                    });
                    alert.setButton(DialogInterface.BUTTON_NEGATIVE, "Nah", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            Mango.alert("Okay.  You can restore your favorites at any time by going to Menu >> Restore.", FavoritesActivity.this);
                        }
                    });
                    alert.show();
                }
            }
            db.close();

            try
            {
                if (Mango.getSharedPreferences().getLong("favoritesLastBackup", 0) < System.currentTimeMillis())
                {
                    if (size != 0)
                        autoBackup();
                }
            }
            catch (Exception e)
            {
                Mango.log("Autobackup crashed with a " + e.toString() + "... favorites data is probably corrupt.");
            }

            if (mFavorites.length > 0)
            {
                if (!Mango.getSharedPreferences().getBoolean("tutorial" + MangoTutorialHandler.FAVORITES + "Done", false))
                    MangoTutorialHandler.startTutorial(MangoTutorialHandler.FAVORITES, this);
            }

            if (!mEventsSent && mFavorites.length > 0)
            {
                HashMap<String, String> parameters = new HashMap<String, String>();
                boolean usingFilters = false;
                boolean usingTags = false;
                for (int i = 0; i < 7; i++)
                {
                    if (!Mango.getSharedPreferences().getBoolean("favoritesFilterTag" + i, true))
                        usingFilters = true;
                }
                String favoritesCount = "0";

                if (size <= 3)
                    favoritesCount = "1-3";
                else if (size <= 6)
                    favoritesCount = "4-6";
                else if (size <= 10)
                    favoritesCount = "7-10";
                else if (size <= 20)
                    favoritesCount = "11-20";
                else if (size <= 50)
                    favoritesCount = "21-50";
                else if (size <= 100)
                    favoritesCount = "51-100";
                else if (size <= 200)
                    favoritesCount = "101-200";
                else if (size > 200)
                    favoritesCount = "201+";

                for (int i = 0; i < mFavorites.length; i++)
                {
                    if (mFavorites[i].tagId != 0)
                        usingTags = true;
                }

                parameters.put("Fav Count", favoritesCount);
                parameters.put("Using Filter", Boolean.toString(usingFilters));
                parameters.put("Using Tags", Boolean.toString(usingTags));
                parameters.put("Sort Type", String.valueOf(Mango.getSharedPreferences().getInt("favoritesSortType", 0)));
                super.logEvent("View Favorites", parameters);
                mEventsSent = true;
            }
        }
        catch (Exception e)
        {
            Mango.alert(
                    "Mango encountered an error while loading your favorites.  If this happens repeatedly, you may need to clear your favorites by going to <b>Settings and Help >> Clear User Data</b>.<br><br><small><b>Stack Trace:</b><br>"
                            + Log.getStackTraceString(e), this);
        }
    }

    private void refreshCovers()
    {
        mCoverCache = new HashMap<String, SoftReference<Bitmap>>();
        mPendingThumbnails = new ArrayList<Integer>();
        mPendingDownloads = new ArrayList<Integer>();
        mQueued = new boolean[mFavorites.length];
        mAdapter.notifyDataSetChanged();
    }

    private Bitmap createCoverArt(Bitmap img)
    {
        try
        {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            if (mOverlay == null)
                mOverlay = BitmapFactory.decodeResource(getResources(), R.drawable.coverart_overlay);
            Bitmap thumbnail = Bitmap.createBitmap(mOverlay.getWidth(), mOverlay.getHeight(), Bitmap.Config.ARGB_8888);
            Bitmap artInset = Bitmap.createBitmap((int) (93 * metrics.density), (int) (93 * metrics.density), Bitmap.Config.RGB_565);

            float downscaleFactor = 1;
            if (img.getHeight() > img.getWidth())
                downscaleFactor = (93 * metrics.density) / img.getScaledWidth(metrics);
            else
                downscaleFactor = (93 * metrics.density) / img.getScaledHeight(metrics);

            Canvas c = new Canvas(artInset);
            c.drawBitmap(Bitmap.createScaledBitmap(img, (int) (img.getWidth() * downscaleFactor), (int) (img.getHeight() * downscaleFactor), true), 0, 0, null);

            c = new Canvas(thumbnail);
            c.drawBitmap(mOverlay, 0, 0, null);
            c.drawBitmap(artInset, 2 * metrics.density, 2 * metrics.density, null);

            mOverlay.recycle();
            artInset.recycle();
            img.recycle();
            mOverlay = null;
            artInset = null;
            img = null;
            return thumbnail;
        }
        catch (NullPointerException e)
        {
            Mango.log("createCoverArt: NullPointerException");
            return null;
        }
        catch (OutOfMemoryError e)
        {
            Mango.log("No memory to generate thumbnails.");
            return null;
        }
    }

    private void setTag(Favorite f, int tagId)
    {
        MangoSqlite db = new MangoSqlite(this);
        db.open();
        f.tagId = tagId;
        db.updateFavorite(f);
        db.close();
        initializeFavorites(true);
    }

    private void openFavorite(final int index)
    {
        if (mTapMode == 1)
        {
            mMenuPosition = index;
            mMenuType = 1;
            showMenu();
            return;
        }

        if (mTapMode == 2)
        {
            mFavorites[index].notificationsEnabled = !mFavorites[index].notificationsEnabled;
            MangoSqlite db = new MangoSqlite(this);
            db.open();
            db.updateFavorite(mFavorites[index]);
            db.close();
            mAdapter.notifyDataSetChanged();
            return;
        }

        final Favorite f = mFavorites[index];

        if (mFavorites[index].newChapterAvailable)
        {
            MangoSqlite db = new MangoSqlite(FavoritesActivity.this);
            db.open();
            mFavorites[index].newChapterAvailable = false;
            db.updateFavorite(mFavorites[index]);
            db.close();
        }

        if (f.resumeFromLibrary)
        {
            AlertDialog alert = new AlertDialog.Builder(FavoritesActivity.this).create();
            alert.setTitle("Read Offline?");
            alert.setMessage("Would you like resume reading " + f.mangaTitle + " " + f.progressChapterId + " from My Library?");
            alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yes, read offline", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    MangoSqlite db = new MangoSqlite(FavoritesActivity.this);
                    Manga m = new Manga();
                    m.id = f.mangaId;
                    m.title = f.mangaTitle;
                    db.open();
                    LibraryChapter[] l = db.getLibraryChaptersForManga(m);
                    for (int i = 0; i < l.length; i++)
                    {
                        if (l[i].chapter.id.equals(f.progressChapterId))
                        {
                            Intent prIntent = new Intent();
                            prIntent.setClass(Mango.CONTEXT, OfflinePagereaderActivity.class);
                            prIntent.putExtra("lcSerializable", l[i]);
                            prIntent.putExtra("initialpage", f.progressPageIndex);
                            prIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(prIntent);
                            overridePendingTransition(R.anim.fadein, R.anim.expandout);
                            break;
                        }
                    }
                }
            });
            alert.setButton(DialogInterface.BUTTON_NEGATIVE, "No, read online", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    Mango.getSharedPreferences().edit().putInt("mangaSite", f.siteId).commit();
                    showDialog(0);
                    f.buildManga(FavoritesActivity.this);
                    return;
                }
            });
            alert.show();
            return;
        }

        Mango.getSharedPreferences().edit().putInt("mangaSite", f.siteId).commit();

        if (f.progressChapterId != null)
        {
            showDialog(0);
            f.buildManga(this);
            return;
        }

        openFavoriteChapters(index, false);
    }

    private void openFavoriteChapters(final int index, boolean forceOpen)
    {
        final Favorite f = mFavorites[index];

        if (f.savedInLibrary && !forceOpen)
        {
            AlertDialog alert = new AlertDialog.Builder(FavoritesActivity.this).create();
            alert.setTitle("Browse Offline Chapters?");
            alert.setMessage("You have some chapters for " + f.mangaTitle + " saved in My Library. Would you rather browse those chapters online or browse your offline collection?");
            alert.setButton(DialogInterface.BUTTON_NEGATIVE, "Browse online", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    openFavoriteChapters(index, true);
                }
            });
            alert.setButton(DialogInterface.BUTTON_POSITIVE, "Browse offline", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    Intent chaptersIntent = new Intent();
                    chaptersIntent.setClassName(Mango.CONTEXT, LibraryBrowserActivity.class.getName());
                    Manga argManga = new Manga();
                    argManga.id = f.mangaId;
                    argManga.title = f.mangaTitle;
                    argManga.generateSimpleName(null);
                    chaptersIntent.putExtra("manga", argManga);
                    startActivity(chaptersIntent);
                }
            });
            alert.show();
            return;
        }

        Mango.getSharedPreferences().edit().putInt("mangaSite", f.siteId).commit();

        Intent chaptersIntent = new Intent();
        chaptersIntent.setClass(Mango.CONTEXT, ChaptersActivity.class);
        Manga argManga = new Manga();
        argManga.bookmarked = true;
        argManga.id = f.mangaId;
        argManga.title = f.mangaTitle;
        argManga.favoriteRowId = f.rowId;
        argManga.coverart = f.coverArtUrl;
        argManga.simpleName = f.mangaSimpleName;
        chaptersIntent.putExtra("manga", argManga);
        startActivity(chaptersIntent);
    }

    private void deleteFavorite(final int index)
    {
        MangoSqlite db = new MangoSqlite(this);
        db.open();
        db.deleteFavorite(mFavorites[index].rowId);
        db.close();
        initializeFavorites(true);
    }

    public void pendingItemFailed(MangoHttpResponse data)
    {
        Mango.DIALOG_DOWNLOADING.dismiss();
        Mango.alert(data.toString(), this);
    }

    public void popupBackupFavorites()
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Backup Favorites");
        alert.setMessage("Mango can backup your Favorites to the SD card, allowing you to restore them later in case you re-install the app or get a new phone.\n\nWhat would you like this backup to be named?");
        final EditText input = new EditText(this);
        alert.setView(input);
        input.setText("MyFavorites");
        input.selectAll();
        alert.setPositiveButton("Okay", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                String value = input.getText().toString();
                if (value.length() > 20)
                    value = value.substring(0, 20);
                final File f = new File(Mango.getDataDirectory() + "/Mango/user/" + value + ".json");
                if (f.exists())
                {
                    AlertDialog.Builder a = new AlertDialog.Builder(FavoritesActivity.this);
                    a.setTitle("Overwrite Backup?");
                    a.setMessage("A backup file with this name already exists.  Would you like to overwrite it?");
                    a.setPositiveButton("Yes, overwrite", new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int whichButton)
                        {
                            backupFavorites(f.getAbsolutePath());
                        }
                    });
                    a.setNegativeButton("No, cancel", new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int whichButton)
                        {}
                    });
                    a.show();
                }
                else
                    backupFavorites(f.getAbsolutePath());
            }
        });
        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {}
        });
        alert.show();
    }

    public void popupRestoreFavorites()
    {
        FileFilter filter = new FileFilter()
        {
            @Override
            public boolean accept(File file)
            {
                return file.getAbsolutePath().endsWith("ser") || file.getAbsolutePath().endsWith("json");
            }
        };
        File f = new File(Mango.getDataDirectory() + "/Mango/user/");
        File[] files = f.listFiles(filter);
        if (files == null || files.length == 0)
        {
            Mango.alert("There are no backup files located in the " + Mango.getDataDirectory() + "/Mango/user/ folder.", FavoritesActivity.this);
            return;
        }

        MangoFileSelector fs = new MangoFileSelector(this);
        fs.setTitle("Select File to Restore");
        fs.allowFolders = false;
        fs.title = "Select File to Restore";
        fs.setListener(new MangoFileSelector.FileSelectorListener()
        {
            public void onClick(final String path)
            {
                AlertDialog alert = new AlertDialog.Builder(FavoritesActivity.this).create();
                alert.setTitle("Restore Favorites");
                alert.setMessage(Html.fromHtml("Mango will now load the Favorites from " + new File(path).getName() + " into your Favorites List.<br><br><b>This will delete any existing Favorites and replace them with the ones in the backup file.</b><br><br>Is this alright?"));
                alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yes, restore!", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        restoreFavorites(path);
                    }
                });
                alert.setButton(DialogInterface.BUTTON_NEGATIVE, "No, never mind", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {

                    }
                });
                alert.show();
            }
        });
        fs.showSelector(Mango.getDataDirectory() + "/Mango/user/", filter);
    }

    public void backupFavorites(String path)
    {
        writeGson(path, false);
    }

    public void restoreFavorites(String path)
    {
        if (path.endsWith("json"))
            readGson(path);
        else
            readSerializedObject(path);
    }

    public void readSerializedObject(String path)
    {
        File file;
        ObjectInputStream in = null;
        try
        {
            file = new File(path);
            if (!file.exists())
            {
                Mango.alert("File does not exist (" + file.getAbsolutePath() + ")", FavoritesActivity.this);
                return;
            }


            long time = System.currentTimeMillis();

            in = new ObjectInputStream(new FileInputStream(file));
            Favorite[] f = (Favorite[]) in.readObject();
            in.close();

            MangoSqlite db = new MangoSqlite(this);
            db.open();
            db.db.execSQL("DELETE FROM tFavorites");
            for (int i = 0; i < f.length; i++)
            {
                db.insertFavorite(f[i]);
            }
            db.close();
            initializeFavorites(true);
            Mango.alert("Mango successfully finished restoring " + f.length + " Favorites from " + file.getName() + ".", this);
        }
        catch (Exception e)
        {
            Mango.alert("The backup file isn't valid.  Please send it to mango@leetsoft.net.<br><br>(" + e.getClass().getSimpleName() + " using file " + path + ")", this);
        }
        finally
        {
            try
            {
                if (in != null)
                    in.close();
                in = null;
            }
            catch (IOException e)
            {

            }
        }
    }

    public void readGson(String path)
    {
        File file;
        try
        {
            file = new File(path);
            if (!file.exists())
            {
                Mango.alert("File does not exist (" + file.getAbsolutePath() + ")", FavoritesActivity.this);
                return;
            }


            long time = System.currentTimeMillis();

            Gson g = new Gson();

            Favorite[] f = (Favorite[]) g.fromJson(new InputStreamReader(new FileInputStream(file)), Favorite[].class);

            MangoSqlite db = new MangoSqlite(this);
            db.open();
            db.db.execSQL("DELETE FROM tFavorites");
            for (int i = 0; i < f.length; i++)
            {
                db.insertFavorite(f[i]);
            }
            db.close();
            Mango.alert("Mango successfully finished restoring " + f.length + " Favorites from " + file.getName() + ".", this);
            initializeFavorites(true);
        }
        catch (Exception e)
        {
            Mango.alert("The backup file isn't valid.  Please send it to mango@leetsoft.net.<br><br>(" + e.getClass().getSimpleName() + " using file " + path + ")", this);
        }
    }

    public void writeGson(String path, boolean silent)
    {
        String state = Environment.getExternalStorageState();
        if (!state.startsWith(Environment.MEDIA_MOUNTED))
        {
            if (!silent)
                Mango.alert("Mango wasn't able to access the SD card.", FavoritesActivity.this);
            return;
        }

        File file = new File(path).getParentFile();
        Gson gson = new Gson();
        BufferedWriter out = null;

        try
        {
            file.mkdirs();
            file = new File(path);
            if (file.exists())
                file.delete();
            file.createNewFile();

            MangoSqlite db = new MangoSqlite(this);
            db.open();
            Favorite[] f = db.getAllFavorites(null);
            db.close();

            long time = System.currentTimeMillis();
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            out.write(gson.toJson(f));
            out.flush();
            if (!silent)
                Mango.alert("Your Favorites have been successfully backed up to the following location:\n\n" + file.getAbsolutePath(), this);
        }
        catch (IOException ioe)
        {
        }
        finally
        {
            try
            {
                if (out != null)
                    out.close();
                out = null;
            }
            catch (IOException e)
            {

            }
        }
    }

    public void autoBackup()
    {
        String state = Environment.getExternalStorageState();
        if (!state.startsWith(Environment.MEDIA_MOUNTED))
        {
            Mango.log("Could not backup favorites because SD card isn't mounted!");
            Mango.getSharedPreferences().edit().putLong("favoritesLastBackup", System.currentTimeMillis() + (1000 * 60 * 60)).commit();
            return;
        }

        MangoSqlite db = new MangoSqlite(this);
        db.open();
        Favorite[] f = db.getAllFavorites(null);
        db.close();
        if (f.length == 0)
        {
            Mango.log("Not making autobackup because favorites is empty");
            return;
        }

        File file = new File(Mango.getDataDirectory() + "/Mango/user/FavoritesAutoBackupOld.json");
        if (file.exists())
            file.delete();

        file = new File(Mango.getDataDirectory() + "/Mango/user/FavoritesAutoBackup.json");
        file.renameTo(new File(Mango.getDataDirectory() + "/Mango/user/FavoritesAutoBackupOld.json"));

        writeGson(Mango.getDataDirectory() + "/Mango/user/FavoritesAutoBackupOld.json", true);

        Mango.getSharedPreferences().edit().putLong("favoritesLastBackup", System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 3)).commit();
    }

    public void loadPendingFavorite(Favorite favorite)
    {
        try
        {
            Mango.DIALOG_DOWNLOADING.dismiss();
            dismissDialog(0);
        }
        catch (Exception e)
        {
        }
        Intent prIntent = new Intent();
        prIntent.setClass(Mango.CONTEXT, PagereaderActivity.class);
        prIntent.putExtra("manga", favorite.mangaObject);
        prIntent.putExtra("chapterid", favorite.progressChapterId);
        prIntent.putExtra("initialpage", favorite.progressPageIndex);
        prIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(prIntent);
        overridePendingTransition(R.anim.fadein, R.anim.expandout);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (mFilterOverlay.getVisibility() == View.VISIBLE)
            {
                toggleSearch();
                return true;
            }
            if (mSearchString != null)
            {
                mSearchString = null;
                mFilterEdit.setText("");
                initializeFavorites(true);
                return true;
            }
            if (mTapMode == 1)
            {
                mTapMode = 0;
                Toast.makeText(this, "You're no longer in Tag Mode.", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (mTapMode == 2)
            {
                mTapMode = 0;
                Toast.makeText(this, "You're no longer in Notification Mode.", Toast.LENGTH_SHORT).show();
                if (!Mango.getSharedPreferences().getBoolean("notifierEnabled", false))
                    Mango.alert("Remember to go to Settings and Help >> Notification Preferences to enable notifications!", "Don't forget!", FavoritesActivity.this);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onSearchRequested()
    {
        toggleSearch();
        return true;
    }

    private void searchTextChanged()
    {
        if (mSearchString != null && mSearchString.equals(mFilterEdit.getText().toString()))
            mFilterButton.setImageResource(R.drawable.toolbar_delete);
        else
            mFilterButton.setImageResource(R.drawable.toolbar_find);
    }

    private void searchClicked()
    {
        if (mFilterEdit.getText().toString().trim().length() == 0)
            return;

        if (mSearchString == null || !mSearchString.equals(mFilterEdit.getText().toString()))
        {
            mSearchString = mFilterEdit.getText().toString();
            mFilterButton.setImageResource(R.drawable.toolbar_delete);
        }
        else if (mSearchString.equals(mFilterEdit.getText().toString()))
        {
            mSearchString = null;
            mFilterEdit.setText("");
        }

        toggleSearch();
        initializeFavorites(true);
    }

    public void toggleSearch()
    {
        mFilterOverlay.bringToFront();
        if (mFilterOverlay.getVisibility() == View.VISIBLE)
        {
            mFilterOverlay.clearAnimation();
            AnimationSet as = (AnimationSet) AnimationUtils.loadAnimation(this, R.anim.titlebarout);
            as.scaleCurrentDuration(0.75f);
            mFilterOverlay.startAnimation(as);
            mFilterOverlay.setVisibility(View.INVISIBLE);
            InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            mgr.hideSoftInputFromWindow(mFilterEdit.getWindowToken(), 0);
        }
        else
        {
            mFilterOverlay.clearAnimation();
            AnimationSet as = (AnimationSet) AnimationUtils.loadAnimation(this, R.anim.titlebarin);
            as.scaleCurrentDuration(0.75f);
            mFilterOverlay.startAnimation(as);
            mFilterOverlay.setVisibility(View.VISIBLE);
            mFilterEdit.requestFocus();
            InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            mgr.showSoftInput(mFilterEdit, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public View getTutorialHighlightView(int index)
    {
        if (index == -1)
            return mFilterOverlay;
        return mListview.getChildAt(index);
    }

    private class CoverArtLoader extends AsyncTask<Void, Void, Void>
    {
        FavoritesActivity activity = null;

        public CoverArtLoader(FavoritesActivity activity)
        {
            attach(activity);
        }

        @Override
        protected void onProgressUpdate(Void... values)
        {
            activity.mAdapter.notifyDataSetChanged();
            super.onProgressUpdate(values);
        }

        @Override
        protected Void doInBackground(Void... params)
        {
            while (mPendingThumbnails.size() > 0)
            {
                int index = mPendingThumbnails.get(0).intValue();
                mPendingThumbnails.remove(0);
                Bitmap b = activity.mNoCoverArt;
                if (MangoCache.checkCacheForImage("cover/", mFavorites[index].coverArtUrl))
                {
                    b = createCoverArt(MangoCache.readBitmapFromCache("cover/", mFavorites[index].coverArtUrl, 1));
                }
                else if (mFavorites[index].coverArtUrl.startsWith("file@"))
                    b = createCoverArt(MangoCache.readCustomCoverArt(mFavorites[index].coverArtUrl, 1));
                else if (mFavorites[index].coverArtUrl.length() > 5)
                    downloadCoverArt(index);

                activity.mCoverCache.put(mFavorites[index].coverArtUrl, new SoftReference<Bitmap>(b));

                if (index >= mListview.getFirstVisiblePosition() && index <= mListview.getLastVisiblePosition())
                    this.publishProgress((Void[]) null);
                mQueued[index] = false;
            }
            mLoader = null;
            return null;
        }

        void downloadCoverArt(int index)
        {
            boolean alreadyQueued = false;
            synchronized (mPendingDownloads)
            {
                for (int i = 0; i < mPendingDownloads.size(); i++)
                {
                    if (mPendingDownloads.get(i).intValue() == index)
                        alreadyQueued = true;
                }
            }

            if (!alreadyQueued)
            {
                Mango.log("Adding cover art to download queue: " + mFavorites[index].mangaTitle);
                mPendingDownloads.add(new Integer(index));
            }

            if (mDownloader == null)
            {
                mDownloader = new CoverArtDownloader(FavoritesActivity.this);
                mListview.postDelayed(new Runnable()
                {

                    @Override
                    public void run()
                    {
                        mDownloader.execute((Void[]) null);
                    }
                }, 200);
            }
        }

        void detach()
        {
            activity = null;
        }

        void attach(FavoritesActivity activity)
        {
            this.activity = activity;
        }
    }

    private class CoverArtDownloader extends AsyncTask<Void, Void, Void>
    {
        FavoritesActivity activity = null;

        public CoverArtDownloader(FavoritesActivity activity)
        {
            attach(activity);
        }

        @Override
        protected void onProgressUpdate(Void... values)
        {
            activity.mAdapter.notifyDataSetChanged();
            super.onProgressUpdate(values);
        }

        @Override
        protected Void doInBackground(Void... params)
        {
            Mango.log("downloader: starting.");

            synchronized (mPendingDownloads)
            {
                try
                {
                    while (mPendingDownloads.size() > 0)
                    {
                        int index = mPendingDownloads.get(0).intValue();
                        MangoHttpResponse ret = MangoHttp.downloadData(mFavorites[index].coverArtUrl, activity);
                        if (!ret.exception)
                        {
                            ret.writeEncodedImageToCache(0, "cover/", mFavorites[index].coverArtUrl);
                            mCoverCache.remove(mFavorites[index].coverArtUrl);
                        }

                        mPendingDownloads.remove(0);
                        this.publishProgress((Void[]) null);
                    }
                }
                catch (Exception ex)
                {
                    Mango.log("downloader: " + ex.toString());
                }
            }

            mDownloader = null;
            return null;
        }

        void detach()
        {
            activity = null;
        }

        void attach(FavoritesActivity activity)
        {
            this.activity = activity;
        }
    }

    class ViewHolder
    {
        ImageView coverArt;
        TextView title;
        TextView progress;
        TextView update;
        TextView site;
        ImageView icon1;
        ImageView icon2;
        ImageView icon3;
        RelativeLayout layout;
    }

    class FavoritesAdapter extends ArrayAdapter<Favorite>
    {
        LayoutInflater mInflater = null;

        public FavoritesAdapter(MangoActivity context)
        {
            super(context, R.layout.favoriteslistrow, mFavorites);
            mInflater = context.getLayoutInflater();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent)
        {
            ViewHolder holder;
            if (convertView == null)
            {
                convertView = mInflater.inflate(R.layout.favoriteslistrow, null);
                holder = new ViewHolder();
                holder.layout = (RelativeLayout) convertView.findViewById(R.id.favoritesRowLayout);
                holder.coverArt = (ImageView) convertView.findViewById(R.id.favoritesRowThumb);
                holder.icon1 = (ImageView) convertView.findViewById(R.id.favoritesRowIcon1);
                holder.icon2 = (ImageView) convertView.findViewById(R.id.favoritesRowIcon2);
                holder.icon3 = (ImageView) convertView.findViewById(R.id.favoritesRowIconTag);
                holder.progress = (TextView) convertView.findViewById(R.id.favoritesRowProgress);
                holder.title = (TextView) convertView.findViewById(R.id.favoritesRowTitle);
                holder.update = (TextView) convertView.findViewById(R.id.favoritesRowUpdate);
                holder.site = (TextView) convertView.findViewById(R.id.favoritesRowSite);
                if (mDisableCovers)
                {
                    holder.coverArt.setVisibility(View.INVISIBLE);
                    holder.coverArt.setLayoutParams(new android.widget.RelativeLayout.LayoutParams(1, 1));
                }
                convertView.setTag(holder);
            }
            else
            {
                holder = (ViewHolder) convertView.getTag();
                holder.coverArt.setImageResource(R.drawable.coverart_overlay);
            }

            if (!mDisableCovers)
            {
                if (!mCoverCache.containsKey(mFavorites[position].coverArtUrl))
                {
                    if (!mQueued[position])
                    {
                        mPendingThumbnails.add(0, new Integer(position));
                        int limit = (mListview.getLastVisiblePosition() - mListview.getFirstVisiblePosition()) + 3;
                        if (mPendingThumbnails.size() > limit)
                        {
                            mQueued[mPendingThumbnails.get(mPendingThumbnails.size() - 1).intValue()] = false;
                            mPendingThumbnails.remove(mPendingThumbnails.size() - 1);
                        }
                        mQueued[position] = true;
                    }

                    if (mLoader == null)
                    {
                        if (mLoaderRunnable != null)
                            mListview.removeCallbacks(mLoaderRunnable);
                        mLoaderRunnable = new Runnable()
                        {

                            @Override
                            public void run()
                            {
                                mLoader = new CoverArtLoader(FavoritesActivity.this);
                                mLoaderRunnable = null;
                                mLoader.execute((Void[]) null);
                            }
                        };
                        mListview.postDelayed(mLoaderRunnable, 300);
                    }
                }
                else
                {
                    if (mCoverCache.get(mFavorites[position].coverArtUrl).get() == null)
                    {
                        mCoverCache.remove(mFavorites[position].coverArtUrl);
                        return getView(position, convertView, parent);
                    }
                    else
                        holder.coverArt.setImageBitmap(mCoverCache.get(mFavorites[position].coverArtUrl).get());
                }
            }

            holder.title.setSelected(true);
            holder.title.setText(mFavorites[position].mangaTitle);
            holder.site.setText(Mango.getSiteName(mFavorites[position].siteId));
            holder.icon1.setVisibility(View.GONE);
            holder.icon2.setVisibility(View.GONE);
            holder.icon3.setVisibility(View.GONE);
            holder.update.setVisibility(View.GONE);
            holder.layout.setBackgroundColor(Color.TRANSPARENT);

            // set icon visibility
            if (mFavorites[position].tagId != 0)
            {
                holder.icon3.setVisibility(View.VISIBLE);
                holder.icon3.setImageDrawable(Mango.getTagDrawable(mFavorites[position].tagId, true));
            }
            if (mFavorites[position].savedInLibrary)
            {
                holder.icon1.setVisibility(View.VISIBLE);
            }
            if (mFavorites[position].notificationsEnabled)
            {
                holder.icon2.setVisibility(View.VISIBLE);
            }

            // set progress text
            if (mFavorites[position].progressChapterId != null)
            {
                holder.progress.setText((mFavorites[position].progressChapterId.length() > 3 ? "" : "Chapter ") + mFavorites[position].progressChapterId + ", page "
                        + (mFavorites[position].progressPageIndex + 1));
            }
            else
            {
                holder.progress.setText("Not started");
            }

            // set update text
            if (mFavorites[position].lastChapterTime != 0)
            {
                holder.update.setVisibility(View.VISIBLE);
                String datestr = (String) android.text.format.DateFormat.format("MMM dd yyyy", new java.util.Date(mFavorites[position].lastChapterTime));
                if (mFavorites[position].lastChapterTime == -1)
                    holder.update.setText("Latest Chapter: " + (mFavorites[position].lastChapterId.length() > 3 ? "" : "c") + mFavorites[position].lastChapterId);
                else
                    holder.update.setText("Updated " + datestr + " (" + (mFavorites[position].lastChapterId.length() > 3 ? "" : "c") + mFavorites[position].lastChapterId + ")");
            }

            if (mFavorites[position].newChapterAvailable)
            {
                // holder.layout.setBackgroundColor(0x0d00ff00);
                holder.layout.setBackgroundColor(0x2d54aaff);
            }

            convertView.setId(position);
            return convertView;
        }
    }

    private class FavoriteComparator implements Comparator<Favorite>
    {
        int sortMode = Mango.getSharedPreferences().getInt("favoritesSortType", 0);

        @Override
        public int compare(Favorite o1, Favorite o2)
        {
            if (sortMode == 1) // last read date
            {
                long diff = (o2.readDate - o1.readDate);
                if (diff > 0)
                    return 1;
                if (diff < 0)
                    return -1;
            }
            if (sortMode == 2) // tag
            {
                if (o1.tagId != o2.tagId)
                    return o1.tagId - o2.tagId;
            }
            if (sortMode == 3) // site
            {
                if (o1.siteId != o2.siteId)
                    return o1.siteId - o2.siteId;
            }
            if (sortMode == 4) // last updated
            {
                long diff = (o2.lastChapterTime - o1.lastChapterTime);
                if (diff > 0)
                    return 1;
                if (diff < 0)
                    return -1;
            }
            return o1.mangaTitle.compareTo(o2.mangaTitle);
        }
    }
}
