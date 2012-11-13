package net.leetsoft.mangareader.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.*;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import net.leetsoft.mangareader.*;
import net.leetsoft.mangareader.ui.MangoAdWrapperView;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

public class LibraryBrowserActivity extends MangoActivity
{
    private ListView mListview;
    private TextView mEmptyView;
    private LibraryChapter[] mAllChapters;
    private LibraryChapter[] mChapters;
    private ListAdapter mAdapter;
    private LibraryLoader mLoader;
    private PageScanner mScanner;
    private int mViewMode;             // 0 = root library folder, 1 = library subfolder, 2 =
    // filesystem
    private int mContextPosition;
    private boolean[] mReadStatus;

    // Filesystem browser
    private String mFilesystemUri;
    private FilesystemChapter[] mFolders;

    private boolean mMultiSelectMode;
    private int mMultiFirstIndex = -1;
    private int mMultiSecondIndex = -1;

    private Handler mProgressHandler;

    private static final int VIEW_ROOTFOLDER = 0;
    private static final int VIEW_SUBFOLDER = 1;
    private static final int VIEW_FILESYSTEM = 2;

    private class InstanceBundle
    {
        private LibraryChapter[] allChapters;
        private LibraryChapter[] chapters;
        private FilesystemChapter[] folders;
        private LibraryLoader loader;
        private PageScanner scanner;
        private int viewMode;
        private String filesystemUri;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        this.setTitle("My Library", null);
        inflateLayoutManager(this, R.layout.mainmenu);
        mListview = (ListView) findViewById(R.id.MainMenuList);
        mEmptyView = (TextView) findViewById(R.id.mainMenuEmpty);

        super.setAdLayout((MangoAdWrapperView) findViewById(R.id.mainmenuAdLayout));
        super.setJpBackground(R.drawable.jp_bg_library);
        LayoutAnimationController lac = AnimationUtils.loadLayoutAnimation(this, R.anim.listlayoutslidein);
        mListview.setLayoutAnimation(lac);
        mProgressHandler = new Handler();

        this.registerForContextMenu(mListview);
        mListview.setOnCreateContextMenuListener(this);

        if (getLastCustomNonConfigurationInstance() != null && ((InstanceBundle) getLastCustomNonConfigurationInstance()).chapters != null)
        {
            InstanceBundle save = (InstanceBundle) getLastCustomNonConfigurationInstance();
            mAllChapters = save.allChapters;
            mChapters = save.chapters;
            mLoader = save.loader;
            mScanner = save.scanner;
            mViewMode = save.viewMode;
            mFilesystemUri = save.filesystemUri;
            mFolders = save.folders;
            if (mViewMode == VIEW_SUBFOLDER)
                setTitle("My Library", mChapters[0].manga.title);
            if (mLoader != null)
                mLoader.attach(this);
            if (mScanner != null)
                mScanner.attach(this);
            save = null;

            if (mViewMode == VIEW_FILESYSTEM)
                filesystemCallback(null);
            else
            {
                if (mChapters == null || mChapters.length == 0)
                    return;

                mAdapter = new LibraryAdapter(this);
                mListview.setAdapter(mAdapter);
                mListview.setOnItemClickListener(new OnItemClickListener()
                {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View v, final int position, long id)
                    {
                        openSeries(position);
                    }
                });
            }
            return;
        }

