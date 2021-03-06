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

public class GenreActivity extends MangoActivity
{
    private Genre[] mGenreList;
    private boolean mGotData = false;
    private XmlDownloader mDownloadTask;

    private ListView mListview;

    private class InstanceBundle
    {
        private Genre[] genreList;
        private boolean gotData;
        private XmlDownloader downloadTask;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        this.setTitle("Genres", Mango.getSiteName(Mango.getSiteId()));
        inflateLayoutManager(this, R.layout.mainmenu);
        mListview = (ListView) findViewById(R.id.MainMenuList);
        super.setAdLayout((MangoAdWrapperView) findViewById(R.id.mainmenuAdLayout));
        super.setJpBackground(R.drawable.jp_bg_genre);

        if (getLastCustomNonConfigurationInstance() != null && ((InstanceBundle) getLastCustomNonConfigurationInstance()).genreList != null)
        {
            InstanceBundle save = (InstanceBundle) getLastCustomNonConfigurationInstance();
            mGenreList = save.genreList;
            mDownloadTask = save.downloadTask;
            if (mDownloadTask != null)
                mDownloadTask.attach(this);
            mGotData = save.gotData;
            save = null;
            if (mGenreList == null || mGenreList.length == 0)
                return;
            mListview.setAdapter(new GenreAdapter(GenreActivity.this));
            mListview.setOnItemClickListener(new GenreOnClickListener());
            return;
        }

        super.logEvent("Browse by Genre", null);

        initializeGenreList();
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
            dialog.setMessage("Retrieving the genre list from the server...");
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
        genreArrayList.toArray(mGenreList);
        genreArrayList = null;

        mListview.setAdapter(new GenreAdapter(this));
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
                    if (!(Mango.getSharedPreferences().getBoolean("ecchiFilter", false) && (currentGenre.name.equalsIgnoreCase("ecchi") || currentGenre.name.equalsIgnoreCase("adult") || currentGenre.name.equalsIgnoreCase("mature"))))
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

            Intent mangaByGenreIntent = new Intent();
            mangaByGenreIntent.setClass(Mango.CONTEXT, FilteredMangaActivity.class);
            Genre argGenre = new Genre();
            argGenre.id = getGenre(position).id;
            argGenre.name = getGenre(position).name;
            mangaByGenreIntent.putExtra("mode", FilteredMangaActivity.MODE_GENRE);
            mangaByGenreIntent.putExtra("argument", argGenre);
            startActivity(mangaByGenreIntent);
        }
    }

    private Genre getGenre(int position)
    {
        return ((GenreAdapter) mListview.getAdapter()).getItem(position);
    }

    class ViewHolder
    {
        TextView text;
        ImageView icon;
        ImageView star;
    }

    class GenreAdapter extends ArrayAdapter<Genre>
    {
        LayoutInflater mInflater;
        Bitmap mIcon;

        public GenreAdapter(Activity context)
        {
            super(context, R.layout.iconlistrow, mGenreList);
            mInflater = context.getLayoutInflater();
            mIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_genre);
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

            holder.text.setText(getGenre(position).name);
            return convertView;
        }
    }

    private class XmlDownloader extends AsyncTask<String, Void, MangoHttpResponse>
    {
        GenreActivity activity = null;

        public XmlDownloader(GenreActivity activity)
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

        void attach(GenreActivity activity)
        {
            this.activity = activity;
        }
    }
}