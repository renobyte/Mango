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
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import net.leetsoft.mangareader.*;
import net.leetsoft.mangareader.ui.MangoAdWrapperView;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

public class HistoryActivity extends MangoActivity
{
    private ListView mListview;
    private TextView mEmptyView;
    private Bookmark[] mHistory;
    private HistoryAdapter mAdapter;
    private int mContextPosition;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        this.setTitle("History", null);
        inflateLayoutManager(this, R.layout.mainmenu);
        mListview = (ListView) findViewById(R.id.MainMenuList);
        mEmptyView = (TextView) findViewById(R.id.mainMenuEmpty);

        registerForContextMenu(mListview);
        mListview.setOnCreateContextMenuListener(this);

        super.setAdLayout((MangoAdWrapperView) findViewById(R.id.mainmenuAdLayout));
        super.setJpBackground(R.drawable.jp_bg_history);

        super.logEvent("View History", null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        if (mHistory != null && mHistory.length > 0)
        {
            MenuInflater inflater = getSupportMenuInflater();
            inflater.inflate(R.menu.historymenu, menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
    {
        if (mHistory.length == 0)
            return;
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        menu.setHeaderTitle(generateDisplayText(info.position));
        mContextPosition = info.position;
        menu.add(Menu.NONE, 0, 0, "Open History Item");
        menu.add(Menu.NONE, 1, 1, "Delete History Item");
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item)
    {
        switch (item.getItemId())
        {
            case 0:
                openHistory(mContextPosition);
                return true;
            case 1:
                deleteHistory(mContextPosition);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.menuHistoryClear)
        {
            AlertDialog alert = new AlertDialog.Builder(HistoryActivity.this).create();
            alert.setTitle("Clear History");
            alert.setMessage("This will clear your entire history and all read/unread chapter markers.  Favorites progress will not be affected.\n\nContinue?");
            alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yep, clear", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    clearHistory();
                }
            });
            alert.setButton(DialogInterface.BUTTON_NEGATIVE, "No, cancel", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {}
            });
            alert.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void clearHistory()
    {
        MangoSqlite db = new MangoSqlite(this);
        db.open();
        for (int i = 0; i < mHistory.length; i++)
        {
            db.deleteBookmark(mHistory[i].rowId);
        }
        db.close();
        initializeHistory();
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        if (Mango.DIALOG_DOWNLOADING != null)
            Mango.DIALOG_DOWNLOADING.dismiss();
        if (id == 0)
        {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle("Downloading data...");
            dialog.setMessage("Mango is downloading more information about this item...");
            dialog.setIndeterminate(true);
            dialog.setCancelable(true);
            Mango.DIALOG_DOWNLOADING = dialog;
            return dialog;
        }
        return super.onCreateDialog(id);
    }

    private void initializeHistory()
    {
        Bookmark[] history = null;
        MangoSqlite db = new MangoSqlite(this);
        try
        {
            db.open();
            if (Mango.getSharedPreferences().getLong("nextHistoryWarning", 0) < System.currentTimeMillis())
            {
                int i = db.getHistoryCount();
                if (i >= 9900)
                {
                    Mango.alert(
                            "Your history database is full.  Mango will delete old items as you mark more chapters as read.\n\nYou may want to consider clearing your history by selecting Menu >> Clear History.",
                            this);
                    Mango.getSharedPreferences().edit().putLong("nextHistoryWarning", System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 3)).commit();
                }
                else if (i > 3500)
                {
                    Mango.alert("Your history database is becoming rather large (" + i + " items), which might slow down Mango.\n\nYou may want to consider clearing your history by selecting Menu >> Clear History.", this);
                    Mango.getSharedPreferences().edit().putLong("nextHistoryWarning", System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 3)).commit();
                }
            }
            history = db.getAllHistoryArray(MangoSqlite.KEY_UPDATETIME + " DESC", null, false);
        }
        catch (SQLException ex)
        {
            Mango.alert("Mango encountered an error while retrieving your history from the SQLite database! If this happens again, please let us know, along with the following data:\n\n"
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage(), "Sqlite Error", this);
            mListview.setAdapter(new ArrayAdapter<String>(HistoryActivity.this, android.R.layout.simple_list_item_1, new String[]{"Error loading history!"}));
            return;
        }
        finally
        {
            db.close();
        }

        mHistory = history;
        if (mHistory == null)
            mHistory = new Bookmark[0];
        Arrays.sort(mHistory, new HistoryComparator());

