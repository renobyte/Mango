package net.leetsoft.mangareader.activities;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import net.leetsoft.mangareader.*;
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
import java.util.HashMap;

public class SearchActivity extends MangoActivity
{
    private SearchAdapter mAdapter;
    private Genre[] mGenreList;
    private int[] mGenreStatus;
    private boolean mGotData = false;
    private XmlDownloader mDownloadTask;

    private ListView mListview;
    private EditText mSearchBox;
    private Button mSearchButton;
    private CheckBox mSummariesCheck;
    private TextView mSearchHelp;

    private class InstanceBundle
    {
        private Genre[] genreList;
        private int[] genreStatus;
        private boolean gotData;
        private XmlDownloader downloadTask;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        this.setTitle("Search", Mango.getSiteName(Mango.getSiteId()));
        inflateLayoutManager(this, R.layout.search);
        mListview = (ListView) findViewById(R.id.searchGenreList);
        mSearchBox = (EditText) findViewById(R.id.searchCriteria);
        mSummariesCheck = (CheckBox) findViewById(R.id.searchSummaries);
        mSearchButton = (Button) findViewById(R.id.searchButton);
        mSearchHelp = (TextView) findViewById(R.id.searchHelpLabel);
        super.setJpBackground(R.drawable.jp_bg_search);
        super.setJpVerticalOffsetView(mSearchHelp);

