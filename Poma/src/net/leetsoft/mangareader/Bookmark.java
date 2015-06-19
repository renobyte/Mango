package net.leetsoft.mangareader;

import android.app.Activity;
import android.os.AsyncTask;
import net.leetsoft.mangareader.activities.ChaptersActivity.ChaptersSaxHandler;
import net.leetsoft.mangareader.activities.HistoryActivity;
import net.leetsoft.mangareader.activities.NewReleasesActivity;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.ArrayList;

public class Bookmark
{
    public static final int MANGA = 0;
    public static final int CHAPTER = 1;
    public static final int PAGE = 2;
    public static final int RECENT = 3;
    public static final int PAGE_LOCAL = 4;
    public static final int RELEASE = 5;
    public String mangaId;
    public String mangaName;
    public int chapterIndex;
    public String chapterName;
    public String chapterId;
    public String chapterUrl;
    public int chapterCount;
    public int pageIndex;
    public String pageId;
    public int bookmarkType;
    public long updateTime;
    public int siteId;
    public Manga manga;
    public long rowId;
    private Activity mReference;

    public void buildManga(Activity ref)
    {
        mReference = ref;
        Manga newmanga = new Manga();
        newmanga.title = mangaName;
        newmanga.id = mangaId;
        if (bookmarkType == Bookmark.CHAPTER || bookmarkType == Bookmark.PAGE || bookmarkType == Bookmark.RECENT || bookmarkType == Bookmark.RELEASE)
        {
            newmanga.chapters = new Chapter[chapterCount];
            newmanga.chapters[chapterIndex] = new Chapter();
            newmanga.chapters[chapterIndex].id = chapterId;
            newmanga.chapters[chapterIndex].title = chapterName;
            manga = newmanga;
            ChapterDownloader dl = new ChapterDownloader(this);
            dl.execute("http://%SERVER_URL%/getchapterlist.aspx?pin=" + Mango.getPin() + "&url=" + newmanga.id + "&site=" + Mango.getSiteId());
        }
    }

    private void callback(MangoHttpResponse data)
    {
        if (data.exception || data.toString().startsWith("error"))
        {
            data.data = ("Mango wasn't able to retrieve metadata for this favorite.  If your device's mobile data or Wi-Fi connection is fine, " + Mango.getSiteName(Mango.getSiteId()) + " might be down.\n\n" + data).getBytes();
            if (bookmarkType == Bookmark.RELEASE)
                ((NewReleasesActivity) mReference).pendingItemFailed(data);
            else
                ((HistoryActivity) mReference).pendingItemFailed(data);
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
            reader.parse(new InputSource(new StringReader(data.toString())));
            chapterArrayList.addAll(handler.getAllChapters());
            if (chapterArrayList.size() == 0)
                throw new Exception("Server returned an empty chapter list.");
            manga.details = handler.getDetails();
        }
        catch (Exception ex)
        {
            data.data = ("Mango received invalid data about this favorite from the server.  If your device's mobile data or Wi-Fi connection is fine, "
                    + Mango.getSiteName(Mango.getSiteId()) + " might be down.\n\n" + data).getBytes();
            if (bookmarkType == Bookmark.RELEASE)
                ((NewReleasesActivity) mReference).pendingItemFailed(data);
            else
                ((HistoryActivity) mReference).pendingItemFailed(data);
            return;
        }

        manga.chapters = new Chapter[chapterArrayList.size()];
        chapterArrayList.toArray(manga.chapters);
        chapterArrayList = null;

        if (bookmarkType == Bookmark.RELEASE)
            ((NewReleasesActivity) mReference).loadPendingBookmark(this);
        else
            ((HistoryActivity) mReference).loadPendingBookmark(this);
    }

    private class ChapterDownloader extends AsyncTask<String, Void, MangoHttpResponse>
    {
        Bookmark bmRef;

        ChapterDownloader(Bookmark ref)
        {
            bmRef = ref;
        }

        @Override
        protected MangoHttpResponse doInBackground(String... params)
        {
            return MangoHttp.downloadData(params[0], bmRef.mReference);
        }

        @Override
        protected void onPostExecute(MangoHttpResponse data)
        {
            bmRef.callback(data);
            bmRef = null;
        }
    }
}
