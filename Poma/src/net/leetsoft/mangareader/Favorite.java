package net.leetsoft.mangareader;

import android.app.Activity;
import android.os.AsyncTask;
import net.leetsoft.mangareader.activities.ChaptersActivity.ChaptersSaxHandler;
import net.leetsoft.mangareader.activities.FavoritesActivity;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;

public class Favorite implements Serializable
{
    /**
     *
     */
    private static final long serialVersionUID = 5562848122485891927L;
    public String mangaTitle;
    public String mangaAltTitles;
    public String mangaSimpleName;
    public String mangaId;

    public String progressChapterId;
    public String progressChapterName;
    public String progressChapterUrl;
    public int progressChapterIndex;
    public int progressPageIndex;

    public long lastChapterTime;
    public String lastChapterId;
    public String lastChapterName;
    public String lastChapterUrl;
    public int lastChapterIndex;
    public boolean newChapterAvailable;

    public long readDate;

    public int siteId;

    public int tagId;
    public String coverArtUrl = "";
    public boolean isOngoing;
    public boolean notificationsEnabled;

    public transient boolean savedInLibrary;
    public transient boolean resumeFromLibrary;
    public transient int rowId;
    public transient Manga mangaObject;
    private transient Activity mReference;

    public void generateSimpleName()
    {
        mangaSimpleName = mangaTitle.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public boolean compareTo(Favorite f)
    {
        if (f.mangaTitle.equalsIgnoreCase(this.mangaTitle))
            return true;

        if (f.mangaId.equalsIgnoreCase(this.mangaId))
            return true;

        // if (f.mangaAltTitles.toLowerCase().contains(this.mangaAltTitles.toLowerCase()))
        // return true;

        if (f.mangaSimpleName.equalsIgnoreCase(this.mangaSimpleName))
            return true;

        return false;
    }

    public void buildManga(Activity ref)
    {
        mReference = ref;
        mangaObject = new Manga();
        mangaObject.title = mangaTitle;
        mangaObject.id = mangaId;
        mangaObject.generateSimpleName();
        mangaObject.coverart = coverArtUrl;
        mangaObject.chapters = new Chapter[progressChapterIndex + 1];
        mangaObject.chapters[progressChapterIndex] = new Chapter();
        mangaObject.chapters[progressChapterIndex].id = progressChapterId;
        mangaObject.chapters[progressChapterIndex].title = progressChapterName;
        mangaObject.chapters[progressChapterIndex].url = progressChapterUrl;

        ChapterDownloader dl = new ChapterDownloader(this);
        dl.execute("http://%SERVER_URL%/getchapterlist.aspx?pin=" + Mango.getPin() + "&url=" + mangaId + "&site=" + Mango.getSiteId());
    }

    private void callback(String data)
    {
        if (data.startsWith("Exception") || data.startsWith("error"))
        {
            data = "Mango wasn't able to download more information about this favorite. If your phone's 3G/4G/WiFi connection is fine, " + Mango.getSiteName(Mango.getSiteId()) + " might be down.\n\n"
                    + data;
            ((FavoritesActivity) mReference).pendingItemFailed(data);
            return;
        }

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
            if (chapterArrayList.size() == 0)
                throw new Exception("Mango Service returned an empty chapter list.");
            mangaObject.details = handler.getDetails();
        } catch (Exception ex)
        {
            data = "Mango received invalid data about this favorite from the server. If you phone's 3G/4G/WiFi connection is fine, " + Mango.getSiteName(Mango.getSiteId()) + " might be down.\n\n"
                    + data;
            ((FavoritesActivity) mReference).pendingItemFailed(data);
            return;
        }

        mangaObject.chapters = new Chapter[chapterArrayList.size()];
        chapterArrayList.toArray(mangaObject.chapters);
        chapterArrayList = null;

        ((FavoritesActivity) mReference).loadPendingFavorite(this);
    }

    private class ChapterDownloader extends AsyncTask<String, Void, String>
    {
        Favorite bmRef;

        ChapterDownloader(Favorite ref)
        {
            bmRef = ref;
        }

        @Override
        protected String doInBackground(String... params)
        {
            return MangoHttp.downloadHtml(params[0], bmRef.mReference);
        }

        @Override
        protected void onPostExecute(String data)
        {
            bmRef.callback(data);
            bmRef = null;
        }
    }
}