        mSearchButton.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                searchClicked();
            }
        });

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
        {
            mSearchHelp.setVisibility(View.GONE);
            super.setJpVerticalOffsetView(mSummariesCheck);
        }

        if (getLastCustomNonConfigurationInstance() != null && ((InstanceBundle) getLastCustomNonConfigurationInstance()).genreList != null)
        {
            InstanceBundle save = (InstanceBundle) getLastCustomNonConfigurationInstance();
            mGenreList = save.genreList;
            mGenreStatus = save.genreStatus;
            mDownloadTask = save.downloadTask;
            if (mDownloadTask != null)
                mDownloadTask.attach(this);
            mGotData = save.gotData;
            save = null;
            if (mGenreList == null || mGenreList.length == 0)
                return;
            mAdapter = new SearchAdapter(this);
            mListview.setAdapter(mAdapter);
            mListview.setOnItemClickListener(new GenreOnClickListener());
            return;
        }

        initializeGenreList();
    }

    private void searchClicked()
    {
        if (Mango.getSharedPreferences().getLong("searchCooldown", 0) > System.currentTimeMillis())
        {
            long cooldown = (Mango.getSharedPreferences().getLong("searchCooldown", 0) - System.currentTimeMillis()) / 1000;
            Mango.alert("Please wait " + cooldown + " seconds before trying another search.", "Search Flood Cooldown", this);
            return;
        }
        String queryString = "";
        String includeStr = "";
        String excludeStr = "";
        queryString = "qry=" + mSearchBox.getText() + "&";
        for (int i = 0; i < mGenreStatus.length; i++)
        {
            if (mGenreStatus[i] == 1)
                includeStr += mGenreList[i].id + ",";
            if (mGenreStatus[i] == 2)
                excludeStr += mGenreList[i].id + ",";
        }
        queryString += "i=" + includeStr + "&e=" + excludeStr;
        if (mSummariesCheck.isChecked())
            queryString += "&s=1";
        queryString = queryString.replace(" ", "%20");
        Intent intent = new Intent();
        intent.setClass(Mango.CONTEXT, SearchResultsActivity.class);
        intent.putExtra("querystring", queryString);
        startActivity(intent);
        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("Used Genres", String.valueOf((includeStr.length() != 0 || excludeStr.length() != 0)));
        parameters.put("Used Criteria", String.valueOf((mSearchBox.getText().length() != 0)));
        parameters.put("Include Summaries", String.valueOf(mSummariesCheck.isChecked()));
        super.logEvent("Perform Search", parameters);
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
            dialog.setMessage("Retrieving genre list from the server...");
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
        save.genreList = mGenreList;
        save.genreStatus = mGenreStatus;
        save.gotData = mGotData;
        save.downloadTask = mDownloadTask;
        if (mDownloadTask != null)
            mDownloadTask.detach();
        return save;
    }

    public void initializeGenreList()
    {
        showDialog(0);
        mGenreList = new Genre[0];
        mDownloadTask = new XmlDownloader(this);
        mDownloadTask.execute("http://%SERVER_URL%/getgenrelist.aspx?pin=" + Mango.getPin() + "&site=" + Mango.getSiteId());
    }

    private void callback(final MangoHttpResponse data, final boolean save)
    {
        Mango.DIALOG_DOWNLOADING.dismiss();
        removeDialog(0);
        if (data.exception)
        {
            Mango.alert("Mango couldn't download the genre list. You might still be able to use the search feature without picking genres, though.\n\n" + data, "Genre Download Failed", this);
            mListview.setAdapter(new ArrayAdapter<String>(SearchActivity.this, android.R.layout.simple_list_item_1, new String[]{"Genre list not available."}));
            mGenreList = new Genre[0];
            mGenreStatus = new int[0];
            return;
        }
        if (data.toString().startsWith("error"))
        {
            mListview.setAdapter(new ArrayAdapter<String>(SearchActivity.this, android.R.layout.simple_list_item_1, new String[]{"Genre list not available."}));
            mGenreList = new Genre[0];
            mGenreStatus = new int[0];
            return;
        }
        parseXml(data.toString());
    }

    private void parseXml(String data)
    {
        ArrayList<Genre> genreArrayList = new ArrayList<Genre>();

        try
        {
            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            SAXParser parser = saxFactory.newSAXParser();
            XMLReader reader = parser.getXMLReader();
            GenreSaxHandler handler = new GenreSaxHandler();
            reader.setContentHandler(handler);
            reader.parse(new InputSource(new StringReader(data)));
            genreArrayList.addAll(handler.getAllGenres());
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

        mGenreList = new Genre[genreArrayList.size()];
        mGenreStatus = new int[genreArrayList.size()];
        genreArrayList.toArray(mGenreList);
        genreArrayList = null;

        mAdapter = new SearchAdapter(this);
        mListview.setAdapter(mAdapter);
        mListview.setOnItemClickListener(new GenreOnClickListener());
    }

    public class GenreSaxHandler extends DefaultHandler
    {
        ArrayList<Genre> allGenres;
        Genre currentGenre;

        public ArrayList<Genre> getAllGenres()
        {
            return this.allGenres;
        }

        @Override
        public void startDocument() throws SAXException
        {
            super.startDocument();
            allGenres = new ArrayList<Genre>();
        }

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException
        {
            super.startElement(uri, localName, name, attributes);
            if (localName.equalsIgnoreCase("genre"))
            {
                this.currentGenre = new Genre();
            }
            else if (localName.equalsIgnoreCase("id"))
            {
                currentGenre.id = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("name"))
            {
                currentGenre.name = attributes.getValue(0);
            }
        }

        @Override
        public void endElement(String uri, String localName, String name) throws SAXException
        {
            super.endElement(uri, localName, name);
            if (this.currentGenre != null)
            {
                if (localName.equalsIgnoreCase("genre"))
                {
                    allGenres.add(currentGenre);
                }
            }
        }
    }

    class GenreOnClickListener implements OnItemClickListener
    {

        @Override
        public void onItemClick(AdapterView<?> parent, View v, final int position, long id)
        {
            if (!mGotData)
                return;

            mGenreStatus[position]++;
            if (mGenreStatus[position] == 3)
                mGenreStatus[position] = 0;

            mAdapter.notifyDataSetChanged();
        }
    }

    class ViewHolder
    {
        TextView text;
        ImageView icon;
        ImageView star;
    }

    class SearchAdapter extends ArrayAdapter<Genre>
    {
        LayoutInflater mInflater;
        Bitmap mIconCross;
        Bitmap mIconCheck;

        public SearchAdapter(Activity context)
        {
            super(context, R.layout.iconlistrow, mGenreList);
            mInflater = context.getLayoutInflater();
            mIconCheck = BitmapFactory.decodeResource(getResources(), R.drawable.ic_include);
            mIconCross = BitmapFactory.decodeResource(getResources(), R.drawable.ic_delete);
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
                holder.star.setVisibility(View.GONE);
                convertView.setTag(holder);
            }
            else
            {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.text.setText(mGenreList[position].name);
            if (mGenreStatus[position] != 0)
            {
                holder.icon.setVisibility(View.VISIBLE);
                if (mGenreStatus[position] == 1)
                    holder.icon.setImageBitmap(mIconCheck);
                else
                    holder.icon.setImageBitmap(mIconCross);
            }
            else
                holder.icon.setVisibility(View.INVISIBLE);
            return convertView;
        }
    }

    private class XmlDownloader extends AsyncTask<String, Void, MangoHttpResponse>
    {
        SearchActivity activity = null;

        public XmlDownloader(SearchActivity activity)
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

        void attach(SearchActivity activity)
        {
            this.activity = activity;
        }
    }
}