package net.leetsoft.mangareader.activities;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CompoundButton.OnCheckedChangeListener;
import net.leetsoft.mangareader.*;
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
import java.util.regex.Pattern;

public class NewReleasesActivity extends MangoActivity
{
    private NewRelease[] mReleaseList;
    private NewRelease[] mUnfilteredReleaseList;
    private boolean mGotData = false;
    private XmlDownloader mDownloadTask;

    private ListView mListview;
    private CheckBox mCheckbox;

    private class InstanceBundle
    {
        private NewRelease[] unfilteredReleaseList;
        private NewRelease[] releaseList;
        private boolean gotData;
        private XmlDownloader downloadTask;
    }

    private class NewRelease
    {
        private Manga manga;
        private String chapterId;
        private String chapterName;
        private String chapterUrl;
        private String postDate;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        this.setTitle("Newest Releases", Mango.getSiteName(Mango.getSiteId()));
        inflateLayoutManager(this, R.layout.newreleases);
        mListview = (ListView) findViewById(R.id.newreleasesList);
        mCheckbox = (CheckBox) findViewById(R.id.newreleasesBookmarkCheck);
        mCheckbox.setOnCheckedChangeListener(new OnCheckedChangeListener()
        {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                mListview.setAdapter(new ReleasesAdapter(NewReleasesActivity.this));
            }
        });
        super.setAdLayout((MangoAdWrapperView) findViewById(R.id.newreleasesAdLayout));
        super.setJpBackground(R.drawable.jp_bg_newest);

        if (getLastCustomNonConfigurationInstance() != null && ((InstanceBundle) getLastCustomNonConfigurationInstance()).releaseList != null)
        {
            InstanceBundle save = (InstanceBundle) getLastCustomNonConfigurationInstance();
            mUnfilteredReleaseList = save.unfilteredReleaseList;
            mReleaseList = save.releaseList;
            mDownloadTask = save.downloadTask;
            if (mDownloadTask != null)
                mDownloadTask.attach(this);
            mGotData = save.gotData;
            save = null;
            if (mReleaseList == null || mReleaseList.length == 0)
                return;
            mListview.setAdapter(new ReleasesAdapter(NewReleasesActivity.this));
            mListview.setOnItemClickListener(new ReleasesOnClickListener());
            return;
        }

