package net.leetsoft.mangareader.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.*;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.*;
import android.widget.AdapterView.AdapterContextMenuInfo;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import net.leetsoft.mangareader.*;
import net.leetsoft.mangareader.services.DownloaderService;
import net.leetsoft.mangareader.services.DownloaderService.DownloaderBinder;
import net.leetsoft.mangareader.ui.MangoAdWrapperView;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

public class ChaptersActivity extends MangoActivity
{
    private Manga mActiveManga;
    private EditText mFindTextbox;
    private ListView mListview;
    private TextWatcher mTextfilter;
    private boolean mGotData = false;
    private RelativeLayout mResumeView;
    private RelativeLayout mDetailsPreview;
    private DetailsViewHolder mDetailsHolder;
    private ChapterViewHolder mResumeHolder;
    private ChaptersAdapter mAdapter;
    private Bitmap mFullSizeArt;
    private Bitmap mThumbnailArt;
    private XmlDownloader mXmlTask;
    private BitmapDownloader mBitmapTask;

    private boolean[] mReadStatus;

    private boolean mSkipRestore;
    private boolean mResumeAvailable;

    private Dialog mDetailsPopup;
    private ImageView mDetailsPopupCoverart;

    private DownloaderService mService;

    private boolean mMultiSelectMode;
    private int mMultiFirstIndex = -1;
    private int mMultiSecondIndex = -1;
    private int mMenuPosition;
    private boolean mFavorited = false;

