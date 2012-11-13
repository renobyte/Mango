package net.leetsoft.mangareader.activities;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import net.leetsoft.mangareader.*;
import net.leetsoft.mangareader.ui.MangoAdWrapperView;
import net.leetsoft.mangareader.ui.MangoTutorialHandler;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.*;

public class AllMangaActivity extends MangoActivity
{
    private Manga[] mMangaList;
    private boolean mGotData = false;
    private XmlDownloader mDownloadTask;
    private XmlParser mParserTask;

    private EditText mFindTextbox;
    private ListView mListview;
    private TextWatcher mTextfilter;

    private boolean mSkipRestore;

    private class InstanceBundle
    {
        private Manga[] mangaList;
        private boolean gotData;
        private XmlDownloader downloadTask;
        private XmlParser parserTask;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        this.setTitle("All Manga", Mango.getSiteName(Mango.getSiteId()));
        inflateLayoutManager(this, R.layout.list_with_find);
        mListview = (ListView) findViewById(R.id.FindList);
        mFindTextbox = (EditText) findViewById(R.id.FindText);
        mFindTextbox.setSingleLine();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        super.setJpVerticalOffsetView(mFindTextbox);
        super.setAdLayout((MangoAdWrapperView) findViewById(R.id.listAdLayout));
        super.setJpBackground(R.drawable.jp_bg_allmanga);
        mTextfilter = new TextWatcher()
        {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                String findString = s.toString().toLowerCase();
                ListView lfReference = AllMangaActivity.this.mListview;

                if (!mGotData || lfReference.getAdapter() == null)
                    return;
                for (int i = 0; i < lfReference.getAdapter().getCount(); i++)
                {
                    if (getManga(i).title.toLowerCase().startsWith(findString))
                    {
                        lfReference.setSelection(i);
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

        if (getLastCustomNonConfigurationInstance() != null && ((InstanceBundle) getLastCustomNonConfigurationInstance()).mangaList != null)
        {
            mSkipRestore = true;
            InstanceBundle save = (InstanceBundle) getLastCustomNonConfigurationInstance();
            mMangaList = save.mangaList;
            mDownloadTask = save.downloadTask;
            mParserTask = save.parserTask;
            if (mDownloadTask != null)
                mDownloadTask.attach(this);
            if (mParserTask != null)
                mParserTask.attach(this);
            mGotData = save.gotData;
            save = null;
            if (mMangaList == null || mMangaList.length == 0)
                return;
            mListview.setAdapter(new AllMangaAdapter(AllMangaActivity.this));
            mListview.setOnItemClickListener(new AllMangaOnClickListener());
            return;
        }

        super.logEvent("Browse All Manga", null);

        long lastupdate = Mango.LAST_CACHE_UPDATE;
        boolean freshCache = false;
        freshCache = (System.currentTimeMillis() - lastupdate < 1000 * 60 * 60 * 24); // one
        // day

        if ((MangoCache.checkCacheForData("allmangalist_" + Mango.getSiteId() + ".xml") && (Mango.MANGA_LIST_CACHED || freshCache)))
        {
            showDialog(0);
            callback("", true);
        }
        else
            initializeMangaList();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance()
    {
        InstanceBundle save = new InstanceBundle();
        save.mangaList = mMangaList;
        save.downloadTask = mDownloadTask;
        save.parserTask = mParserTask;
        if (mDownloadTask != null)
            mDownloadTask.detach();
        if (mParserTask != null)
            mParserTask.detach();
        save.gotData = mGotData;
        mMangaList = null;
        return save;
    }

    @Override
    public void onSaveInstanceState(Bundle save)
    {
        super.onSaveInstanceState(save);
        save.putBoolean("gotdata", mGotData);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);
        if (mSkipRestore)
            return;
        mGotData = savedInstanceState.getBoolean("gotdata");
        mMangaList = (Manga[]) savedInstanceState.get("mangalist");
        if (mMangaList == null || mMangaList.length == 0)
        {
            if (mGotData)
            {
                showDialog(0);
                callback("", true);
                return;
            }
            else
                return;
        }
        mListview.setAdapter(new AllMangaAdapter(AllMangaActivity.this));
        mListview.setOnItemClickListener(new AllMangaOnClickListener());
    }

    @Override
    public void onDestroy()
    {
        mFindTextbox.removeTextChangedListener(mTextfilter);
        super.onDestroy();
    }

    @Override
    public void onPause()
    {
        if (mDownloadTask != null)
            mDownloadTask.detach();
        if (mParserTask != null)
            mParserTask.detach();
        super.onPause();
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        if (Mango.DIALOG_DOWNLOADING != null)
        {
            Mango.DIALOG_DOWNLOADING.dismiss();
            removeDialog(0);
            removeDialog(1);
        }

        if (id == 0)
        {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle("Downloading data...");
            dialog.setMessage("Retrieving the manga list from the Mango Service...");
            dialog.setIndeterminate(true);
            dialog.setCancelable(true);
            Mango.DIALOG_DOWNLOADING = dialog;
            return dialog;
        }
        if (id == 1)
        {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle("Processing data...");
            dialog.setMessage("Hang tight for just a bit...");
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            Mango.DIALOG_DOWNLOADING = dialog;
            return dialog;
        }
        return super.onCreateDialog(id);
    }

    public void initializeMangaList()
    {
        showDialog(0);
        mMangaList = new Manga[0];
        mDownloadTask = new XmlDownloader(this);
        mDownloadTask.execute("http://%SERVER_URL%/getserieslist.aspx?pin=" + Mango.getPin() + "&site=" + Mango.getSiteId());
    }

    private void callback(final String data, final boolean save)
    {
        if (data.startsWith("Exception"))
        {
            Mango.DIALOG_DOWNLOADING.dismiss();
            removeDialog(0);
            Mango.alert("Sorry, Mango wasn't able to load the requested data.  :'(\n\nTry again in a moment, or switch to another manga source.\n\n" + data, "Connectivity Problem! T__T", this);
            mListview.setAdapter(new ArrayAdapter<String>(AllMangaActivity.this, android.R.layout.simple_list_item_1, new String[]{
                    "Download failed! Press the back key and try again."}));
            return;
        }
        if (data.startsWith("error"))
        {
            Mango.DIALOG_DOWNLOADING.dismiss();
            removeDialog(0);
            Mango.alert("The Mango Service gave the following error:\n\n" + data, "Problem! T__T", this);
            mListview.setAdapter(new ArrayAdapter<String>(AllMangaActivity.this, android.R.layout.simple_list_item_1, new String[]{
                    "Download failed! Press the back key and try again."}));
            return;
        }
        if (!save)
        {
            Mango.getSharedPreferences().edit().putLong("lastAllMangaCacheUpdate", System.currentTimeMillis()).commit();
            Mango.LAST_CACHE_UPDATE = System.currentTimeMillis();
        }
        Mango.MANGA_LIST_CACHED = true;
        showDialog(1);
        mParserTask = new XmlParser(this);
        mParserTask.execute(new String[]{data,
                String.valueOf(save)});
    }

    private void parseCallback(Object data)
    {
        if (data != null && data.toString().contains("Exception"))
        {
            Mango.log("parseCallback: " + Log.getStackTraceString((Exception) data));
            Mango.getSharedPreferences().edit().putLong("lastAllMangaCacheUpdate", 0).commit();
            Mango.LAST_CACHE_UPDATE = 0;

            Mango.alert(
                    "Mango wasn't able to process the data.\n\n<strong>Possible Solutions:</strong>\n<small>-Go to Settings and Help >> Advanced >> Force Stop, then try again.\n-Check the 'Disable Menu Backgrounds' option in Preferences, then do a Force Stop and try again.\n-Power off and on your device, then try again.</small>",
                    AllMangaActivity.this);
            mListview.setAdapter(new ArrayAdapter<String>(AllMangaActivity.this, android.R.layout.simple_list_item_1, new String[]{
                    "Download failed! Press the back key and try again."}));
            return;
        }
        mListview.setAdapter(new AllMangaAdapter(this));
        mListview.setOnItemClickListener(new AllMangaOnClickListener());
        Mango.DIALOG_DOWNLOADING.dismiss();
        removeDialog(1);

        if (!Mango.getSharedPreferences().getBoolean("tutorial" + MangoTutorialHandler.MANGA_LIST + "Done", false))
            MangoTutorialHandler.startTutorial(MangoTutorialHandler.MANGA_LIST, this);

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mFindTextbox.getWindowToken(), 0);
    }

    private Manga[] parseXml(String data) throws Exception
    {
        if (data == null)
        {
            throw new Exception("parseXml: data parameter is null.  Try restarting the app.");
        }
        ArrayList<Manga> mangaArrayList = new ArrayList<Manga>();
        Favorite[] f = new Favorite[0];
        Manga random = new Manga();
        random.id = "randommanga";
        random.title = "Try a random manga!";
        random.completed = true;
        mangaArrayList.add(random);

        // connect to the database to set bookmarked status
        MangoSqlite db = new MangoSqlite(this);
        try
        {
            db.open();
            f = db.getAllFavorites(null);

            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            SAXParser parser = saxFactory.newSAXParser();
            XMLReader reader = parser.getXMLReader();
            AllMangaSaxHandler handler = new AllMangaSaxHandler();
            reader.setContentHandler(handler);
            reader.parse(new InputSource(new StringReader(data)));
            mangaArrayList.addAll(handler.getAllManga());
            handler.getAllManga().get(0).title.toString();
        } catch (Exception ex)
        {
            Mango.MANGA_LIST_CACHED = false;

            throw ex;
        } finally
        {
            db.close();
        }

        for (int i = 1; i < mangaArrayList.size(); i++)
        {
            Manga m = mangaArrayList.get(i);
            for (int j = 0; j < f.length; j++)
            {
                Favorite fav = f[j];
                try
                {
                    if (m.simpleName.equals(fav.mangaSimpleName))
                        m.bookmarked = true;
                    else if (m.id.equals(fav.mangaId))
                        m.bookmarked = true;
                    else if (m.title.equals(fav.mangaTitle))
                        m.bookmarked = true;
                } catch (Exception e)
                {
                    Mango.log("parseXml: " + e.toString() + " when setting bookmarked flag.");
                    m.bookmarked = false;
                }
                if (m.bookmarked)
                {
                    if (fav.coverArtUrl.length() < 15)
                    {
                        fav.coverArtUrl = m.coverart;
                        db.open();
                        db.updateFavorite(fav);
                        db.close();
                    }
                    m.favoriteRowId = fav.rowId;
                    break;
                }
            }
        }

        Manga[] ret = new Manga[mangaArrayList.size()];
        mangaArrayList.toArray(ret);
        mangaArrayList = null;
        return ret;
    }

    public class AllMangaSaxHandler extends DefaultHandler
    {
        ArrayList<Manga> allManga;
        Manga currentManga;

        public ArrayList<Manga> getAllManga()
        {
            return this.allManga;
        }

        @Override
        public void startDocument() throws SAXException
        {
            super.startDocument();
            allManga = new ArrayList<Manga>();
        }

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException
        {
            super.startElement(uri, localName, name, attributes);
            if (localName.equalsIgnoreCase("manga"))
            {
                this.currentManga = new Manga();
            }
            else if (localName.equalsIgnoreCase("title"))
            {
                currentManga.title = attributes.getValue(0);
                currentManga.generateSimpleName();
            }
            else if (localName.equalsIgnoreCase("url"))
            {
                currentManga.id = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("completed"))
            {
                currentManga.completed = Boolean.parseBoolean(attributes.getValue(0));
            }
            else if (localName.equalsIgnoreCase("cover"))
            {
                currentManga.coverart = attributes.getValue(0);
            }
        }

        @Override
        public void endElement(String uri, String localName, String name) throws SAXException
        {
            super.endElement(uri, localName, name);
            if (this.currentManga != null)
            {
                if (localName.equalsIgnoreCase("manga"))
                {
                    allManga.add(currentManga);
                }
            }
        }
    }

    class AllMangaOnClickListener implements OnItemClickListener
    {

        @Override
        public void onItemClick(AdapterView<?> parent, View v, final int position, long id)
        {
            if (!mGotData)
                return;
            if (position == 0)
            {
                Random random = new Random(System.currentTimeMillis());
                random.nextInt(mListview.getAdapter().getCount() - 1);
                onItemClick(parent, v, random.nextInt(mListview.getAdapter().getCount() - 1), id);
                return;
            }

            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mFindTextbox.getWindowToken(), 0);

            Intent chaptersIntent = new Intent();
            chaptersIntent.setClass(Mango.CONTEXT, ChaptersActivity.class);
            Manga argManga = new Manga();
            argManga.bookmarked = getManga(position).bookmarked;
            argManga.id = getManga(position).id;
            argManga.title = getManga(position).title;
            chaptersIntent.putExtra("manga", argManga);
            startActivity(chaptersIntent);
            return;
        }
    }