        mAdapter = new HistoryAdapter(this);
        mListview.setAdapter(mAdapter);
        mListview.setOnItemClickListener(new OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, final int position, long id)
            {
                openHistory(position);
            }
        });

        if (mHistory.length > 30)
            mListview.setFastScrollEnabled(true);

        mEmptyView.setVisibility(View.GONE);
        if (mHistory.length == 0)
        {
            mEmptyView.setVisibility(View.VISIBLE);
            mEmptyView.setText("Your reading history is empty.");
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        initializeHistory();
    }

    private void openHistory(final int index)
    {
        if (mHistory[index].siteId == Mango.SITE_LOCAL)
        {
            MangoSqlite db = new MangoSqlite(HistoryActivity.this);
            Manga m = new Manga();
            m.id = mHistory[index].mangaId;
            m.title = mHistory[index].mangaName;
            db.open();
            LibraryChapter[] l = db.getLibraryChaptersForManga(m);
            for (int i = 0; i < l.length; i++)
            {
                if (l[i].chapter.id.equals(mHistory[index].chapterId))
                {
                    Intent prIntent = new Intent();
                    prIntent.setClass(Mango.CONTEXT, OfflinePagereaderActivity.class);
                    prIntent.putExtra("lcSerializable", l[i]);
                    prIntent.putExtra("initialpage", 0);
                    prIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(prIntent);
                    overridePendingTransition(R.anim.fadein, R.anim.expandout);
                    break;
                }
            }
            return;
        }

        Mango.getSharedPreferences().edit().putInt("mangaSite", mHistory[index].siteId).commit();

        Bookmark b = mHistory[index];
        showDialog(0);
        b.buildManga(this);
    }

    private void deleteHistory(final int index)
    {
        MangoSqlite db = new MangoSqlite(HistoryActivity.this);
        try
        {
            db.open();
            db.deleteBookmark(mHistory[index].rowId);
            db.close();
            initializeHistory();
            if (mHistory.length > 0)
                mListview.setSelection(index - 1);
        }
        catch (SQLException e)
        {
            Mango.alert("There was a problem deleting the history item from the SQLite database!\n\n" + e.getClass().getSimpleName() + ": " + e.getMessage(), HistoryActivity.this);
        }
        finally
        {
            db.close();
        }
    }

    public void loadPendingBookmark(Bookmark bookmark)
    {
        Mango.DIALOG_DOWNLOADING.dismiss();
        Intent prIntent = new Intent();
        prIntent.setClass(Mango.CONTEXT, PagereaderActivity.class);
        prIntent.putExtra("manga", bookmark.manga);
        prIntent.putExtra("chapterid", bookmark.chapterId);
        bookmark.pageIndex = (bookmark.bookmarkType == Bookmark.CHAPTER) ? 0 : bookmark.pageIndex;
        prIntent.putExtra("initialpage", bookmark.pageIndex);
        prIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(prIntent);
        overridePendingTransition(R.anim.fadein, R.anim.expandout);
    }

    private String generateDisplayText(int index)
    {
        String itemdisplay = "";
        Bookmark itembookmark = mHistory[index];
        itemdisplay = itembookmark.mangaName + ": " + itembookmark.chapterId;
        return itemdisplay;
    }

    public void pendingItemFailed(MangoHttpResponse data)
    {
        Mango.DIALOG_DOWNLOADING.dismiss();
        Mango.alert(data.toString(), this);
    }

    class ViewHolder
    {
        View readTag;
        TextView text;
        TextView site;
        ImageView icon;
    }

    class HistoryAdapter extends ArrayAdapter<Bookmark>
    {
        LayoutInflater mInflater = null;
        Bitmap mIcon;

        public HistoryAdapter(Activity context)
        {
            super(context, R.layout.iconlistrow, mHistory);
            mInflater = context.getLayoutInflater();
            mIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_history);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent)
        {
            ViewHolder holder;
            if (convertView == null)
            {
                convertView = mInflater.inflate(R.layout.iconlistrow2, null);
                holder = new ViewHolder();
                holder.readTag = convertView.findViewById(R.id.ilr2ReadTag);
                holder.text = (TextView) convertView.findViewById(R.id.ilr2Label);
                holder.site = (TextView) convertView.findViewById(R.id.ilr2Site);
                holder.icon = (ImageView) convertView.findViewById(R.id.ilr2Icon);
                convertView.setTag(holder);
            }
            else
            {
                holder = (ViewHolder) convertView.getTag();
            }
            holder.text.setText(generateDisplayText(position));
            String date = "";
            date = android.text.format.DateFormat.getMediumDateFormat(getApplicationContext()).format(new Date(mHistory[position].updateTime));
            date += ", " + android.text.format.DateFormat.getTimeFormat(getApplicationContext()).format(new Date(mHistory[position].updateTime));
            holder.site.setText(Mango.getSiteName(mHistory[position].siteId) + ", " + date);
            holder.icon.setImageBitmap(mIcon);
            return convertView;
        }
    }

    private class HistoryComparator implements Comparator<Bookmark>
    {
        @Override
        public int compare(Bookmark o1, Bookmark o2)
        {
            if (o1.updateTime < o2.updateTime)
                return 1;
            if (o1.updateTime > o2.updateTime)
                return -1;
            return 0;
        }
    }
}