        super.logEvent("Browse New Releases", null);
        initializeReleasesList();
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
            dialog.setTitle("Downloading data...");
            dialog.setMessage("Retrieving the newest releases list from the server...");
            dialog.setIndeterminate(true);
            dialog.setCancelable(true);
            Mango.DIALOG_DOWNLOADING = dialog;
            return dialog;
        }
        return super.onCreateDialog(id);
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance()
    {
        InstanceBundle save = new InstanceBundle();
        save.unfilteredReleaseList = mUnfilteredReleaseList;
        save.releaseList = mReleaseList;
        save.gotData = mGotData;
        save.downloadTask = mDownloadTask;
        if (mDownloadTask != null)
            mDownloadTask.detach();
        return save;
    }

    public void initializeReleasesList()
    {
        showDialog(0);
        mReleaseList = new NewRelease[0];
        mDownloadTask = new XmlDownloader(this);
        mDownloadTask.execute("http://%SERVER_URL%/getrecentupdates.aspx?pin=" + Mango.getPin() + "&site=" + Mango.getSiteId());
    }

    private void callback(final MangoHttpResponse data, final boolean save)
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

    private void parseXml(String data)
    {
        ArrayList<NewRelease> recentArrayList = new ArrayList<NewRelease>();

        try
        {
            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            SAXParser parser = saxFactory.newSAXParser();
            XMLReader reader = parser.getXMLReader();
            ReleasesSaxHandler handler = new ReleasesSaxHandler();
            reader.setContentHandler(handler);
            reader.parse(new InputSource(new StringReader(data)));
            recentArrayList.addAll(handler.getAllReleases());
        } catch (SAXException ex)
        {
            Mango.alert("The server returned malformed XML.\n\n<strong>Error Details:</strong>\n" + data + ex.toString(), "Invalid Response", this);
            return;
        } catch (NullPointerException ex)
        {
            Mango.alert("Mango was unable to load the requested data.\n\n<strong>Error Details</strong>\n" + data, "Parse Failed", this);
            return;
        } catch (ParserConfigurationException e)
        {
        } catch (IOException e)
        {
        }

        mGotData = true;

        mUnfilteredReleaseList = new NewRelease[recentArrayList.size()];
        recentArrayList.toArray(mUnfilteredReleaseList);
        recentArrayList = null;

        Favorite[] f = new Favorite[0];

        new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                ArrayList<NewRelease> filteredList = new ArrayList<NewRelease>();

                MangoSqlite db = new MangoSqlite(NewReleasesActivity.this);
                db.open();
                Favorite[] f = db.getAllFavorites(null);
                db.close();

                Favorite temp = new Favorite();
                for (int i = 0; i < mUnfilteredReleaseList.length; i++)
                {
                    for (int j = 0; j < f.length; j++)
                    {
                        temp.mangaId = mUnfilteredReleaseList[i].manga.id;
                        temp.mangaTitle = mUnfilteredReleaseList[i].manga.title;
                        temp.mangaSimpleName = mUnfilteredReleaseList[i].manga.simpleName;
                        if (f[j].compareTo(temp))
                        {
                            filteredList.add(mUnfilteredReleaseList[i]);
                            break;
                        }
                    }
                }

                mReleaseList = new NewRelease[filteredList.size()];
                filteredList.toArray(mReleaseList);
                filteredList = null;

                mListview.post(new Runnable()
                {

                    @Override
                    public void run()
                    {
                        mListview.setAdapter(new ReleasesAdapter(NewReleasesActivity.this));
                    }
                });
            }
        }).start();

        mListview.setAdapter(new ReleasesAdapter(this));
        mListview.setOnItemClickListener(new ReleasesOnClickListener());
        if (getReleasesArray().length > 50)
            mListview.setFastScrollEnabled(true);
    }

    public class ReleasesSaxHandler extends DefaultHandler
    {
        ArrayList<NewRelease> allReleases;
        NewRelease currentRelease;
        Pattern p = Pattern.compile("[^a-z0-9]");

        public ArrayList<NewRelease> getAllReleases()
        {
            return this.allReleases;
        }

        @Override
        public void startDocument() throws SAXException
        {
            super.startDocument();
            allReleases = new ArrayList<NewRelease>();
        }

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException
        {
            super.startElement(uri, localName, name, attributes);
            if (localName.equalsIgnoreCase("recent"))
            {
                this.currentRelease = new NewRelease();
                this.currentRelease.manga = new Manga();
            }
            else if (localName.equalsIgnoreCase("mangaid"))
            {
                currentRelease.manga.id = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("title"))
            {
                currentRelease.manga.title = attributes.getValue(0);
                currentRelease.manga.generateSimpleName(p);
            }
            else if (localName.equalsIgnoreCase("date"))
            {
                currentRelease.postDate = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("chapterid"))
            {
                currentRelease.chapterId = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("chaptername"))
            {
                currentRelease.chapterName = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("chapterurl"))
            {
                currentRelease.chapterUrl = attributes.getValue(0);
            }
        }

        @Override
        public void endElement(String uri, String localName, String name) throws SAXException
        {
            super.endElement(uri, localName, name);
            if (this.currentRelease != null)
            {
                if (localName.equalsIgnoreCase("recent"))
                {
                    allReleases.add(currentRelease);
                }
            }
        }
    }

    class ReleasesOnClickListener implements OnItemClickListener
    {

        @Override
        public void onItemClick(AdapterView<?> parent, View v, final int position, long id)
        {
            if (!mGotData)
                return;
            Bookmark b = new Bookmark();
            b.bookmarkType = Bookmark.RELEASE;
            b.chapterCount = 999;
            b.chapterUrl = getReleasesArray()[position].chapterUrl;
            b.chapterId = getReleasesArray()[position].chapterId;
            b.chapterName = getReleasesArray()[position].chapterId;
            b.mangaId = getReleasesArray()[position].manga.id;
            b.mangaName = getReleasesArray()[position].manga.title;
            b.siteId = Mango.getSiteId();
            showDialog(0);
            b.buildManga(NewReleasesActivity.this);
            return;
        }
    }

    class ViewHolder
    {
        TextView text;
        ImageView icon;
        ImageView star;
    }

    private NewRelease[] getReleasesArray()
    {
        if (mCheckbox.isChecked())
        {
            if (mReleaseList != null)
                return mReleaseList;
            else
                return new NewRelease[0];
        }
        else
        {
            if (mUnfilteredReleaseList != null)
                return mUnfilteredReleaseList;
            else
                return new NewRelease[0];
        }

    }

    class ReleasesAdapter extends ArrayAdapter<NewRelease>
    {
        LayoutInflater mInflater;
        Bitmap mIcon;

        public ReleasesAdapter(Activity context)
        {
            super(context, R.layout.iconlistrow, getReleasesArray());
            mInflater = context.getLayoutInflater();
            mIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_book_recent);
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
                holder.star.setVisibility(View.GONE);
                convertView.setTag(holder);
            }
            else
            {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.text.setText(getReleasesArray()[position].manga.title + ": " + getReleasesArray()[position].chapterId + " (" + getReleasesArray()[position].postDate + ")");
            return convertView;
        }
    }

    private class XmlDownloader extends AsyncTask<String, Void, MangoHttpResponse>
    {
        NewReleasesActivity activity = null;

        public XmlDownloader(NewReleasesActivity activity)
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
                activity.callback(data, false);
            }
        }

        void detach()
        {
            activity = null;
        }

        void attach(NewReleasesActivity activity)
        {
            this.activity = activity;
        }
    }

    public void pendingItemFailed(MangoHttpResponse data)
    {
        Mango.DIALOG_DOWNLOADING.dismiss();
        Mango.alert(data.toString(), this);
    }

    public void loadPendingBookmark(Bookmark bookmark)
    {
        Mango.DIALOG_DOWNLOADING.dismiss();
        Intent prIntent = new Intent();
        prIntent.setClass(Mango.CONTEXT, PagereaderActivity.class);
        prIntent.putExtra("manga", bookmark.manga);
        prIntent.putExtra("chapterid", bookmark.chapterId);
        bookmark.pageIndex = 0;
        prIntent.putExtra("initialpage", bookmark.pageIndex);
        prIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(prIntent);
        overridePendingTransition(R.anim.fadein, R.anim.expandout);
    }
}