    private class InstanceBundle
    {
        private Manga activeManga;
        private Bitmap fullSizeImg;
        private boolean gotData;
        private boolean popupOpen;
        private XmlDownloader xmlTask;
        private BitmapDownloader bitmapTask;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        inflateLayoutManager(this, R.layout.list_with_find);
        mListview = (ListView) findViewById(R.id.FindList);
        mFindTextbox = (EditText) findViewById(R.id.FindText);
        super.setJpVerticalOffsetView(mFindTextbox);
        super.setAdLayout((MangoAdWrapperView) findViewById(R.id.listAdLayout));
        super.setJpBackground(R.drawable.jp_bg_chapters);
        mFindTextbox.setSingleLine();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        mTextfilter = new TextWatcher()
        {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                String findString = s.toString().toLowerCase();
                ListView lfReference = mListview;

                if (!mGotData)
                    return;
                for (int i = 0; i < lfReference.getAdapter().getCount() - mListview.getHeaderViewsCount(); i++)
                {
                    if (getChapter(i).id.toLowerCase().endsWith(findString))
                    {
                        lfReference.setSelection(i + (mResumeAvailable ? 2 : 1));
                        return;
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s)
            {}

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {}

        };
        mFindTextbox.addTextChangedListener(mTextfilter);
        mListview.setFastScrollEnabled(true);
        mFindTextbox = (EditText) findViewById(R.id.FindText);
        mFindTextbox.setSingleLine();
        mFindTextbox.setHint("Jump to chapter number");
        DigitsKeyListener dkl = new DigitsKeyListener(true, true);
        mFindTextbox.setKeyListener(dkl);

        this.registerForContextMenu(mListview);
        mListview.setOnCreateContextMenuListener(this);

        Bundle arguments = getIntent().getExtras();
        mActiveManga = (Manga) arguments.getSerializable("manga");

        mSkipRestore = true;
        if (getLastCustomNonConfigurationInstance() != null)
        {
            InstanceBundle save = (InstanceBundle) getLastCustomNonConfigurationInstance();
            mActiveManga = save.activeManga;
            mFullSizeArt = save.fullSizeImg;
            mGotData = save.gotData;
            mXmlTask = save.xmlTask;
            mBitmapTask = save.bitmapTask;
            if (mXmlTask != null)
                mXmlTask.attach(this);
            if (mBitmapTask != null)
                mBitmapTask.attach(this);
            if (mActiveManga.chapters == null || mActiveManga.chapters.length == 0)
                return;
            mAdapter = new ChaptersAdapter(this);
            initializeDetailsHolder();
            initializeResumeView();
            mListview.setAdapter(mAdapter);
            mListview.setOnItemClickListener(new clickListener());
            if (save.popupOpen)
                showDetailsPopup();
        }
        else
        {
            super.logEvent("Browse Chapters", null);
            showDialog(0);
            mXmlTask = new XmlDownloader(this);
            mXmlTask.execute("http://%SERVER_URL%/getchapterlist.aspx?pin=" + Mango.getPin() + "&url=" + mActiveManga.id + "&site=" + Mango.getSiteId());
        }
        this.setTitle(mActiveManga.title, Mango.getSiteName(Mango.getSiteId()));
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
    {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        mMenuPosition = info.position - mListview.getHeaderViewsCount();

        if (mMenuPosition < 0)
            return;
        super.onCreateContextMenu(menu, v, menuInfo);

        menu.setHeaderTitle(getChapter(mMenuPosition).title);
        menu.add(Menu.NONE, 0, 0, "Download to My Library");
        menu.add(Menu.NONE, 1, 1, "Mark " + (mReadStatus[mMenuPosition] ? "Unread" : "Read"));
        menu.add(Menu.NONE, 2, 2, "Mark this and previous as unread");
        menu.add(Menu.NONE, 3, 3, "Mark this and previous as read");
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item)
    {
        switch (item.getItemId())
        {
            case 0:
                addChapterToQueue(getChapter(mMenuPosition).id, false);
                return true;
            case 1:
                changeReadStatus(getChapter(mMenuPosition), !mReadStatus[mMenuPosition]);
                return true;
            case 2:
                setAllAsUnread(getChapter(mMenuPosition));
                return true;
            case 3:
                setAllAsRead(getChapter(mMenuPosition));
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void setAllAsRead(Chapter startChapter)
    {
        MangoSqlite db = new MangoSqlite(this);
        db.open();

        int index = -1;
        for (int i = 0; i < mActiveManga.chapters.length; i++)
        {
            if (mActiveManga.chapters[i].id.equals(startChapter.id))
            {
                index = i;
                break;
            }
        }

        if (index == -1)
            return;

        for (int k = index; k >= 0; k--)
        {
            if (!mReadStatus[k])
                db.insertRecentBookmark(mActiveManga.id, mActiveManga.title, k, mActiveManga.chapters[k].title, mActiveManga.chapters[k].id, mActiveManga.chapters.length, Mango.getSiteId(), true);
        }
        db.close();
        initializeReadTags();
    }

    private void setAllAsUnread(Chapter startChapter)
    {
        MangoSqlite db = new MangoSqlite(this);
        long deleteRowId = -1;
        db.open();
        Bookmark[] bmarray = db.getAllHistoryArray(MangoSqlite.KEY_UPDATETIME + " ASC", MangoSqlite.KEY_MANGAID + " = '" + mActiveManga.id + "'", true);

        int index = -1;
        for (int i = 0; i < mActiveManga.chapters.length; i++)
        {
            if (mActiveManga.chapters[i].id.equals(startChapter.id))
            {
                index = i;
                break;
            }
        }

        if (index == -1)
            return;

        for (int k = index; k >= 0; k--)
        {
            if (mReadStatus[k])
            {
                for (int i = 0; i < bmarray.length; i++)
                {
                    Bookmark bm = bmarray[i];
                    if (bm.bookmarkType == Bookmark.RECENT && bm.mangaId.equals(mActiveManga.id) && bm.chapterId.equals(mActiveManga.chapters[k].id))
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

    private void changeReadStatus(Chapter chapter, boolean read)
    {
        MangoSqlite db = new MangoSqlite(this);
        db.open();

        int index = -1;
        for (int i = 0; i < mActiveManga.chapters.length; i++)
        {
            if (mActiveManga.chapters[i].id.equals(chapter.id))
            {
                index = i;
                break;
            }
        }

        if (mReadStatus[mMenuPosition])
        {
            Bookmark[] bmarray = db.getAllHistoryArray(MangoSqlite.KEY_UPDATETIME + " ASC", MangoSqlite.KEY_MANGAID + " = '" + mActiveManga.id + "'", true);
            long deleteRowId = -1;
            for (int i = 0; i < bmarray.length; i++)
            {
                Bookmark bm = bmarray[i];
                if (bm.bookmarkType == Bookmark.RECENT && bm.mangaId.equals(mActiveManga.id) && bm.chapterId.equals(mActiveManga.chapters[index].id))
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
            db.insertRecentBookmark(mActiveManga.id, mActiveManga.title, index, mActiveManga.chapters[index].title, mActiveManga.chapters[index].id, mActiveManga.chapters.length, Mango.getSiteId(),
                    true);
        }

        db.close();

        initializeReadTags();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.chaptersmenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        if (menu == null)
            return false;
        menu.clear();
        onCreateOptionsMenu(menu);

        if (!mFavorited)
        {
            menu.getItem(0).setIcon(R.drawable.ic_action_favorite);
            menu.getItem(0).setTitle("Add Favorite");
            menu.getItem(0).setTitleCondensed("Add Favorite");
        }
        else
        {
            menu.getItem(0).setIcon(R.drawable.ic_action_remove);
            menu.getItem(0).setTitle("Remove Favorite");
            menu.getItem(0).setTitleCondensed("Remove Favorite");
        }

        if (mMultiSelectMode && mMultiFirstIndex == -1)
        {
            menu.getItem(1).setTitle("Cancel");
            menu.getItem(1).setTitleCondensed("Cancel");
            menu.getItem(1).setIcon(getResources().getDrawable(R.drawable.ic_action_clear));
        }
        else if (mMultiSelectMode)
        {
            menu.getItem(1).setTitle("Download These");
            menu.getItem(1).setTitleCondensed("Download These");
            menu.getItem(1).setIcon(getResources().getDrawable(R.drawable.ic_action_okay));
        }
        else
        {
            menu.getItem(1).setTitle("Multi-Select");
            menu.getItem(1).setTitleCondensed("Multi-Select");
            menu.getItem(1).setIcon(getResources().getDrawable(R.drawable.ic_action_multiselect));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.menuChapterMultiSelect)
        {
            toggleMultiselect(false);
            return true;
        }
        if (item.getItemId() == R.id.menuChapterAddFavorite)
        {
            MangoSqlite db = new MangoSqlite(ChaptersActivity.this);
            try
            {
                db.open();
                if (mFavorited)
                {
                    db.deleteFavorite(db.getFavoriteForManga(mActiveManga).rowId);
                    Mango.alert(mActiveManga.title + " has been removed from your favorites.", "Favorite Deleted", ChaptersActivity.this);
                }
                else
                {
                    Favorite f = new Favorite();
                    Manga m = mActiveManga;
                    f.isOngoing = !m.completed;
                    f.mangaId = m.id;
                    f.mangaTitle = m.title;
                    f.mangaSimpleName = m.simpleName;
                    f.coverArtUrl = m.coverart;
                    f.notificationsEnabled = false;
                    f.siteId = Mango.getSiteId();
                    db.insertFavorite(f);

                    Mango.alert(mActiveManga.title + " has been favorited! Mango will now track your reading progress in the Favorites screen.", "Favorite Created", ChaptersActivity.this);
                }
                mFavorited = !mFavorited;
            }
            catch (SQLException ex)
            {

            }
            finally
            {
                db.close();
            }
        }

        refreshMenu();
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle save)
    {
        super.onSaveInstanceState(save);
        save.putBoolean("gotdata", mGotData);
        save.putSerializable("manga", mActiveManga);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);
        if (mSkipRestore)
            return;
        mGotData = savedInstanceState.getBoolean("gotdata");
        mActiveManga = (Manga) savedInstanceState.get("manga");
        mAdapter = new ChaptersAdapter(this);
        initializeDetailsHolder();
        initializeResumeView();
        mListview.setAdapter(mAdapter);
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance()
    {
        InstanceBundle save = new InstanceBundle();
        save.activeManga = mActiveManga;
        save.activeManga.chapters = mActiveManga.chapters;
        save.fullSizeImg = mFullSizeArt;
        save.gotData = mGotData;
        save.xmlTask = mXmlTask;
        save.bitmapTask = mBitmapTask;
        if (mDetailsPopup != null && mDetailsPopup.isShowing())
            save.popupOpen = true;
        if (mXmlTask != null)
            mXmlTask.detach();
        if (mBitmapTask != null)
            mBitmapTask.detach();
        mActiveManga = null;
        mFullSizeArt = null;
        if (mDetailsHolder != null)
            mDetailsHolder.coverart.setImageBitmap(null);
        return save;
    }

    @Override
    public void onDestroy()
    {
        mXmlTask.detach();
        mFindTextbox.removeTextChangedListener(mTextfilter);
        if (mDetailsPopup != null)
            mDetailsPopupCoverart.setImageBitmap(null);
        super.onDestroy();
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        if (id == 0)
        {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle("Downloading data...");
            dialog.setMessage("Retrieving chapters for " + mActiveManga.title + " from the server...");
            dialog.setIndeterminate(true);
            dialog.setCancelable(true);
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
            if (mAdapter != null)
                ((BaseAdapter) mAdapter).notifyDataSetChanged();
            Toast.makeText(ChaptersActivity.this, "Multi-select mode cancelled.", Toast.LENGTH_SHORT).show();
        }
        else if (!mMultiSelectMode)
        {

            if (!Mango.getSharedPreferences().getBoolean("multiSelectPopup", false))
            {
                Mango.alert(
                        "Multi-select mode allows you to quickly queue a number of chapters to download.\n\nTap the first chapter you wish to download, then the last.  Finally, press the check button.  Press Back to cancel.",
                        ChaptersActivity.this);
                Mango.getSharedPreferences().edit().putBoolean("multiSelectPopup", true).commit();
            }
            else
                Toast.makeText(ChaptersActivity.this, "Multi-select mode activated.", Toast.LENGTH_SHORT).show();

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

            AlertDialog alert = new AlertDialog.Builder(ChaptersActivity.this).create();
            alert.setTitle("Add to Queue");
            alert.setMessage("Are you sure you want to add " + chapterCount + " chapters to the download queue?");
            alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yes", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    addManyToQueue();
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

    private void callback(MangoHttpResponse data)
    {
        Mango.DIALOG_DOWNLOADING.dismiss();
        removeDialog(0);
        if (data.exception)
        {
            Mango.alert("Mango was unable to fetch the requested data.\n\nPlease try again in a moment or try another manga source.\n\n<strong>Error Details:</strong>\n" + data.toString(), "Network Error", this);
            mListview.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new String[]{"Unable to load data.  Close this screen and try again."}));
            return;
        }
        if (data.toString().startsWith("error"))
        {
            Mango.alert("The server returned an error.\n\nPlease try again in a moment or try another manga source.\n\n<strong>Error Details:</strong>\n" + data.toString().substring(7), "Server Error", this);
            mListview.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new String[]{"Unable to load data.  Close this screen and try again."}));
            return;
        }
        parseXml(data.toString());
    }

    public void callbackCoverArt(MangoHttpResponse img)
    {
        if (img.exception)
        {
            Mango.log("Failed to download cover art bitmap." + img.toString());
            setCoverArt(BitmapFactory.decodeResource(getResources(), R.drawable.placeholder_error), true);
            return;
        }

        try
        {
            setCoverArt(img.toBitmap(), false);
        }
        catch (final Exception e)
        {
            Mango.log("setCoverArt failed. " + e.toString());
            setCoverArt(BitmapFactory.decodeResource(getResources(), R.drawable.placeholder_error), true);
        }
    }

    private void parseXml(String data)
    {
        ArrayList<Chapter> chapterArrayList = new ArrayList<Chapter>();

        try
        {
            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            SAXParser parser = saxFactory.newSAXParser();
            XMLReader reader = parser.getXMLReader();
            ChaptersSaxHandler handler = new ChaptersSaxHandler();
            reader.setContentHandler(handler);
            reader.parse(new InputSource(new StringReader(data)));
            chapterArrayList.addAll(handler.getAllChapters());
            mActiveManga.details = handler.getDetails();
            // make sure the data was parsed properly.
            mActiveManga.details.artist.toString();
            MangoSqlite db = new MangoSqlite(ChaptersActivity.this);
            db.open();
            Favorite f = db.getFavoriteForManga(mActiveManga);
            if (f != null)
            {
                if (f.coverArtUrl.length() < 5 || f.coverArtUrl.contains("nocoverart"))
                {
                    f.coverArtUrl = mActiveManga.details.coverArtUrl;
                    db.updateFavorite(f);
                }
                if (MangoCache.checkCacheForImage("cover/", f.coverArtUrl))
                    mFullSizeArt = MangoCache.readBitmapFromCache("cover/", f.coverArtUrl, 1);
                else if (f.coverArtUrl.startsWith("file@"))
                    mFullSizeArt = MangoCache.readCustomCoverArt(f.coverArtUrl, 1);
            }
            db.close();
        }
        catch (SAXException ex)
        {
            Mango.alert("The server returned malformed XML.\n\n<strong>Error Details:</strong>\n" + ex.toString() + "\n\n" + data, "Invalid Response", this);
            return;
        }
        catch (NullPointerException ex)
        {
            Mango.alert("The chapter list for this manga was empty.  Try looking it up on another manga source.", "No Chapter List", this);
            return;
        }
        catch (ParserConfigurationException e)
        {
        }
        catch (IOException e)
        {
        }

        mGotData = true;

        mActiveManga.chapters = new Chapter[chapterArrayList.size()];
        chapterArrayList.toArray(mActiveManga.chapters);
        chapterArrayList = null;

        mAdapter = new ChaptersAdapter(this);
        initializeDetailsHolder();
        initializeResumeView();

        mListview.setAdapter(mAdapter);
        mListview.setOnItemClickListener(new clickListener());
        mListview.setFastScrollEnabled(true);
    }

    private void initializeDetailsHolder()
    {
        mDetailsPreview = (RelativeLayout) View.inflate(this, R.layout.detailslistrow, null);
        mListview.addHeaderView(mDetailsPreview);
        mDetailsHolder = new DetailsViewHolder();
        mDetailsHolder.artist = (TextView) mDetailsPreview.findViewById(R.id.artistlabel);
        mDetailsHolder.coverart = (ImageView) mDetailsPreview.findViewById(R.id.coverartthumb);
        mDetailsHolder.summary = (TextView) mDetailsPreview.findViewById(R.id.summarylabel);
        mDetailsHolder.artist.setSelected(true);
        if (mActiveManga.details.artist.equals(mActiveManga.details.author))
            mDetailsHolder.artist.setText("Art and story by " + mActiveManga.details.artist);
        else
            mDetailsHolder.artist.setText(mActiveManga.details.artist + " and " + mActiveManga.details.author);
        mDetailsHolder.summary.setText(mActiveManga.details.summary);
        setCoverArt(BitmapFactory.decodeResource(getResources(), R.drawable.placeholder_downloading), true);
        if (mFullSizeArt == null)
        {
            mBitmapTask = new BitmapDownloader(this);
            mBitmapTask.execute(mActiveManga.details.coverArtUrl);
        }
        else
            setCoverArt(mFullSizeArt, false);

        initializeReadTags();
    }

    private void initializeReadTags()
    {
        mReadStatus = new boolean[mActiveManga.chapters.length];
        new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                try
                {
                    MangoSqlite db = new MangoSqlite(ChaptersActivity.this);
                    db.open();
                    Bookmark[] b = db.getAllHistoryArray(MangoSqlite.KEY_UPDATETIME + " DESC", MangoSqlite.KEY_MANGAID + " = '" + mActiveManga.id + "'", true);
                    db.close();
                    for (int i = 0; i < b.length; i++)
                    {
                        Bookmark bm = b[i];
                        if (bm.siteId == Mango.getSiteId() && bm.mangaId.equals(mActiveManga.id))
                        {
                            for (int j = 0; j < mActiveManga.chapters.length; j++)
                            {
                                if (bm.chapterId.equals(getChapter(j).id))
                                    mReadStatus[j] = true;
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    Mango.log("Exception");
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
        }).start();
    }

    private void initializeResumeView()
    {
        if (mResumeHolder == null)
        {
            mResumeHolder = new ChapterViewHolder();
            mResumeView = (RelativeLayout) View.inflate(this, R.layout.iconlistrow, null);
            mResumeHolder.icon = (ImageView) mResumeView.findViewById(R.id.icon);
            mResumeHolder.text = (TextView) mResumeView.findViewById(R.id.label);
            mResumeView.findViewById(R.id.star).setVisibility(View.GONE);
            mResumeHolder.icon.setImageResource(R.drawable.ic_resume);
        }
        MangoSqlite db = new MangoSqlite(this);
        db.open();
        Favorite f = db.getFavoriteForManga(mActiveManga);
        if (f != null)
            mFavorited = true;

        refreshMenu();

        if (f != null && f.siteId == Mango.getSiteId() && f.progressChapterId != null)
        {
            mResumeAvailable = true;
            mResumeHolder.text.setText("Resume from " + (f.progressChapterId.length() > 4 ? "" : "Chapter ") + f.progressChapterId);
            if (mListview.getHeaderViewsCount() == 1)
                mListview.addHeaderView(mResumeView);
        }
        db.close();
    }

    @Override
    public void onResume()
    {
        if (mListview.getHeaderViewsCount() == 2)
            initializeResumeView();
        super.onResume();
    }

    private void setCoverArt(Bitmap img, boolean placeholder)
    {
        try
        {
            if (!placeholder)
                mFullSizeArt = img.copy(Config.RGB_565, false);
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            Bitmap overlay = BitmapFactory.decodeResource(getResources(), R.drawable.coverart_overlay);
            Bitmap thumbnail = Bitmap.createBitmap(overlay.getWidth(), overlay.getHeight(), Bitmap.Config.ARGB_8888);
            Bitmap artInset = Bitmap.createBitmap((int) (93 * metrics.density), (int) (93 * metrics.density), Bitmap.Config.RGB_565);

            float downscaleFactor = 1;
            if (img.getHeight() > img.getWidth())
                downscaleFactor = (93 * metrics.density) / img.getScaledWidth(metrics);
            else
                downscaleFactor = (93 * metrics.density) / img.getScaledHeight(metrics);

            Canvas c = new Canvas(artInset);
            c.drawBitmap(Bitmap.createScaledBitmap(img, (int) (img.getWidth() * downscaleFactor), (int) (img.getHeight() * downscaleFactor), true), 0, 0, null);

            c = new Canvas(thumbnail);
            c.drawBitmap(overlay, 0, 0, null);
            c.drawBitmap(artInset, 2 * metrics.density, 2 * metrics.density, null);

            mDetailsHolder.coverart.setImageBitmap(thumbnail);
            if (mDetailsPopup != null && mDetailsPopup.isShowing())
                mDetailsPopupCoverart.setImageBitmap(mFullSizeArt);
        }
        catch (OutOfMemoryError er)
        {
            Mango.log("OutOfMemoryError in ChaptersActivity.setCoverArt.");
            System.gc();
            mDetailsPopupCoverart.setImageBitmap(null);
        }
    }

    public static class ChaptersSaxHandler extends DefaultHandler
    {
        ArrayList<Chapter> allChapters;
        Chapter currentChapter;
        MangaDetails details = new MangaDetails();
        boolean inDetails = true;

        public ArrayList<Chapter> getAllChapters()
        {
            return this.allChapters;
        }

        public MangaDetails getDetails()
        {
            return this.details;
        }

        @Override
        public void startDocument() throws SAXException
        {
            super.startDocument();
            allChapters = new ArrayList<Chapter>();
            currentChapter = null;
        }

        @Override
        public void endDocument()
        {
            if (allChapters.size() < 1)
                details = null; // force an exception later on
        }

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException
        {
            if (localName.equalsIgnoreCase("chapter"))
            {
                this.currentChapter = new Chapter();
            }
            else if (localName.equalsIgnoreCase("coverurl"))
            {
                details.coverArtUrl = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("rank"))
            {
                details.rank = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("genres"))
            {
                details.genres = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("author"))
            {
                details.author = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("artist"))
            {
                details.artist = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("schedule"))
            {
                details.schedule = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("summary"))
            {
                details.summary = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("title") && this.currentChapter != null)
            {
                currentChapter.title = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("id"))
            {
                currentChapter.id = attributes.getValue(0);
                currentChapter.url = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("url"))
            {
                currentChapter.url = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("scan"))
            {
                currentChapter.scanlator = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("date"))
            {
                currentChapter.date = attributes.getValue(0);
            }
        }

        @Override
        public void endElement(String uri, String localName, String name) throws SAXException
        {
            super.endElement(uri, localName, name);
            if (this.currentChapter != null)
            {
                if (localName.equalsIgnoreCase("chapter"))
                {
                    allChapters.add(currentChapter);
                }
            }
        }
    }

    class clickListener implements AdapterView.OnItemClickListener
    {
        @Override
        public void onItemClick(AdapterView<?> parent, View v, final int position, long id)
        {
            if (!mGotData)
                return;

            if (position == 0)
            {
                showDetailsPopup();
                return;
            }

            final int pos = position - (mResumeAvailable ? 2 : 1);

            if (pos == -1)
            {
                resumeChapter();
                return;
            }

            if (mMultiSelectMode)
            {
                refreshMenu();
                if (mMultiFirstIndex == -1)
                {
                    mMultiSecondIndex = pos;
                    mMultiFirstIndex = pos;
                    ((BaseAdapter) mAdapter).notifyDataSetChanged();
                    return;
                }
                mMultiSecondIndex = pos;
                ((BaseAdapter) mAdapter).notifyDataSetChanged();
                return;
            }

            AlertDialog alert = new AlertDialog.Builder(ChaptersActivity.this).create();
            alert.setTitle(mActiveManga.title + " " + getChapter(pos).id);
            alert.setMessage("What would you like to do with this chapter?");
            alert.setButton(DialogInterface.BUTTON_POSITIVE, "Start Reading", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    Intent prIntent = new Intent();
                    prIntent.setClass(Mango.CONTEXT, PagereaderActivity.class);
                    prIntent.putExtra("manga", mActiveManga);
                    prIntent.putExtra("chapterid", getChapter(pos).id);
                    prIntent.putExtra("initialpage", 0);
                    prIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(prIntent);
                    overridePendingTransition(R.anim.fadein, R.anim.expandout);
                }
            });
            alert.setButton(DialogInterface.BUTTON_NEGATIVE, "Save to Library", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    addChapterToQueue(getChapter(pos).id, false);
                    if (!Mango.getSharedPreferences().getBoolean("multiselectTip", false))
                    {
                        Mango.alert("Choose Multi-Select from the top-right corner to quicky add many chapters to your queue.", "Tip", ChaptersActivity.this,
                                new DialogInterface.OnClickListener()
                                {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which)
                                    {
                                        Mango.getSharedPreferences().edit().putBoolean("multiselectTip", true).commit();
                                    }
                                });
                    }
                }
            });
            alert.show();
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
        }
        return super.onKeyDown(keyCode, event);
    }

    private void resumeChapter()
    {
        MangoSqlite db = new MangoSqlite(this);
        try
        {
            db.open();
            Favorite f = db.getFavoriteForManga(mActiveManga);
            Intent prIntent = new Intent();
            prIntent.setClass(Mango.CONTEXT, PagereaderActivity.class);
            prIntent.putExtra("manga", mActiveManga);
            prIntent.putExtra("chapterid", f.progressChapterId);
            prIntent.putExtra("initialpage", f.progressPageIndex);
            prIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(prIntent);
            overridePendingTransition(R.anim.fadein, R.anim.expandout);
            db.close();
        }
        catch (Exception e)
        {
            // TODO: handle exception
        }
        finally
        {
            db.close();
        }
    }

    private void showDetailsPopup()
    {
        mDetailsPopup = new Dialog(ChaptersActivity.this);
        Window window = mDetailsPopup.getWindow();
        window.requestFeature(Window.FEATURE_NO_TITLE);
        mDetailsPopup.setContentView(getLayoutInflater().inflate(R.layout.details, null));
        mDetailsPopup.show();
        TextView title = (TextView) mDetailsPopup.findViewById(R.id.DetailsMangaTitle);
        mDetailsPopupCoverart = (ImageView) mDetailsPopup.findViewById(R.id.DetailsCoverArt);
        TextView artistauthor = (TextView) mDetailsPopup.findViewById(R.id.DetailsArtistAuthor);
        TextView popularity = (TextView) mDetailsPopup.findViewById(R.id.DetailsPopularity);
        TextView genres = (TextView) mDetailsPopup.findViewById(R.id.DetailsGenre);
        TextView schedule = (TextView) mDetailsPopup.findViewById(R.id.DetailsChapters);
        TextView summary = (TextView) mDetailsPopup.findViewById(R.id.DetailsSummary);
        title.setText(mActiveManga.title);
        if (mFullSizeArt != null)
            mDetailsPopupCoverart.setImageBitmap(mFullSizeArt);
        artistauthor.setText(mDetailsHolder.artist.getText());
        popularity.setText(mActiveManga.details.rank);
        genres.setText(mActiveManga.details.genres);
        schedule.setText(mActiveManga.chapters.length + " chapters (" + mActiveManga.details.schedule + ")");
        summary.setText(mActiveManga.details.summary.replace("   ", "\n"));
    }

    private Chapter getChapter(int position)
    {
        if (Mango.getSharedPreferences().getBoolean("reverseChapterList", false))
            return mAdapter.getItem(mAdapter.getCount() - position - 1);
        else
            return mAdapter.getItem(position);
    }

    class ChapterViewHolder
    {
        View readTag;
        TextView text;
        TextView site;
        ImageView icon;
        LinearLayout overlay;
    }

    class DetailsViewHolder
    {
        ImageView coverart;
        TextView artist;
        TextView summary;
        TextView seemore;
    }

    class ChaptersAdapter extends ArrayAdapter<Chapter>
    {
        LayoutInflater mInflater;

        public ChaptersAdapter(MangoActivity context)
        {
            super(context, R.layout.iconlistrow2, mActiveManga.chapters);
            mReadStatus = new boolean[mActiveManga.chapters.length];
            mInflater = context.getLayoutInflater();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent)
        {
            ChapterViewHolder holder;
            if (convertView == null || convertView.getTag() == null)
            {
                convertView = mInflater.inflate(R.layout.iconlistrow2, null);
                holder = new ChapterViewHolder();
                holder.overlay = (LinearLayout) convertView.findViewById(R.id.ilr2RowHolder);
                holder.icon = (ImageView) convertView.findViewById(R.id.ilr2Icon);
                holder.text = (TextView) convertView.findViewById(R.id.ilr2Label);
                holder.site = (TextView) convertView.findViewById(R.id.ilr2Site);
                holder.readTag = convertView.findViewById(R.id.ilr2ReadTag);
                holder.readTag.setBackgroundColor(Color.TRANSPARENT);
                convertView.setTag(holder);
                holder.icon.setImageResource(R.drawable.ic_librarychapter);
            }
            else
            {
                holder = (ChapterViewHolder) convertView.getTag();
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

            if (mReadStatus[position])
                holder.readTag.setBackgroundColor(Color.rgb(56, 101, 0));
            else
                holder.readTag.setBackgroundColor(Color.TRANSPARENT);

            holder.text.setText(getChapter(position).title);
            holder.site.setText("");
            if (getChapter(position).date != null && getChapter(position).date.length() > 0)
                holder.site.setText("Added " + getChapter(position).date);
            if (getChapter(position).scanlator != null && getChapter(position).scanlator.length() > 0)
            {
                if (holder.site.getText().length() > 0)
                    holder.site.setText(holder.site.getText() + " ");
                holder.site.setText(holder.site.getText() + "by " + getChapter(position).scanlator);
            }
            return convertView;
        }
    }

    private void addChapterToQueue(final String chapterId, final boolean silent)
    {
        ServiceConnection sConnection = new ServiceConnection()
        {

            @Override
            public void onServiceConnected(ComponentName className, IBinder service)
            {
                DownloaderBinder binder = (DownloaderBinder) service;
                mService = binder.getService();
                mService.addToQueue(mActiveManga, chapterId, Mango.getSiteId(), silent);
                unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName arg0)
            {}
        };
        startService(new Intent(ChaptersActivity.this, DownloaderService.class));
        bindService(new Intent(ChaptersActivity.this, DownloaderService.class), sConnection, Context.BIND_AUTO_CREATE);
    }

    private void addManyToQueue()
    {
        ServiceConnection sConnection = new ServiceConnection()
        {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service)
            {
                Mango.log("addManyToQueue: Connected to service! (" + service.toString() + ")");
                DownloaderBinder binder = (DownloaderBinder) service;
                mService = binder.getService();
                if (Mango.getSharedPreferences().getBoolean("reverseChapterList", false))
                {
                    int retval = 0;
                    for (int i = mMultiSecondIndex; i >= mMultiFirstIndex; i--)
                    {
                        String id = getChapter(i).id;
                        retval = mService.addToQueue(mActiveManga, id, Mango.getSiteId(), true);
                        if (retval != 0)
                            Mango.log("addManyToQueue: mService.addToQueue returned " + retval);
                    }
                }
                else
                {
                    int retval = 0;
                    for (int i = mMultiFirstIndex; i <= mMultiSecondIndex; i++)
                    {
                        String id = getChapter(i).id;
                        retval = mService.addToQueue(mActiveManga, id, Mango.getSiteId(), true);
                        if (retval != 0)
                            Mango.log("addManyToQueue: mService.addToQueue returned " + retval);
                    }
                }

                Mango.log("addManyToQueue: Calling unbindService!");
                unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName arg0)
            {
                Mango.log("Disconnected from service in addManyToQueue!");
            }
        };
        Mango.log("addManyToQueue: Binding to service!");
        Intent i = new Intent(ChaptersActivity.this, DownloaderService.class);
        if (!bindService(i, sConnection, Context.BIND_AUTO_CREATE))
        {
            Mango.log("The call to bindService returned false.");
            Mango.alert("Mango was unable to bind to the Downloader Service. Try adding a single chapter to the downloader, then try multi-select.", this);
            return;
        }
        Mango.log("addManyToQueue: Bound to service. Now calling startService.");
        startService(i);

    }

    private class XmlDownloader extends AsyncTask<String, Void, MangoHttpResponse>
    {
        ChaptersActivity activity = null;

        public XmlDownloader(ChaptersActivity activity)
        {
            attach(activity);
        }

        @Override
        protected MangoHttpResponse doInBackground(String... params)
        {
            return MangoHttp.downloadData(params[0], activity);
        }

        @Override
        protected void onPostExecute(MangoHttpResponse data)
        {
            if (activity == null)
            {
                Mango.DIALOG_DOWNLOADING.dismiss();
                Mango.log("AsyncTask skipped onPostExecute because no activity is attached!");
            }
            else
            {
                activity.callback(data);
            }
        }

        void detach()
        {
            activity = null;
        }

        void attach(ChaptersActivity activity)
        {
            this.activity = activity;
        }
    }

    private class BitmapDownloader extends AsyncTask<String, Void, MangoHttpResponse>
    {
        ChaptersActivity activity = null;

        public BitmapDownloader(ChaptersActivity activity)
        {
            attach(activity);
        }

        @Override
        protected MangoHttpResponse doInBackground(String... params)
        {
            return MangoHttp.downloadData(params[0], activity);
        }

        @Override
        protected void onPostExecute(MangoHttpResponse img)
        {
            if (activity == null)
            {
                Mango.log("AsyncTask skipped onPostExecute because no activity is attached!");
            }
            else
            {
                activity.callbackCoverArt(img);
            }
            detach();
        }

        void detach()
        {
            activity = null;
        }

        void attach(ChaptersActivity activity)
        {
            this.activity = activity;
        }
    }
}