        Bundle arguments = getIntent().getExtras();
        if (!getIntent().hasExtra("manga"))
        {
            mViewMode = Mango.getSharedPreferences().getInt("libraryLastViewMode", VIEW_ROOTFOLDER);
            if (mViewMode == VIEW_ROOTFOLDER)
            {
                showDialog(0);
                mLoader = new LibraryLoader(this);
                mLoader.execute((Manga) null);
            }
            else
            {
                showDialog(0);
                mFilesystemUri = Mango.getSharedPreferences().getString("fileBrowserHomePath", Mango.getDataDirectory().getAbsolutePath());
                mLoader = new LibraryLoader(this);
                mLoader.execute((Manga) null);
            }
            refreshMenu();
        }
        else
        {
            showDialog(0);
            mViewMode = VIEW_SUBFOLDER;
            mLoader = new LibraryLoader(this);
            mLoader.execute((Manga) arguments.get("manga"));
        }
        super.logEvent("Browse Offline", null);
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance()
    {
        InstanceBundle save = new InstanceBundle();
        save.allChapters = mAllChapters;
        save.chapters = mChapters;
        save.loader = mLoader;
        save.scanner = mScanner;
        save.viewMode = mViewMode;
        save.filesystemUri = mFilesystemUri;
        save.folders = mFolders;
        if (mLoader != null)
            mLoader.detach();
        if (mScanner != null)
            mScanner.detach();
        mChapters = null;
        return save;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.mylibrarymenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        if (menu == null)
            return false;
        menu.clear();
        onCreateOptionsMenu(menu);
        if (mViewMode == VIEW_ROOTFOLDER || mViewMode == VIEW_FILESYSTEM)
        {
            menu.removeItem(R.id.menuLibraryMultiSelect);
            menu.removeItem(R.id.menuLibraryDownloadMore);
        }
        else
        {
            menu.removeItem(R.id.menuLibrarySwapMode);
            if (mMultiSelectMode && mMultiFirstIndex == -1)
            {
                menu.getItem(0).setTitle("Cancel");
                menu.getItem(0).setTitleCondensed("Cancel");
                menu.getItem(0).setIcon(getResources().getDrawable(R.drawable.ic_action_clear));
            }
            else if (mMultiSelectMode)
            {
                menu.getItem(0).setTitle("Delete These");
                menu.getItem(0).setTitleCondensed("Delete These");
                menu.getItem(0).setIcon(getResources().getDrawable(R.drawable.ic_action_okay));
            }
            else if (!mMultiSelectMode)
            {
                menu.getItem(0).setTitle("Multi-Select");
                menu.getItem(0).setTitleCondensed("Multi-Select");
                menu.getItem(0).setIcon(getResources().getDrawable(R.drawable.ic_action_multiselect));
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.menuLibraryMultiSelect)
        {
            if (mViewMode == VIEW_ROOTFOLDER)
            {
                Mango.alert("You must first open a folder before you can use multi-select mode.", LibraryBrowserActivity.this);
                return true;
            }
            toggleMultiselect(false);
            return true;
        }
        if (item.getItemId() == R.id.menuLibraryRestore)
        {
            promptRestore();
        }
        if (item.getItemId() == R.id.menuLibrarySwapMode)
        {
            if (mViewMode == VIEW_FILESYSTEM)
            {
                showDialog(0);
                mViewMode = VIEW_ROOTFOLDER;
                mLoader = new LibraryLoader(this);
                mLoader.execute((Manga) null);
            }
            else
            {
                showDialog(0);
                mViewMode = VIEW_FILESYSTEM;
                mFilesystemUri = Mango.getSharedPreferences().getString("fileBrowserHomePath", Mango.getDataDirectory().getAbsolutePath());
                mLoader = new LibraryLoader(this);
                mLoader.execute((Manga) null);
            }
        }
        if (item.getItemId() == R.id.menuLibraryScan)
        {
            promptScanner();
            return true;
        }
        if (item.getItemId() == R.id.menuLibraryDownloadMore)
        {
            if (mChapters != null && mChapters.length > 0)
            {
                Mango.getSharedPreferences().edit().putInt("mangaSite", mChapters[0].siteId).commit();

                Intent chaptersIntent = new Intent();
                chaptersIntent.setClass(Mango.CONTEXT, ChaptersActivity.class);
                Manga argManga = new Manga();
                argManga.bookmarked = false;
                argManga.id = mChapters[0].manga.id;
                argManga.title = mChapters[0].manga.title;
                argManga.simpleName = mChapters[0].manga.simpleName;
                chaptersIntent.putExtra("manga", argManga);
                startActivity(chaptersIntent);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
    {
        try
        {
            super.onCreateContextMenu(menu, v, menuInfo);
            AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
            if (mViewMode == VIEW_FILESYSTEM)
                menu.setHeaderTitle(mFolders[info.position].fileObj.getName());
            else
                menu.setHeaderTitle(generateDisplayText(info.position));
            mContextPosition = info.position;
            if (mViewMode == VIEW_ROOTFOLDER)
            {
                menu.add(Menu.NONE, 0, 0, "Open Folder");
                menu.add(Menu.NONE, 1, 1, "Delete Folder");
            }
            else if (mViewMode == VIEW_FILESYSTEM)
            {
                menu.add(Menu.NONE, 0, 0, "Open Folder");
            }
            else
            {
                menu.add(Menu.NONE, 0, 0, "Open Chapter");
                menu.add(Menu.NONE, 1, 1, "Delete Chapter");
                menu.add(Menu.NONE, 2, 2, "Mark " + (mReadStatus[info.position] ? "Unread" : "Read"));
                menu.add(Menu.NONE, 3, 3, "Set this and previous as unread");
                menu.add(Menu.NONE, 4, 4, "Set this and previous as read");
                menu.add(Menu.NONE, 5, 5, "Properties");
            }
        }
        catch (Exception e)
        {
            Mango.log(e.toString());
        }
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item)
    {
        Mango.log("Context item selected. " + item.getItemId());
        switch (item.getItemId())
        {
            case 0:
                if (mViewMode == VIEW_FILESYSTEM)
                    openFilesystemFolder(mContextPosition);
                else
                    openSeries(mContextPosition);
                return true;
            case 1:
                if (mViewMode == VIEW_ROOTFOLDER)
                    promptDeleteFolder(mContextPosition);
                else
                    promptDeleteItem(mContextPosition);
                return true;
            case 2:
                changeReadStatus(mChapters[mContextPosition], !mReadStatus[mContextPosition]);
                return true;
            case 3:
                setAllAsUnread(mChapters[mContextPosition]);
                return true;
            case 4:
                setAllAsRead(mChapters[mContextPosition]);
                return true;
            case 5:
                if (mViewMode == VIEW_SUBFOLDER)
                    showProperties(mContextPosition);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void setAllAsRead(LibraryChapter startChapter)
    {
        MangoSqlite db = new MangoSqlite(this);
        db.open();

        int index = mContextPosition;

        for (int k = index; k >= 0; k--)
        {
            if (!mReadStatus[k])
                db.insertRecentBookmark(mChapters[k].manga.id, mChapters[k].manga.title, mChapters[k].chapterIndex, mChapters[k].chapter.title, mChapters[k].chapter.id, mChapters[k].chapterCount,
                        Mango.SITE_LOCAL, true);
        }
        db.close();
        initializeReadTags();
    }

    private void setAllAsUnread(LibraryChapter startChapter)
    {
        MangoSqlite db = new MangoSqlite(this);
        long deleteRowId = -1;
        db.open();
        Bookmark[] bmarray = db.getAllHistoryArray(MangoSqlite.KEY_UPDATETIME + " ASC", MangoSqlite.KEY_MANGAID + " = '" + startChapter.manga.id + "'", true);

        int index = mContextPosition;

        for (int k = index; k >= 0; k--)
        {
            if (mReadStatus[k])
            {
                for (int i = 0; i < bmarray.length; i++)
                {
                    Bookmark bm = bmarray[i];
                    if (bm.bookmarkType == Bookmark.RECENT && bm.mangaId.equals(mChapters[k].manga.id) && bm.chapterId.equals(mChapters[k].chapter.id))
                    {
                        deleteRowId = bm.rowId;
                        break;
                    }
                }
                if (deleteRowId != -1)
                {
                    db.deleteBookmark(deleteRowId);
                }
            }
        }
        db.close();
        initializeReadTags();
    }

    private void changeReadStatus(LibraryChapter chapter, boolean read)
    {
        MangoSqlite db = new MangoSqlite(this);
        db.open();

        if (mReadStatus[mContextPosition])
        {
            Bookmark[] bmarray = db.getAllHistoryArray(MangoSqlite.KEY_UPDATETIME + " ASC", MangoSqlite.KEY_MANGAID + " = '" + chapter.manga.id + "'", true);
            long deleteRowId = -1;
            for (int i = 0; i < bmarray.length; i++)
            {
                Bookmark bm = bmarray[i];
                if (bm.bookmarkType == Bookmark.RECENT && bm.mangaId.equals(chapter.manga.id) && bm.chapterId.equals(chapter.chapter.id))
                {
                    deleteRowId = bm.rowId;
                    break;
                }
            }
            if (deleteRowId != -1)
            {
                db.deleteBookmark(deleteRowId);
            }
        }
        else
        {
            db.insertRecentBookmark(chapter.manga.id, chapter.manga.title, chapter.chapterIndex, chapter.chapter.title, chapter.chapter.id, chapter.chapterCount, Mango.SITE_LOCAL, true);
        }

        db.close();

        initializeReadTags();
    }

    private void showProperties(int mContextPosition2)
    {
        LibraryChapter c = mChapters[mContextPosition2];
        Mango.alert("Chapter Title:\n\t" + c.chapter.title + "\nChapter Index:\n\t" + c.chapterIndex + "\nChapter ID:\n\t" + c.chapter.id + "\nSource:\n\t" + Mango.getSiteName(c.siteId)
                + "\nFile Path:\n\t" + c.path, "Chapter Properties", LibraryBrowserActivity.this);
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        if (Mango.DIALOG_DOWNLOADING != null)
        {
            Mango.DIALOG_DOWNLOADING.dismiss();
            removeDialog(0);
        }
        if (id == 0)
        {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle("Now Loading!");
            dialog.setMessage("This'll just take a sec.");
            dialog.setIndeterminate(true);
            dialog.setCancelable(true);
            Mango.DIALOG_DOWNLOADING = dialog;
            return dialog;
        }
        else if (id == 2)
        {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle("Scanning pages...");
            dialog.setMessage("Starting scanner...");
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            Mango.DIALOG_DOWNLOADING = dialog;
            return dialog;
        }
        return super.onCreateDialog(id);
    }

    private void toggleMultiselect(boolean cancel)
    {
        if (mMultiSelectMode && (cancel || mMultiFirstIndex == -1))
        {
            mMultiSelectMode = false;
            ((BaseAdapter) mAdapter).notifyDataSetChanged();
            Toast.makeText(LibraryBrowserActivity.this, "Multi-select mode cancelled.", Toast.LENGTH_SHORT).show();
        }
        else if (!mMultiSelectMode)
        {
            if (!Mango.getSharedPreferences().getBoolean("multiSelectPopup", false))
            {
                Mango.alert(
                        "Multi-select mode allows you to quickly delete a bunch of chapters.\n\nTap the first chapter you wish to remove, then the last.  Finally, tap the check button.  Press Back to cancel.",
                        "Alert", LibraryBrowserActivity.this);
                Mango.getSharedPreferences().edit().putBoolean("multiSelectPopup", true).commit();
            }
            else
                Toast.makeText(LibraryBrowserActivity.this, "Multi-select mode activated.", Toast.LENGTH_SHORT).show();
            mMultiSelectMode = true;
            mMultiSecondIndex = -1;
            mMultiFirstIndex = -1;
        }
        else
        {
            int smaller = 0;
            int bigger = 0;
            int chapterCount = 0;

            if (mMultiFirstIndex > mMultiSecondIndex)
            {
                smaller = mMultiSecondIndex;
                bigger = mMultiFirstIndex;
                mMultiFirstIndex = smaller;
                mMultiSecondIndex = bigger;
            }
            else
            {
                smaller = mMultiFirstIndex;
                bigger = mMultiSecondIndex;
            }

            for (int i = smaller; i <= bigger; i++)
            {
                chapterCount++;
            }

            if (chapterCount == 0)
                return;
            mMultiSelectMode = false;
            ((BaseAdapter) mAdapter).notifyDataSetChanged();

            AlertDialog alert = new AlertDialog.Builder(LibraryBrowserActivity.this).create();
            alert.setTitle("Remove Chapters");
            alert.setMessage("Are you sure you want to remove " + chapterCount + " chapters?");
            alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yes", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    promptDeleteRange();
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
        refreshMenu();
    }

    private void updateProgressDialog(final String text)
    {
        mProgressHandler.post(new Runnable()
        {

            @Override
            public void run()
            {
                Mango.DIALOG_DOWNLOADING.setMessage(text);
            }
        });

    }

    private Object initializeLibrary()
    {
        mViewMode = VIEW_ROOTFOLDER;

        if (mAllChapters != null)
        {
            updateProgressDialog("Restoring...");
            mChapters = mAllChapters;
            mAllChapters = null;
            return null;
        }

        mChapters = new LibraryChapter[0];
        ArrayList<LibraryChapter> filteredArrayList = new ArrayList<LibraryChapter>();
        ArrayList<LibraryChapter> chaptersArrayList = new ArrayList<LibraryChapter>();
        MangoSqlite db = new MangoSqlite(this);
        try
        {
            db.open();
            updateProgressDialog("Querying...");
            String[] chapters = db.getLibraryChapterIndex();
            updateProgressDialog("Processing 0 of " + chapters.length + "...");
            for (int i = 0; i < chapters.length; i++)
            {
                LibraryChapter newLc = new LibraryChapter();
                newLc.chapterCount = 1;
                newLc.manga = new Manga();
                newLc.manga.title = chapters[i];

                boolean dontAdd = false;
                for (int j = 0; j < filteredArrayList.size(); j++)
                {
                    if (filteredArrayList.size() > 0 && filteredArrayList.get(j).manga.title.equals(newLc.manga.title))
                        dontAdd = true;
                }

                for (int k = 0; k < chaptersArrayList.size(); k++)
                {
                    if (chaptersArrayList.get(k).manga.title.equals(newLc.manga.title))
                        chaptersArrayList.get(k).chapterCount++;
                }

                filteredArrayList.add(newLc);

                if (!dontAdd)
                    chaptersArrayList.add(newLc);

                updateProgressDialog("Processing " + (i + 1) + " of " + chapters.length + "...");
            }
        }
        catch (SQLException ex)
        {
            return "Mango encountered an error while retrieving your Library from the SQLite database! If this happens again, please let us know, along with the following data:\n\n"
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
        finally
        {
            if (db != null)
                db.close();
        }

        if (chaptersArrayList.size() == 0)
            return null;

        mChapters = new LibraryChapter[chaptersArrayList.size()];
        chaptersArrayList.toArray(mChapters);
        Arrays.sort(mChapters, new LibraryComparator());

        return null;
    }

    private Object initializeChapters(Manga m)
    {
        mViewMode = VIEW_SUBFOLDER;
        mAllChapters = mChapters;
        mChapters = new LibraryChapter[0];
        MangoSqlite db = new MangoSqlite(this);
        LibraryChapter[] lc = null;
        try
        {
            db.open();
            updateProgressDialog("Querying...");
            lc = db.getAllLibraryChapters(MangoSqlite.KEY_MANGATITLE + " = '" + m.title + "'");
            db.close();
        }
        catch (SQLException ex)
        {
            return "Mango encountered an error while retrieving your Library from the SQLite database! If this happens again, please let us know, along with the following data:\n\n"
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
        finally
        {
            if (db != null)
                db.close();
        }

        mChapters = lc;
        updateProgressDialog("Sorting...");
        Arrays.sort(mChapters, new LibraryComparator());
        initializeReadTags();

        return null;
    }

    private Object initializeFilesystemView(String uri)
    {
        mViewMode = VIEW_FILESYSTEM;
        mFilesystemUri = uri;
        File path = new File(uri);
        ArrayList<FilesystemChapter> folders = new ArrayList<FilesystemChapter>();

        // Add parent folder
        FilesystemChapter parentFolder = new FilesystemChapter();
        parentFolder.fileObj = path.getParentFile();
        if (parentFolder.fileObj == null)
            parentFolder.fileObj = path;
        parentFolder.isParentFolder = true;
        folders.add(parentFolder);

        try
        {
            if (!path.exists())
                throw new FileNotFoundException("The system couldn't find the file specified!");

            updateProgressDialog("Listing folder...");

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
                throw new FileNotFoundException("This folder has no subfolders to display!");

            for (int i = 0; i < files.length; i++)
            {
                updateProgressDialog("Processing " + (i + 1) + " of " + files.length + "...");
                FilesystemChapter fsf = new FilesystemChapter();
                fsf.fileObj = files[i];
                fsf.isChapter = false;
                fsf.pages = 0;
                fsf.isValidChapterFolder();
                folders.add(fsf);
            }
        }
        catch (FileNotFoundException e)
        {
            return e;
        }
        finally
        {
            mFolders = new FilesystemChapter[folders.size()];
            folders.toArray(mFolders);
            Arrays.sort(mFolders, new FolderComparator());
        }
        return null;
    }

    private void initializeReadTags()
    {
        new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                mReadStatus = new boolean[mChapters.length];
                try
                {
                    MangoSqlite db = new MangoSqlite(LibraryBrowserActivity.this);
                    db.open();
                    Bookmark[] b = db.getAllHistoryArray(MangoSqlite.KEY_UPDATETIME + " DESC", MangoSqlite.KEY_MANGAID + " = '" + mChapters[0].manga.id + "'", true);
                    db.close();
                    for (int i = 0; i < b.length; i++)
                    {
                        Bookmark bm = b[i];
                        if (bm.mangaId.equals(mChapters[0].manga.id))
                        {
                            for (int j = 0; j < mChapters.length; j++)
                            {
                                if (bm.chapterId.equals(mChapters[j].chapter.id))
                                    mReadStatus[j] = true;
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    Mango.log("Exception");
                }
                mListview.postDelayed(new Runnable()
                {

                    @Override
                    public void run()
                    {
                        ((LibraryAdapter) mAdapter).notifyDataSetChanged();
                    }
                }, 300);
            }
        }).start();
    }

    private void openFilesystemFolder(final int index)
    {
        if (mFolders[index].isChapter)
        {
            Intent prIntent = new Intent();
            prIntent.setClass(Mango.CONTEXT, OfflinePagereaderActivity.class);
            LibraryChapter lc = new LibraryChapter();
            Manga m = new Manga();
            m.id = mFolders[index].fileObj.getParent();
            // Find second to last path seperator
            int firstSlash = m.id.lastIndexOf("/");
            m.title = m.id.substring(m.id.lastIndexOf("/", firstSlash - 1));
            Chapter c = new Chapter();
            c.id = mFolders[index].fileObj.getName();
            c.title = mFolders[index].fileObj.getName();
            lc.chapterCount = 1;
            lc.chapterIndex = 1;
            lc.manga = m;
            lc.path = mFolders[index].fileObj.getAbsolutePath() + "/";
            lc.siteId = 1;
            lc.filesystemChapter = mFolders[index];
            lc.chapter = c;
            prIntent.putExtra("lcSerializable", lc);
            prIntent.putExtra("initialpage", 0);
            prIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(prIntent);
            overridePendingTransition(R.anim.fadein, R.anim.expandout);
        }
        else
        {
            showDialog(0);
            mViewMode = VIEW_FILESYSTEM;
            mFilesystemUri = mFolders[index].fileObj.getAbsolutePath();
            mLoader = new LibraryLoader(LibraryBrowserActivity.this);
            mLoader.execute((Manga) null);
            Mango.getSharedPreferences().edit().putString("fileBrowserHomePath", mFilesystemUri).commit();
        }
    }

    private void openSeries(final int index)
    {
        if (mMultiSelectMode)
        {
            refreshMenu();
            if (mMultiFirstIndex == -1)
            {
                mMultiSecondIndex = index;
                mMultiFirstIndex = index;
                ((BaseAdapter) mAdapter).notifyDataSetChanged();
                return;
            }
            mMultiSecondIndex = index;
            ((BaseAdapter) mAdapter).notifyDataSetChanged();
            return;
        }

        if (mViewMode == VIEW_ROOTFOLDER)
        {
            try
            {
                showDialog(0);
                mViewMode = VIEW_SUBFOLDER;
                mLoader = new LibraryLoader(this);
                mLoader.execute(mChapters[index].manga);
            }
            catch (ArrayIndexOutOfBoundsException e)
            {
                mViewMode = VIEW_ROOTFOLDER;
                dismissDialog(0);
                Mango.alert("Mango had a bit of a problem opening the chapter. Please try closing the My Library screen and trying again.", LibraryBrowserActivity.this);
            }
        }
        else
        {
            AlertDialog alert = new AlertDialog.Builder(LibraryBrowserActivity.this).create();
            alert.setTitle(generateDisplayText(index));
            alert.setMessage("What do you want to do with this chapter?");
            alert.setButton(DialogInterface.BUTTON_POSITIVE, "Start Reading", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    Intent prIntent = new Intent();
                    prIntent.setClass(Mango.CONTEXT, OfflinePagereaderActivity.class);
                    prIntent.putExtra("lcSerializable", mChapters[index]);
                    prIntent.putExtra("initialpage", 0);
                    prIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(prIntent);
                    overridePendingTransition(R.anim.fadein, R.anim.expandout);
                }
            });
            alert.setButton(DialogInterface.BUTTON_NEGATIVE, "Remove", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    promptDeleteItem(index);
                }
            });
            alert.show();
        }
    }

    private void rootCallback(Object retval)
    {
        Mango.DIALOG_DOWNLOADING.dismiss();
        removeDialog(0);
        if (retval != null)
        {
            Mango.alert(retval.toString(), this);
            mListview.setAdapter(new ArrayAdapter<String>(LibraryBrowserActivity.this, android.R.layout.simple_list_item_1, new String[]{"Problem loading data."}));
            return;
        }

        this.setTitle("My Library", null);

        mAdapter = new LibraryAdapter(this);
        mListview.setAdapter(mAdapter);
        mListview.startLayoutAnimation();
        mListview.setOnItemClickListener(new OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, final int position, long id)
            {
                openSeries(position);
            }
        });

        mEmptyView.setVisibility(View.GONE);
        if (mChapters.length == 0)
        {
            mEmptyView.setVisibility(View.VISIBLE);
            mEmptyView.setText("The My Library feature lets you download manga to your phone so you can read them offline later.\n\nDownload some chapters by tapping on them and clicking \"Save to My Library\"!\n\n(Press Menu >> Restore Database to force Mango to restore your Library database from the SD card)");

            File file = new File(Mango.getDataDirectory() + "/Mango/library/database.xml");
            if (file.exists())
            {

                AlertDialog alert = new AlertDialog.Builder(LibraryBrowserActivity.this).create();
                alert.setTitle("Restore My Library Database?");
                alert.setMessage("Your My Library database seems to be empty, but a backup is available.  Would you like to restore it?");
                alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yes, restore", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        promptRestore();
                    }
                });
                alert.setButton(DialogInterface.BUTTON_NEGATIVE, "Nah", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        Mango.alert("Okay.  You can restore your database backup at any time by going to Menu >> Restore.", LibraryBrowserActivity.this);
                    }
                });
                alert.show();
            }
        }

        if (mChapters.length > 30)
            mListview.setFastScrollEnabled(true);

        refreshMenu();

        Mango.getSharedPreferences().edit().putInt("libraryLastViewMode", mViewMode).commit();
    }

    private void chapterCallback(Object retval)
    {
        Mango.DIALOG_DOWNLOADING.dismiss();
        removeDialog(0);
        if (retval != null)
        {
            Mango.alert(retval.toString(), this);
            mListview.setAdapter(new ArrayAdapter<String>(LibraryBrowserActivity.this, android.R.layout.simple_list_item_1, new String[]{"Problem loading data."}));
            return;
        }

        mEmptyView.setVisibility(View.GONE);
        if (mChapters.length == 0)
        {
            mListview.setMinimumHeight(mListview.getChildAt(0).getHeight() * 2);
            mEmptyView.setVisibility(View.VISIBLE);
            mEmptyView.setText("This folder is empty.\n\nPress the Back key to return to the list of all manga.");
            return;
        }

        this.setTitle("My Library", mChapters[0].manga.title);

        mAdapter = new LibraryAdapter(this);
        mListview.setAdapter(mAdapter);
        mListview.startLayoutAnimation();
        mListview.setOnItemClickListener(new OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, final int position, long id)
            {
                openSeries(position);
            }
        });
        if (mChapters.length > 40)
            mListview.setFastScrollEnabled(true);

        refreshMenu();
    }

    private void filesystemCallback(Object retval)
    {
        Mango.DIALOG_DOWNLOADING.dismiss();
        removeDialog(0);

        if (!Mango.getSharedPreferences().getBoolean("filesystemPopup", false))
        {
            Mango.alert(
                    "You've swapped to the File Browser mode.  Using this mode, you can use Mango to read any manga on your SD card, even if it came from somewhere else (like your computer, for example).\n\nNote that some features are not supported while in this mode, such as Favorites and History tracking.",
                    LibraryBrowserActivity.this);
            Mango.getSharedPreferences().edit().putBoolean("filesystemPopup", true).commit();
        }

        mEmptyView.setVisibility(View.GONE);
        if (retval != null)
        {
            mEmptyView.setVisibility(View.VISIBLE);
            mEmptyView.setText(((Exception) retval).getMessage() + "\n\nTap 'Parent Folder' to return to the previous folder.");
        }

        this.setTitle("File Browser", mFilesystemUri.substring(mFilesystemUri.lastIndexOf('/'), mFilesystemUri.length()));

        mAdapter = new FilesystemAdapter(this);
        mListview.setAdapter(mAdapter);
        mListview.startLayoutAnimation();
        mListview.setOnItemClickListener(new OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, final int position, long id)
            {
                openFilesystemFolder(position);
            }
        });

        if (mFolders.length > 30)
            mListview.setFastScrollEnabled(true);

        refreshMenu();

        Mango.getSharedPreferences().edit().putInt("libraryLastViewMode", mViewMode).commit();
    }

    private void startScanner()
    {
        showDialog(2);
        PageScanner scanner = new PageScanner(this);
        if (mViewMode == VIEW_ROOTFOLDER)
            scanner.execute((Manga) null);
        else
            scanner.execute(mChapters[0].manga);
    }

    private void scannerCallback(String report)
    {
        Mango.DIALOG_DOWNLOADING.dismiss();
        removeDialog(2);
        Mango.DIALOG_DOWNLOADING = new ProgressDialog(this);
        if (report.length() == 0)
            report = "No problems found.";
        Mango.alert(report, "Repair Missing Pages", this);
    }

    private void promptDeleteRange()
    {
        AlertDialog alert = new AlertDialog.Builder(LibraryBrowserActivity.this).create();
        alert.setTitle("Remove Chapters from My Library");
        alert.setMessage("Do you also want to delete the image files on the SD card?\n\nPress the Back key to cancel.");
        alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yes", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                Thread t = new Thread(new Runnable()
                {

                    @Override
                    public void run()
                    {
                        try
                        {
                            mListview.post(new Runnable()
                            {

                                @Override
                                public void run()
                                {
                                    Toast.makeText(LibraryBrowserActivity.this, "Deleting selected items...", Toast.LENGTH_SHORT).show();
                                }
                            });
                            deleteRange(true);
                            mListview.post(new Runnable()
                            {

                                @Override
                                public void run()
                                {
                                    Toast.makeText(LibraryBrowserActivity.this, "Items deleted successfully!", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        catch (final Exception e)
                        {
                            mListview.post(new Runnable()
                            {

                                @Override
                                public void run()
                                {
                                    Toast.makeText(LibraryBrowserActivity.this, "Error deleting items:\n" + e.toString(), Toast.LENGTH_LONG).show();
                                }
                            });
                            Mango.log("deleteRange: " + e.toString());
                        }
                    }
                });
                t.start();
            }
        });
        alert.setButton(DialogInterface.BUTTON_NEGATIVE, "No", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                deleteRange(false);
            }
        });
        alert.show();
    }

    private void promptScanner()
    {
        AlertDialog alert = new AlertDialog.Builder(LibraryBrowserActivity.this).create();
        alert.setTitle("Repair Missing Pages");
        String cName = "your entire library";
        if (mViewMode == VIEW_SUBFOLDER)
            cName = mChapters[0].manga.title;
        alert.setMessage("This tool will scan " + cName
                + " and re-download any bad pages.\n\nMake sure you have an active 3G/4G/WiFi connection and don't close this screen while the scan is running.\n\nPress Back to cancel.");
        alert.setButton(DialogInterface.BUTTON_POSITIVE, "Start!", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                startScanner();
            }
        });
        alert.show();
    }

    private void promptRestore()
    {
        AlertDialog alert = new AlertDialog.Builder(LibraryBrowserActivity.this).create();
        alert.setTitle("Restore Database?");
        alert.setMessage("Would you like to import the My Library database from the SD card?  This will clear the current My Library database and replace it with the database saved on the SD card.");
        alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yes, start!", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                File f = new File(Mango.getDataDirectory() + "/Mango/library/database.xml");
                if (!f.exists())
                {
                    Mango.alert("Unable to restore backup because database.xml file could not be found.", LibraryBrowserActivity.this);
                    return;
                }
                new Thread(new Runnable()
                {

                    @Override
                    public void run()
                    {
                        mListview.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                Toast.makeText(LibraryBrowserActivity.this, "Processing, this may take a few minutes...", Toast.LENGTH_LONG).show();
                            }
                        });
                        final boolean retval = MangoLibraryIO.readLibraryBackup(LibraryBrowserActivity.this);
                        mListview.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                if (retval)
                                    Mango.alert("Restore process finished successfully!", LibraryBrowserActivity.this);
                                else
                                    Mango.alert("Mango could not restore the backup file! :'(\n\nContact us for more help.", LibraryBrowserActivity.this);
                                initializeLibrary();
                            }
                        });
                    }
                }).start();
            }
        });
        alert.setButton(DialogInterface.BUTTON_NEGATIVE, "Nah", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {}
        });
        alert.show();
    }

    private void promptDeleteFolder(final int index)
    {
        AlertDialog alert = new AlertDialog.Builder(LibraryBrowserActivity.this).create();
        alert.setTitle("Delete Images from SD Card?");
        alert.setMessage("Do you also want to delete the image files on the SD card?\n\nPress the Back key to cancel.");
        alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yes", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                try
                {
                    Thread t = new Thread(new Runnable()
                    {

                        @Override
                        public void run()
                        {
                            try
                            {
                                mListview.post(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        Toast.makeText(LibraryBrowserActivity.this, "Deleting folder...", Toast.LENGTH_SHORT).show();
                                    }
                                });
                                deleteFolder(mChapters[index], true);
                                mListview.post(new Runnable()
                                {

                                    @Override
                                    public void run()
                                    {
                                        Toast.makeText(LibraryBrowserActivity.this, "Folder deleted successfully.", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                            catch (final Exception e)
                            {
                                mListview.post(new Runnable()
                                {

                                    @Override
                                    public void run()
                                    {
                                        Toast.makeText(LibraryBrowserActivity.this, "Error deleting items:\n" + e.toString(), Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    });
                    t.start();
                }
                catch (ArrayIndexOutOfBoundsException e)
                {
                    Mango.alert("There was a problem deleting the folder. :'(\nPlease provide the following technical data:\n\nArray index out of bounds. mChapters.length=" + mChapters.length
                            + "\nindex=" + index + "\nmRootMode=" + mViewMode, LibraryBrowserActivity.this);
                }
            }
        });
        alert.setButton(DialogInterface.BUTTON_NEGATIVE, "No", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                try
                {
                    deleteFolder(mChapters[index], false);
                }
                catch (ArrayIndexOutOfBoundsException e)
                {
                    Mango.alert("There was a problem deleting the folder. :'(\nPlease provide the following technical data:\n\nArray index out of bounds. mChapters.length=" + mChapters.length
                            + "\nindex=" + index + "\nmRootMode=" + mViewMode, LibraryBrowserActivity.this);
                }
            }
        });
        alert.show();
    }

    private void promptDeleteItem(final int index)
    {
        AlertDialog alert = new AlertDialog.Builder(LibraryBrowserActivity.this).create();
        alert.setTitle("Remove from My Library");
        alert.setMessage("Do you also want to delete the image files on the SD card?\n\nPress the Back key to cancel.");
        alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yes", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                deleteItem(mChapters[index], true);
            }
        });
        alert.setButton(DialogInterface.BUTTON_NEGATIVE, "No", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                deleteItem(mChapters[index], false);
            }
        });
        alert.show();
    }

    private void deleteFolder(final LibraryChapter chapter, final boolean deleteFiles)
    {
        MangoSqlite db = new MangoSqlite(LibraryBrowserActivity.this);
        LibraryChapter[] lcArray;
        try
        {
            db.open();
            lcArray = db.getLibraryChaptersForManga(chapter.manga);
            for (int i = 0; i < lcArray.length; i++)
            {
                db.deleteLibraryChapter(lcArray[i].rowId);
                if (deleteFiles)
                    MangoLibraryIO.deleteFiles(lcArray[i]);
            }
            MangoLibraryIO.writeLibraryBackup(db.getAllLibraryChapters(null));
        }
        catch (SQLException e)
        {
            Mango.alert("There was a problem deleting the item from the SQLite database!\n\n" + e.getClass().getSimpleName() + ": " + e.getMessage(), LibraryBrowserActivity.this);
        }
        finally
        {
            db.close();
        }

        mViewMode = VIEW_ROOTFOLDER;
        mLoader = new LibraryLoader(this);
        mLoader.execute((Manga) null);
    }

    private void deleteItem(final LibraryChapter chapter, final boolean deleteFiles)
    {
        MangoSqlite db = new MangoSqlite(LibraryBrowserActivity.this);
        try
        {
            db.open();
            db.deleteLibraryChapter(chapter.rowId);
            MangoLibraryIO.writeLibraryBackup(db.getAllLibraryChapters(null));
            db.close();
            showDialog(0);
            mViewMode = VIEW_SUBFOLDER;
            mLoader = new LibraryLoader(this);
            mLoader.execute(mChapters[0].manga);
        }
        catch (SQLException e)
        {
            Mango.alert("There was a problem deleting the item from the SQLite database!\n\n" + e.getClass().getSimpleName() + ": " + e.getMessage(), LibraryBrowserActivity.this);
        }
        finally
        {
            db.close();
        }

        if (deleteFiles)
            MangoLibraryIO.deleteFiles(chapter);
    }

    private void deleteRange(final boolean deleteFiles)
    {
        MangoSqlite db = new MangoSqlite(LibraryBrowserActivity.this);
        try
        {
            db.open();
            for (int i = mMultiFirstIndex; i <= mMultiSecondIndex; i++)
            {
                db.deleteLibraryChapter(mChapters[i].rowId);
                if (deleteFiles)
                    MangoLibraryIO.deleteFiles(mChapters[i]);
            }
            MangoLibraryIO.writeLibraryBackup(db.getAllLibraryChapters(null));
            db.close();
            showDialog(0);
            mViewMode = VIEW_SUBFOLDER;
            mLoader = new LibraryLoader(this);
            mLoader.execute(mChapters[0].manga);
        }
        catch (SQLException e)
        {
            Mango.alert("There was a problem deleting the item from the SQLite database!\n\n" + e.getClass().getSimpleName() + ": " + e.getMessage(), LibraryBrowserActivity.this);
            Mango.log(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        catch (Exception e)
        {
            Mango.alert("There was a problem deleting the selected items. :'(\nPlease provide the following technical data:\n\n" + e.getClass().getSimpleName() + "\nmChapters.length="
                    + mChapters.length + "\nfirstIndex=" + mMultiFirstIndex + "\nsecondIndex=" + mMultiSecondIndex + "\nrootMode=" + mViewMode, this);
            Mango.log(e.getClass().getSimpleName() + ", mChapters.length=" + mChapters.length + ", firstIndex=" + mMultiFirstIndex + ", secondIndex=" + mMultiSecondIndex + ", viewMode=" + mViewMode);
        }
        finally
        {
            db.close();
        }
    }

    public void loadPendingBookmark(Bookmark bookmark)
    {

    }

    private String generateDisplayText(int index)
    {
        try
        {
            if (mViewMode == VIEW_SUBFOLDER)
            {
                return mChapters[index].chapter.title;
            }
            else
            {
                return mChapters[index].manga.title;
            }
        }
        catch (Exception ex)
        {
            return "null";
        }
    }

    private String generateSubText(int index)
    {
        try
        {
            if (mViewMode == VIEW_SUBFOLDER)
            {
                return Mango.getSiteName(mChapters[index].siteId);
            }
            else
            {
                return mChapters[index].chapterCount + " chapter" + (mChapters[index].chapterCount != 1 ? "s" : "");
            }
        }
        catch (Exception ex)
        {
            return "null";
        }
    }

    class ViewHolder
    {
        View readTag;
        TextView text;
        TextView site;
        ImageView icon;
        LinearLayout overlay;
    }

    class LibraryAdapter extends ArrayAdapter<LibraryChapter>
    {
        LayoutInflater mInflater = null;
        Bitmap mIcon;
        Bitmap mIcon2;

        public LibraryAdapter(Activity context)
        {
            super(context, R.layout.iconlistrow2, mChapters);
            mInflater = context.getLayoutInflater();
            mIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_libraryseries);
            mIcon2 = BitmapFactory.decodeResource(getResources(), R.drawable.ic_librarychapter);
            mReadStatus = new boolean[mChapters.length];
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent)
        {
            ViewHolder holder;
            if (convertView == null)
            {
                convertView = mInflater.inflate(R.layout.iconlistrow2, null);
                holder = new ViewHolder();
                holder.overlay = (LinearLayout) convertView.findViewById(R.id.ilr2RowHolder);
                holder.text = (TextView) convertView.findViewById(R.id.ilr2Label);
                holder.icon = (ImageView) convertView.findViewById(R.id.ilr2Icon);
                holder.site = (TextView) convertView.findViewById(R.id.ilr2Site);
                holder.readTag = convertView.findViewById(R.id.ilr2ReadTag);
                holder.readTag.setBackgroundColor(Color.TRANSPARENT);
                convertView.setTag(holder);
            }
            else
            {
                holder = (ViewHolder) convertView.getTag();
            }

            if (mMultiSelectMode && mMultiFirstIndex != -1 && (position >= mMultiSecondIndex && position <= mMultiFirstIndex))
            {
                holder.overlay.setBackgroundColor(Color.argb(60, 0, 255, 110));
            }

            if (mMultiSelectMode && mMultiFirstIndex != -1
                    && (mMultiSecondIndex > mMultiFirstIndex ? position >= mMultiFirstIndex && position <= mMultiSecondIndex : position >= mMultiSecondIndex && position <= mMultiFirstIndex))
            {
                holder.overlay.setBackgroundColor(Color.argb(60, 0, 255, 110));
            }
            else
            {
                holder.overlay.setBackgroundColor(Color.TRANSPARENT);
            }

            holder.text.setText(generateDisplayText(position));
            holder.site.setText(generateSubText(position));
            holder.readTag.setBackgroundColor(Color.TRANSPARENT);

            try
            {
                if (mViewMode == VIEW_ROOTFOLDER)
                    holder.icon.setImageBitmap(mIcon);
                else
                {
                    holder.icon.setImageBitmap(mIcon2);
                    if (mReadStatus[position])
                        holder.readTag.setBackgroundColor(Color.rgb(56, 101, 0));
                    else
                        holder.readTag.setBackgroundColor(Color.TRANSPARENT);
                }
            }
            catch (Exception e)
            {
                holder.readTag.setBackgroundColor(Color.GRAY);
            }

            convertView.setId(position);
            return convertView;
        }
    }

    class FilesystemAdapter extends ArrayAdapter<FilesystemChapter>
    {
        LayoutInflater mInflater = null;
        Bitmap mIcon;
        Bitmap mIcon2;
        Bitmap mIcon3;

        public FilesystemAdapter(Activity context)
        {
            super(context, R.layout.iconlistrow2, mFolders);
            mInflater = context.getLayoutInflater();
            mIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_folder);
            mIcon2 = BitmapFactory.decodeResource(getResources(), R.drawable.ic_folderup);
            mIcon3 = BitmapFactory.decodeResource(getResources(), R.drawable.ic_libraryseries);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent)
        {
            ViewHolder holder;
            if (convertView == null)
            {
                convertView = mInflater.inflate(R.layout.iconlistrow2, null);
                holder = new ViewHolder();
                holder.overlay = (LinearLayout) convertView.findViewById(R.id.ilr2RowHolder);
                holder.text = (TextView) convertView.findViewById(R.id.ilr2Label);
                holder.icon = (ImageView) convertView.findViewById(R.id.ilr2Icon);
                holder.site = (TextView) convertView.findViewById(R.id.ilr2Site);
                holder.readTag = convertView.findViewById(R.id.ilr2ReadTag);
                holder.readTag.setBackgroundColor(Color.TRANSPARENT);
                holder.overlay.setBackgroundColor(Color.TRANSPARENT);
                convertView.setTag(holder);
            }
            else
            {
                holder = (ViewHolder) convertView.getTag();
            }

            if (position == 0)
            {
                holder.text.setText("Parent Folder");
                holder.site.setText("");
                holder.icon.setImageBitmap(mIcon2);
            }
            else
            {
                holder.text.setText("/" + mFolders[position].fileObj.getName());
                if (mFolders[position].isChapter)
                {
                    holder.site.setText("Chapter (" + mFolders[position].pages + " images)");
                    holder.icon.setImageBitmap(mIcon3);
                }
                else
                {
                    holder.site.setText("Directory");
                    holder.icon.setImageBitmap(mIcon);
                }
            }

            convertView.setId(position);
            return convertView;
        }
    }

    private class LibraryComparator implements Comparator<LibraryChapter>
    {

        @Override
        public int compare(LibraryChapter o1, LibraryChapter o2)
        {
            if (mViewMode == VIEW_ROOTFOLDER)
            {
                String tmp1 = o1.manga.title;
                String tmp2 = o2.manga.title;
                return tmp1.compareTo(tmp2);
            }
            else
            {
                return o1.chapterIndex - o2.chapterIndex;
            }
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (mMultiSelectMode)
            {
                toggleMultiselect(true);
                return true;
            }

            if (mViewMode == VIEW_SUBFOLDER)
            {
                showDialog(0);
                mViewMode = VIEW_ROOTFOLDER;
                mLoader = new LibraryLoader(this);
                mLoader.execute((Manga) null);
            }
            else
                finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private class LibraryLoader extends AsyncTask<Manga, Void, Object>
    {
        LibraryBrowserActivity activity = null;

        public LibraryLoader(LibraryBrowserActivity activity)
        {
            attach(activity);
        }

        void detach()
        {
            activity = null;
        }

        void attach(LibraryBrowserActivity activity)
        {
            this.activity = activity;
        }

        @Override
        protected Object doInBackground(Manga... params)
        {
            if (activity.mViewMode == VIEW_ROOTFOLDER)
                return initializeLibrary();
            else if (activity.mViewMode == VIEW_SUBFOLDER)
                return initializeChapters(params[0]);
            else
                return initializeFilesystemView(mFilesystemUri);
        }

        @Override
        protected void onPostExecute(Object result)
        {
            if (activity.mViewMode == VIEW_ROOTFOLDER)
                rootCallback(null);
            else if (activity.mViewMode == VIEW_SUBFOLDER)
                chapterCallback(null);
            else
                filesystemCallback(result);
            super.onPostExecute(result);
        }
    }

    private class PageScanner extends AsyncTask<Manga, String, String>
    {
        LibraryBrowserActivity activity = null;

        ArrayList<LibraryChapter> chapters = new ArrayList<LibraryChapter>();
        ArrayList<Page> pages = new ArrayList<Page>();

        String substringStart;
        String substringAltStart;
        String urlPrefix;

        int failedPages = 0;
        int repairedPages = 0;

        StringBuilder report = new StringBuilder();

        public PageScanner(LibraryBrowserActivity activity)
        {
            attach(activity);
        }

        void detach()
        {
            activity = null;
        }

        void attach(LibraryBrowserActivity activity)
        {
            this.activity = activity;
        }

        @Override
        protected String doInBackground(Manga... params)
        {
            publishProgress("Reading library metadata table...");
            scanMetadata(params[0]);
            int index = 0;
            for (Iterator iterator = chapters.iterator(); iterator.hasNext(); )
            {
                index++;
                failedPages = 0;
                repairedPages = 0;
                LibraryChapter lc = (LibraryChapter) iterator.next();
                if (lc.path.startsWith("/Mango/"))
                    lc.path = "/Mango/" + lc.path.substring(7);
                publishProgress("Scanning chapter " + (index) + " of " + chapters.size());

                if (!parseXml(MangoLibraryIO.readIndexData(lc.path)))
                {
                    report.append(lc.manga.title + " " + lc.chapter.id + " could not be opened. Please redownload the entire chapter.\n\n");
                    continue;
                }

                for (int i = 0; i < pages.size(); i++)
                {
                    if (!MangoLibraryIO.checkBitmap(lc.path, pages.get(i).id))
                    {
                        publishProgress("Repairing " + lc.manga.title + " " + lc.chapter.id + " (page " + pages.get(i).id + ")");
                        if (!downloadPage(urlPrefix + pages.get(i).id, lc.path, pages.get(i)))
                            failedPages++;
                        else
                            repairedPages++;
                    }
                }

                if (!(repairedPages == 0 && failedPages == 0))
                {
                    report.append(lc.manga.title + " " + lc.chapter.id + ":\n");
                    if (repairedPages != 0)
                        report.append(repairedPages + " pages successfully repaired.\n");
                    if (failedPages != 0)
                        report.append(failedPages + " pages are still missing. (download failed)\n");
                    report.append("\n");
                }

            }

            return null;
        }

        @Override
        protected void onProgressUpdate(String... progress)
        {
            Mango.DIALOG_DOWNLOADING.setMessage(progress[0]);
        }

        @Override
        protected void onPostExecute(String result)
        {
            scannerCallback(report.toString());
            super.onPostExecute(result);
        }

        private boolean downloadPage(String url, String path, Page page)
        {
            if (!substringStart.equals(""))
                url = urlPrefix + page.url;
            else
                url += ".jpg";

            if (!url.toLowerCase().endsWith("jpg") && !url.toLowerCase().endsWith("png") && !url.toLowerCase().endsWith("gif"))
            {
                String html = MangoHttp.downloadHtml(url, activity);
                url = magic(html);

                if (url.contains("Exception"))
                    return false;
            }

            Mango.log(path + ", " + page.id);

            if (MangoHttp.downloadEncodedImage(url, path, page.id, 1, activity).equals("ok"))
                return true;
            return false;
        }

        private String magic(String data)
        {
            try
            {
                if (substringAltStart.equals(""))
                {
                    int srcStart = data.indexOf(substringStart) + substringStart.length();
                    int srcEnd = data.indexOf("\"", srcStart);
                    return data.substring(srcStart, srcEnd);
                }
                int substringOffset = data.indexOf(substringAltStart) + substringAltStart.length();
                substringOffset -= 150; // lolmagic literal
                int srcStart = data.indexOf(substringStart, substringOffset) + substringStart.length();
                int srcEnd = data.indexOf("\"", srcStart);
                return data.substring(srcStart, srcEnd);
            }
            catch (Exception ex)
            {
                return ex.getClass().getSimpleName();
            }
        }

        private boolean scanMetadata(Manga manga)
        {
            MangoSqlite db = new MangoSqlite(activity);
            try
            {
                db.open();

                LibraryChapter[] lc;
                if (manga != null)
                    lc = db.getLibraryChaptersForManga(manga);
                else
                    lc = db.getAllLibraryChapters(null);
                for (int i = 0; i < lc.length; i++)
                {
                    chapters.add(lc[i]);
                }
            }
            catch (SQLException ex)
            {
                return false;
            }
            finally
            {
                if (db != null)
                    db.close();
            }
            return true;
        }

        private boolean parseXml(String data)
        {
            pages = new ArrayList<Page>();
            try
            {
                SAXParserFactory saxFactory = SAXParserFactory.newInstance();
                SAXParser parser = saxFactory.newSAXParser();
                XMLReader reader = parser.getXMLReader();
                PagelistSaxHandler handler = new PagelistSaxHandler();
                reader.setContentHandler(handler);
                reader.parse(new InputSource(new StringReader(data)));
                pages.addAll(handler.getAllPages());
                substringStart = handler.getSubstringStart();
                substringStart.replace("&quot;", "\"");
                substringStart.replace("&amp;", "&");
                substringStart.replace("&lt;", "<");
                substringStart.replace("&gt;", ">");
                substringAltStart = handler.getSubstringAltStart();
                substringAltStart.replace("&quot;", "\"");
                substringAltStart.replace("&amp;", "&");
                substringAltStart.replace("&lt;", "<");
                substringAltStart.replace("&gt;", ">");
                urlPrefix = handler.getImageUrlPrefix();
            }
            catch (Exception ex)
            {
                return false;
            }
            return true;
        }
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
}