    private Manga getManga(int position)
    {
        return ((AllMangaAdapter) mListview.getAdapter()).getItem(position);
    }

    class ViewHolder
    {
        TextView text;
        ImageView icon;
        ImageView star;
    }

    class AllMangaAdapter extends ArrayAdapter<Manga> implements SectionIndexer
    {
        HashMap<String, Integer> mAlphaIndexer;
        String[] mSections;
        LayoutInflater mInflater;
        Bitmap mIcon;

        public AllMangaAdapter(FragmentActivity context)
        {
            super(context, R.layout.iconlistrow, mMangaList);
            mInflater = context.getLayoutInflater();
            mAlphaIndexer = new HashMap<String, Integer>();

            int size = mMangaList.length;
            for (int i = size - 1; i >= 0; i--)
            {
                String element = mMangaList[i].title;
                mAlphaIndexer.put(element.substring(0, (element.length() > 1 ? 2 : 1)), i);
            }

            Set<String> keys = mAlphaIndexer.keySet();
            Iterator<String> it = keys.iterator();
            ArrayList<String> keyList = new ArrayList<String>();

            while (it.hasNext())
            {
                String key = it.next();
                keyList.add(key);
            }

            Collections.sort(keyList, String.CASE_INSENSITIVE_ORDER);

            mSections = new String[keyList.size()];
            keyList.toArray(mSections);

            mIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_book_closed);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent)
        {
            ViewHolder holder;
            if (convertView == null)
            {
                convertView = mInflater.inflate(R.layout.iconlistrow, null);
                holder = new ViewHolder();
                holder.text = (TextView) convertView.findViewById(R.id.label);
                holder.icon = (ImageView) convertView.findViewById(R.id.icon);
                holder.star = (ImageView) convertView.findViewById(R.id.star);
                holder.icon.setImageBitmap(mIcon);
                convertView.setTag(holder);
            }
            else
            {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.text.setText(getManga(position).title);
            final ViewHolder vh = holder;
            holder.star.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    MangoSqlite db = new MangoSqlite(AllMangaActivity.this);
                    try
                    {
                        db.open();
                        if (getManga(position).bookmarked)
                        {
                            db.deleteFavorite(db.getFavoriteForManga(getManga(position)).rowId);

                            if (!Mango.getSharedPreferences().getBoolean("popupFavoriteRemoved", false))
                                Mango.alert(getManga(position).title + " has been removed from your favorites.", "Favorite removed!", AllMangaActivity.this);
                            Mango.getSharedPreferences().edit().putBoolean("popupFavoriteRemoved", true).commit();
                            vh.star.setImageResource(android.R.drawable.btn_star_big_off);
                        }
                        else
                        {
                            Favorite f = new Favorite();
                            Manga m = getManga(position);
                            f.isOngoing = !m.completed;
                            f.mangaId = m.id;
                            f.mangaTitle = m.title;
                            f.mangaSimpleName = m.simpleName;
                            f.coverArtUrl = m.coverart;
                            f.notificationsEnabled = false;
                            f.siteId = Mango.getSiteId();
                            db.insertFavorite(f);

                            if (!Mango.getSharedPreferences().getBoolean("popupFavoriteAdded", false))
                                Mango.alert(getManga(position).title + " has been favorited! Mango will now track your reading progress in the Favorites screen.", "Favorite added!",
                                        AllMangaActivity.this);
                            Mango.getSharedPreferences().edit().putBoolean("popupFavoriteAdded", true).commit();
                            vh.star.setImageResource(android.R.drawable.btn_star_big_on);
                        }
                        getManga(position).bookmarked = !getManga(position).bookmarked;
                    } catch (SQLException ex)
                    {

                    } finally
                    {
                        db.close();
                    }
                }
            });
            if (position == 0)
                holder.star.setVisibility(View.INVISIBLE);
            else
                holder.star.setVisibility(View.VISIBLE);
            if (getManga(position).bookmarked)
                holder.star.setImageResource(android.R.drawable.btn_star_big_on);
            else
                holder.star.setImageResource(android.R.drawable.btn_star_big_off);
            if (getManga(position).completed)
                holder.icon.setImageResource(R.drawable.ic_book_closed);
            else
                holder.icon.setImageResource(R.drawable.ic_book_open);
            return convertView;
        }

        public void showStar(ViewHolder holder)
        {
            holder.star.setImageResource(android.R.drawable.btn_star_big_on);
        }

        @Override
        public int getPositionForSection(int section)
        {
            String letter = mSections[section];
            return mAlphaIndexer.get(letter);
        }

        @Override
        public int getSectionForPosition(int position)
        {
            return 0;
        }

        @Override
        public Object[] getSections()
        {
            return mSections;
        }
    }

    private class XmlDownloader extends AsyncTask<String, Void, String>
    {
        AllMangaActivity activity = null;

        public XmlDownloader(AllMangaActivity activity)
        {
            attach(activity);
        }

        @Override
        protected String doInBackground(String... params)
        {
            Mango.log("doInBackground " + params[0]);
            return MangoHttp.downloadData(params[0], activity);
        }

        @Override
        protected void onPostExecute(String data)
        {
            if (activity == null)
            {
                Mango.DIALOG_DOWNLOADING.dismiss();
                Mango.log("AsyncTask skipped onPostExecute because no activity is attached!");
            }
            else
            {
                activity.callback(data, false);
            }
        }

        void detach()
        {
            activity = null;
        }

        void attach(AllMangaActivity activity)
        {
            this.activity = activity;
        }
    }

    private class XmlParser extends AsyncTask<String, Void, Object>
    {
        AllMangaActivity activity = null;

        public XmlParser(AllMangaActivity activity)
        {
            attach(activity);
        }

        @Override
        protected Object doInBackground(String... params)
        {
            String data = params[0];
            boolean save = (params[1].equals("true")) ? true : false;
            if (save)
            {
                try
                {
                    return parseXml(MangoCache.readDataFromCache("allmangalist_" + Mango.getSiteId() + ".xml"));
                } catch (Exception ex)
                {
                    return ex;
                }
            }
            MangoCache.writeDataToCache(data, "allmangalist_" + Mango.getSiteId() + ".xml");
            try
            {
                return parseXml(data);
            } catch (Exception ex)
            {
                return ex;
            }
        }

        @Override
        protected void onPostExecute(Object data)
        {
            if (activity == null)
            {
                Mango.DIALOG_DOWNLOADING.dismiss();
                Mango.log("AsyncTask skipped onPostExecute because no activity is attached!");
            }
            else
            {
                if (!data.getClass().toString().contains("Exception"))
                {
                    activity.mGotData = true;
                    activity.mMangaList = (Manga[]) data;
                    activity.parseCallback(null);
                }
                activity.parseCallback(data);
            }
        }

        void detach()
        {
            activity = null;
        }

        void attach(AllMangaActivity activity)
        {
            this.activity = activity;
        }
    }

    public View getTutorialHighlightView(int index)
    {
        if (index == -1)
        {
            return mFindTextbox;
        }
        if (index == 1)
        {
            return mListview.getChildAt(index).findViewById(R.id.star);
        }
        return mListview.getChildAt(index);
    }
}
