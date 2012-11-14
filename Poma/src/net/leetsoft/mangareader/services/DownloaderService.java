package net.leetsoft.mangareader.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.widget.RemoteViews;
import android.widget.Toast;
import net.leetsoft.mangareader.*;
import net.leetsoft.mangareader.activities.DownloaderActivity;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class DownloaderService extends Service
{
    public static int STATUS_INPROGRESS = 0;
    public static int STATUS_COOLDOWN = 1;
    public static int STATUS_QUEUED = 2;

    private LibraryChapter mTargetChapter;
    private int mCurrentPageIndex;
    private String mPageUrlPrefix;
    private Page[] mPages;
    private boolean[] mPageStatus;
    private int mChapterLength;
    private int mChapterHashCode;
    private String mSubstringStart;
    private String mSubstringAltStart;
    private int mPagelistRetries;

    // Async Variables
    private XmlDownloader mXmlTask;
    private ImageBackgroundDownloader mCurrentDownloader;

    // Queue variables
    private ArrayList<QueueItem> mDownloadQueue;
    private QueueItem mTargetItem;
    private int mCompletedPages;
    private Timer mCooldownTimer;

    // Status reporting variables
    private int mSuccessfulPages;                          // total successful pages
    private int mSuccessfulChapters;                       // total successful chapters
    private int mFailedPages;                              // failed pages, reset to 0
    private int mFailedChapters;                           // total failed chapters
    private int mPartialChapters;                          // chapters with missing pages
    private StringBuilder mReportText;                               // report text

    // Pause Variables
    private boolean mIsPaused;
    private boolean mIsForcePaused;

    // Notification Variables
    private Notification mQueueNotification;
    private NotificationManager mNotificationManager;
    private RemoteViews mNotificationViews;
    private int mNotificationCounter;

    // Service variables
    private final IBinder mBinder = new DownloaderBinder();
    private Runnable mPageCompletedRunnable;                    // callback for completed page
    private Runnable mChapterCompletedRunnable;                 // callback for completed chapter
    private Runnable mMiscellaneousRunnable;                    // misc UI update (cooldown tick,

    // pause state change, etc)

    public class DownloaderBinder extends Binder
    {
        public DownloaderService getService()
        {
            return DownloaderService.this;
        }
    }

    public void setPageCompletedCallback(Runnable callback)
    {
        mPageCompletedRunnable = callback;
    }

    public void setChapterCompletedCallback(Runnable callback)
    {
        mChapterCompletedRunnable = callback;
    }

    public void setMiscellaneousCallback(Runnable callback)
    {
        mMiscellaneousRunnable = callback;
    }

    public void clearCallbacks()
    {
        mPageCompletedRunnable = null;
        mChapterCompletedRunnable = null;
        mMiscellaneousRunnable = null;
    }

    private void doPageCallback()
    {
        if (mPageCompletedRunnable != null)
            mPageCompletedRunnable.run();
    }

    private void doChapterCallback()
    {
        if (mChapterCompletedRunnable != null)
            mChapterCompletedRunnable.run();
    }

    private void doMiscellaneousCallback()
    {
        if (mMiscellaneousRunnable != null)
            mMiscellaneousRunnable.run();
    }

    public String getStatus()
    {
        if (mChapterLength == 0)
            return "starting...";
        else
        {
            return mCompletedPages + "/" + mChapterLength + " (" + getPercentage(mCompletedPages, mChapterLength) + "%)";
        }
    }

    public int getCompletedPages()
    {
        return mCompletedPages;
    }

    public int getChapterLength()
    {
        return mChapterLength;
    }

    /**
     * @return<br> 0 = added successfully <br>
     * 1 = already in queue <br>
     * 2 = added successfully, but chapter is already in library
     */
    public int addToQueue(Manga manga, String chapterId, int siteId, boolean silent)
    {
        int returnValue = 0;

        for (int i = 0; i < mDownloadQueue.size(); i++)
        {
            QueueItem qi = mDownloadQueue.get(i);
            if (qi.chapterObj.manga.title.equals(manga.title) && qi.chapterObj.chapter.id.equals(chapterId))
            {
                Toast.makeText(this, manga.title + " " + chapterId + " is already in the queue.", Toast.LENGTH_SHORT).show();
                return 1;
            }
        }

        LibraryChapter newChapter = new LibraryChapter();
        QueueItem newItem = new QueueItem();
        int index = 0;

        for (int i = 0; i < manga.chapters.length; i++)
        {
            if (manga.chapters[i].id.equals(chapterId))
                index = i;
        }

        newChapter.manga.id = manga.id;
        newChapter.manga.title = manga.title;
        newChapter.chapterIndex = index;
        newChapter.chapter.title = manga.chapters[index].title;
        newChapter.chapter.id = manga.chapters[index].id;
        newChapter.chapter.url = manga.chapters[index].url;
        newChapter.siteId = siteId;

        MangoSqlite db = new MangoSqlite(this);
        db.open();
        LibraryChapter[] chapters = db.getLibraryChaptersForManga(manga);
        db.close();
        for (int i = 0; i < chapters.length; i++)
        {
            if (chapters[i].chapter.id.equals(newChapter.chapter.id))
            {
                newChapter.rowId = chapters[i].rowId;
                returnValue = 2;
            }
        }

        if (returnValue != 2 && MangoLibraryIO.readIndexData("/Mango/library/" + manga.id + "/" + chapterId + "/") != null)
        {
            db = new MangoSqlite(this);
            db.open();
            newChapter.manga.generateSimpleName();
            db.insertLibraryChapter(newChapter.manga.id, newChapter.manga.title, newChapter.manga.simpleName, newChapter.chapterIndex, newChapter.chapter.title, newChapter.chapter.id,
                    newChapter.chapterCount, newChapter.chapter.url, "/Mango/library/" + newChapter.manga.id + "/" + newChapter.chapter.id + "/", newChapter.siteId);
            db.close();
            Toast.makeText(this, manga.title + " " + chapterId + " is already saved on the SD card. Chapter imported into Library (download skipped).", Toast.LENGTH_SHORT).show();
            if (mDownloadQueue.size() == 0)
            {
                mNotificationManager.cancel(1337);
                stopSelf();
            }
            return 3;
        }

        newItem.chapterObj = newChapter;
        newItem.statusCode = DownloaderService.STATUS_QUEUED;

        mDownloadQueue.add(newItem);
        if (!silent)
            Toast.makeText(this, manga.title + " " + chapterId + " added to download queue.", Toast.LENGTH_SHORT).show();

        if (mDownloadQueue.get(0) == newItem)
        {
            Mango.log("DownloaderService", "Initializing the first chapter. (" + manga.title + " " + chapterId + ")");
            initializeChapter(mDownloadQueue.get(0));
        }
        return returnValue;
    }

    public void removeFromQueue(int index)
    {
        QueueItem item = mDownloadQueue.get(index);
        if (item.statusCode == DownloaderService.STATUS_INPROGRESS)
        {
            chapterCompleted(true);
        }
        else if (item.statusCode == DownloaderService.STATUS_COOLDOWN)
        {
            if (mCooldownTimer != null)
                mCooldownTimer.cancel();
            mDownloadQueue.remove(index);
            if (mDownloadQueue.size() == 0)
                shutdownService();
            else
            {
                mDownloadQueue.get(index).statusCode = 1;
                initializeChapter(mDownloadQueue.get(index));
            }
        }
        else
        {
            mDownloadQueue.remove(index);
        }
    }

    public void cancelAll()
    {
        for (int i = mDownloadQueue.size() - 1; i >= 0; i--)
        {
            try
            {
                Mango.log("DownloaderService", "Removing " + i + " from queue (status " + mDownloadQueue.get(i).statusCode + ")");
                removeFromQueue(i);
                doChapterCallback();
            }
            catch (ArrayIndexOutOfBoundsException e)
            {
                Mango.log("DownloaderService", "cancelAll encountered an ArrayIndexOutOfBoundsException.");

            }
        }
    }

    public void moveUp(int index)
    {
        QueueItem temp = mDownloadQueue.get(index);
        mDownloadQueue.remove(index);
        mDownloadQueue.add(index - 1, temp);
        if (index == 1)
        {
            mDownloadQueue.get(1).statusCode = 2;
            initializeChapter(mDownloadQueue.get(0));
        }
    }

    public void moveToTop(int index)
    {
        QueueItem temp = mDownloadQueue.get(index);
        mDownloadQueue.remove(index);
        if (mDownloadQueue.get(0).statusCode != STATUS_INPROGRESS)
        {
            mDownloadQueue.add(0, temp);
            mDownloadQueue.get(1).statusCode = 2;
            initializeChapter(mDownloadQueue.get(0));
        }
        else
            mDownloadQueue.add(1, temp);
    }

    public void moveDown(int index)
    {
        QueueItem temp = mDownloadQueue.get(index);
        mDownloadQueue.remove(index);
        mDownloadQueue.add(index + 1, temp);
        if (index == 0)
        {
            mDownloadQueue.get(1).statusCode = 2;
            initializeChapter(mDownloadQueue.get(0));
        }
    }

    public void moveToEnd(int index)
    {
        QueueItem temp = mDownloadQueue.get(index);
        mDownloadQueue.remove(index);
        mDownloadQueue.add(temp);
        if (index == 0)
        {
            mDownloadQueue.get(mDownloadQueue.indexOf(temp)).statusCode = 2;
            initializeChapter(mDownloadQueue.get(0));
        }
    }

    public ArrayList<QueueItem> getQueue()
    {
        return mDownloadQueue;
    }

    private int getPercentage(int current, int max)
    {
        return (int) (((double) current / (double) max) * 100);
    }

    private void initializeChapter(QueueItem item)
    {
        writeDownloadQueue();

        mTargetItem = item;
        mTargetChapter = item.chapterObj;

        mCurrentPageIndex = 0;
        mCompletedPages = 0;
        mChapterLength = 0;
        mPagelistRetries = 0;
        mChapterHashCode = mTargetChapter.hashCode();

        if (mCooldownTimer != null)
            mCooldownTimer.cancel();

        System.gc();

        mNotificationViews.setTextViewText(R.id.NotificationText, mTargetChapter.manga.title + " " + mTargetChapter.chapter.id + "\nStarting... (" + (mDownloadQueue.size() - 1) + " )");
        mNotificationViews.setProgressBar(R.id.NotificationProgress, 1, 1, true);
        mNotificationManager.notify(1337, mQueueNotification);
        doPageCallback();

        mXmlTask = new XmlDownloader(this);
        String url = "http://%SERVER_URL%/getpages.aspx?pin=" + Mango.getPin() + "&mangaid=" + mTargetChapter.manga.id + "&chapterid=" + mTargetChapter.chapter.url + "&site="
                + mTargetChapter.siteId;

        mXmlTask.execute(url);
    }

    private class XmlDownloader extends AsyncTask<String, Void, MangoHttpResponse>
    {
        WeakReference<DownloaderService> service = null;

        public XmlDownloader(DownloaderService service)
        {
            attach(service);
        }

        @Override
        protected MangoHttpResponse doInBackground(String... params)
        {
            if (service == null || service.get() == null)
            {
                Mango.log("DownloaderService", "XmlDownloader's service WeakReference was null.  Perhaps the service was collected by the OS?");
                MangoHttpResponse errorResponse = new MangoHttpResponse();
                errorResponse.requestUri = params[0];
                errorResponse.exception = true;
                return errorResponse;
            }

            return MangoHttp.downloadData(params[0], service.get());
        }

        @Override
        protected void onPostExecute(MangoHttpResponse data)
        {
            if (service == null || service.get() == null)
                Mango.log("DownloaderService", "AsyncTask skipped onPostExecute because no DownloaderService is attached!");
            else
                service.get().callback(data);
        }

        void detach()
        {
            service = null;
        }

        void attach(DownloaderService service)
        {
            this.service = new WeakReference<DownloaderService>(service);
        }
    }

    private void callback(MangoHttpResponse data)
    {
        if (data.exception)
        {
            Mango.log("DownloaderService", "Unable to download pagelist.");
            if (mPagelistRetries > 3)
            {
                mFailedChapters++;
                mFailedPages++;
                mReportText.append(mTargetChapter.manga.title + " " + mTargetChapter.chapter.id + " pagelist download failed. (" + data.toString() + ")\n\n");
                chapterCompleted(true);
            }
            else
            {
                mPagelistRetries++;
                mXmlTask = new XmlDownloader(this);
                String url = "http://%SERVER_URL%/getpages.aspx?pin=" + Mango.getPin() + "&mangaid=" + mTargetChapter.manga.id + "&chapterid=" + mTargetChapter.chapter.url + "&site="
                        + mTargetChapter.siteId;

                mXmlTask.execute(url);
            }
        }

        parseXml(data.toString());
    }

    private void parseXml(String data)
    {
        ArrayList<Page> pageArrayList = new ArrayList<Page>();
        try
        {
            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            SAXParser parser = saxFactory.newSAXParser();
            XMLReader reader = parser.getXMLReader();
            PagelistSaxHandler handler = new PagelistSaxHandler();
            reader.setContentHandler(handler);
            reader.parse(new InputSource(new StringReader(data)));
            pageArrayList.addAll(handler.getAllPages());
            mSubstringStart = handler.getSubstringStart();
            mSubstringStart.replace("&quot;", "\"");
            mSubstringStart.replace("&amp;", "&");
            mSubstringStart.replace("&lt;", "<");
            mSubstringStart.replace("&gt;", ">");
            mSubstringAltStart = handler.getSubstringAltStart();
            mSubstringAltStart.replace("&quot;", "\"");
            mSubstringAltStart.replace("&amp;", "&");
            mSubstringAltStart.replace("&lt;", "<");
            mSubstringAltStart.replace("&gt;", ">");
            mPageUrlPrefix = handler.getImageUrlPrefix();
        }
        catch (Exception ex)
        {
            Mango.log("DownloaderService", "Error parsing xml. " + ex.toString());
            mReportText.append(mTargetChapter.manga.title + " " + mTargetChapter.chapter.id + " failed. (" + getExceptionText(ex.toString()) + ")\n\n");
            chapterCompleted(true);
            return;
        }

        if (pageArrayList.size() == 0)
        {
            Mango.log("DownloaderService", "Pagelist is empty.");
            mReportText.append(mTargetChapter.manga.title + " " + mTargetChapter.chapter.id + " failed. (Pagelist was empty)\n\n");
            chapterCompleted(true);
            return;
        }

        mPages = new Page[pageArrayList.size()];
        mPageStatus = new boolean[pageArrayList.size()];
        pageArrayList.toArray(mPages);
        pageArrayList = null;
        mChapterLength = mPages.length;

        String path = "/Mango/library/" + mTargetChapter.manga.id + "/" + mTargetChapter.chapter.id + "/";
        mTargetChapter.path = path;

        String status = MangoLibraryIO.writeIndexData(path, data);
        if (!status.equals("okay"))
        {
            Mango.log("DownloaderService", "Unable to write chapter index data! (" + status + ")");
            chapterCompleted(true);
            return;
        }

        long cooldown = Mango.getSharedPreferences().getLong("downloaderCooldown", 0);
        if (cooldown > System.currentTimeMillis())
        {
            mTargetItem.statusCode = DownloaderService.STATUS_COOLDOWN;
            mCooldownTimer = new Timer();
            mCooldownTimer.schedule(new WaitTask(), 1000, 1000);
            mNotificationViews.setTextViewText(R.id.NotificationText, mTargetChapter.manga.title + " " + mTargetChapter.chapter.id + "\nWaiting for cooldown... ("
                    + ((cooldown - System.currentTimeMillis()) / 1000) + " seconds)");
            mNotificationViews.setProgressBar(R.id.NotificationProgress, 1, 1, true);
            mNotificationManager.notify(1337, mQueueNotification);
            doMiscellaneousCallback();
            return;
        }

        startChapter();
    }

    private class WaitTask extends TimerTask
    {
        @Override
        public void run()
        {
            if (!MangoHttp.checkConnectivity(DownloaderService.this))
            {
                setForcePause(true);
            }
            else
            {
                if (mIsForcePaused)
                    setForcePause(false);
            }

            if (mIsPaused)
            {
                return;
            }

            long cooldown = Mango.getSharedPreferences().getLong("downloaderCooldown", 0);

            try
            {
                mNotificationViews.setTextViewText(R.id.NotificationText, mTargetChapter.manga.title + " " + mTargetChapter.chapter.id + "\nWaiting for cooldown... ("
                        + ((cooldown - System.currentTimeMillis()) / 1000) + " seconds)");
            }
            catch (NullPointerException e)
            {
                mCooldownTimer.cancel();
                return;
            }
            mNotificationViews.setProgressBar(R.id.NotificationProgress, 1, 1, true);
            mNotificationManager.notify(1337, mQueueNotification);
            doMiscellaneousCallback();
            if (cooldown < System.currentTimeMillis())
            {
                startChapter();
                mCooldownTimer.cancel();
            }
        }
    }

    private void startChapter()
    {
        mNotificationCounter++;
        if (mNotificationCounter == 1)
        {
            mNotificationCounter = 0;
            mNotificationManager.cancel(1337);
            mQueueNotification = null;
            mNotificationViews = null;
            System.gc();
            mNotificationViews = new RemoteViews(getPackageName(), R.layout.queuenotification);
            mNotificationViews.setTextViewText(R.id.NotificationText, "Re-creating notification.");
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, DownloaderActivity.class), 0);

            mQueueNotification = new Notification(R.drawable.icon_notify_progress, (mDownloadQueue.size() - 1) + " chapters remaining...", System.currentTimeMillis());
            mQueueNotification.contentIntent = pendingIntent;
            mQueueNotification.contentView = mNotificationViews;
            mQueueNotification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR | Notification.FLAG_ONLY_ALERT_ONCE;
            this.startForeground(1337, mQueueNotification);
        }
        mNotificationViews.setTextViewText(R.id.NotificationText, mTargetChapter.manga.title + " " + mTargetChapter.chapter.id + "\n" + (mIsPaused ? "Paused. " : "Starting... ") + "("
                + mChapterLength + " pages, " + (mDownloadQueue.size() - 1) + " queued)");
        mNotificationViews.setProgressBar(R.id.NotificationProgress, mChapterLength, mCompletedPages, false);
        mNotificationManager.notify(1337, mQueueNotification);
        doPageCallback();
        mTargetItem.statusCode = DownloaderService.STATUS_INPROGRESS;
        mCurrentDownloader = new ImageBackgroundDownloader(this);
        mCurrentDownloader.downloadImage(mPageUrlPrefix + mPages[0].id, mPages[0], 0, mChapterHashCode);
    }

    private void pageDownloadedCallback(int index, int chapterHashcode)
    {
        if (chapterHashcode != mChapterHashCode)
        {
            Mango.log("DownloaderService", "!!! pageDownloadedCallback: hashcodes do not match.");
            return;
        }

        int nextPage = 9999;
        int completed = 0;

        mPageStatus[index] = true;

        for (int i = 0; i < mPageStatus.length; i++)
        {
            if (mPageStatus[i] == true)
                completed++;
            else
            {
                if (i < nextPage)
                    nextPage = i;
            }
        }

        mCurrentPageIndex = nextPage;
        mCompletedPages = completed;

        mNotificationViews.setTextViewText(R.id.NotificationText, mTargetChapter.manga.title + " " + mTargetChapter.chapter.id + "\n" + (mIsPaused ? "Paused. " : "Downloading... ") + "("
                + getPercentage(mCompletedPages, mChapterLength) + "%, " + (mDownloadQueue.size() - 1) + " queued)");
        mNotificationViews.setProgressBar(R.id.NotificationProgress, mChapterLength, mCompletedPages, false);
        mNotificationManager.notify(1337, mQueueNotification);
        doPageCallback();
        if (getPercentage(mCompletedPages, mChapterLength) >= 100)
        {
            chapterCompleted(false);
            return;
        }
        mCurrentDownloader = new ImageBackgroundDownloader(this);
        mCurrentDownloader.downloadImage(mPageUrlPrefix + mPages[mCurrentPageIndex].id, mPages[mCurrentPageIndex], mCurrentPageIndex, mChapterHashCode);
    }

    private void chapterCompleted(boolean canceled)
    {
        if (!canceled)
        {
            if (mFailedPages != 0)
            {
                Mango.log(mFailedPages + " pages did not download properly.");
                mReportText.append(mTargetChapter.manga.title + " " + mTargetChapter.chapter.id + " is missing " + mFailedPages + " pages.\n\n");
                mPartialChapters++;
            }
            else
                mSuccessfulChapters++;
            mFailedPages = 0;
            Mango.log("DownloaderService", "Adding metadata for new chapter...");
            MangoSqlite db = new MangoSqlite(this);
            db.open();
            LibraryChapter[] chapters = db.getLibraryChaptersForManga(mTargetChapter.manga);
            for (int i = 0; i < chapters.length; i++)
            {
                if (chapters[i].chapter.id.equals(mTargetChapter.chapter.id))
                {
                    Mango.log("DownloaderService", "Deleted existing metadata record. (" + chapters[i].rowId + ")");
                    db.deleteLibraryChapter(chapters[i].rowId);
                }
            }
            mTargetChapter.manga.generateSimpleName();
            db.insertLibraryChapter(mTargetChapter.manga.id, mTargetChapter.manga.title, mTargetChapter.manga.simpleName, mTargetChapter.chapterIndex, mTargetChapter.chapter.title,
                    mTargetChapter.chapter.id, mTargetChapter.chapterCount, mTargetChapter.chapter.url, mTargetItem.chapterObj.path, mTargetChapter.siteId);
            db.close();
            Mango.log("DownloaderService", "Chapter added to Library metadata table.");
        }
        else
        {
            mFailedPages = 0;
            Mango.log("DownloaderService", "Skipping " + mTargetChapter.manga.title + " " + mTargetChapter.chapter.id);

            if (mTargetChapter.rowId == 0)
                MangoLibraryIO.deleteIndexData(mTargetChapter.path);
            else
                Mango.log("DownloaderService", "Not deleting index for skipped chapter because metadata exists.");
        }

        mChapterHashCode = 0;

        float cooldownMultiplier = 1;
        if (mTargetChapter.siteId == Mango.SITE_MANGABLE)
            cooldownMultiplier = 2;
        else
            cooldownMultiplier = 0.5f;

        long cooldown = System.currentTimeMillis() + (long) ((1000 * mChapterLength) * cooldownMultiplier);
        if (cooldown - System.currentTimeMillis() > 99000)
            cooldown = System.currentTimeMillis() + 99999;

        Mango.getSharedPreferences().edit().putLong("downloaderCooldown", cooldown).commit();

        if (mDownloadQueue.size() > 1)
        {
            mDownloadQueue.remove(0);
            initializeChapter(mDownloadQueue.get(0));
            doChapterCallback();
            return;
        }

        if (mCooldownTimer != null)
            mCooldownTimer.cancel();
        doChapterCallback();
        shutdownService();
    }

    public boolean getPause()
    {
        return mIsPaused;
    }

    public void setPause(boolean pause)
    {
        mIsPaused = pause;
        if (!pause)
        {
            if (mCurrentDownloader != null)
                mCurrentDownloader.downloadImage();
        }
        doMiscellaneousCallback();
    }

    public boolean getForcePause()
    {
        return mIsForcePaused;
    }

    private void setForcePause(boolean pause)
    {
        mIsPaused = pause;
        if (mIsPaused)
        {
            mIsForcePaused = true;
        }
        else
            mIsForcePaused = false;
        doMiscellaneousCallback();
    }

    private String getExceptionText(String text)
    {
        int colonPosition = text.indexOf(":");
        if (colonPosition == -1)
            return text;
        return text.substring(colonPosition + 1).trim();
    }

    class ImageBackgroundDownloader
    {
        int currentChapter = -1;
        int currentIndex = -1;
        int retries = 0;
        private Page currentPage;
        private DownloaderTask downloaderTask;
        private WeakReference<DownloaderService> service;
        private String currentUrl;
        private boolean modifiedUrl;
        private Timer resumeChecker;

        private boolean downloading = false;

        private class DownloaderTask extends AsyncTask<String, Void, MangoHttpResponse>
        {
            WeakReference<ImageBackgroundDownloader> downloader;

            DownloaderTask(ImageBackgroundDownloader ref)
            {
                downloader = new WeakReference<ImageBackgroundDownloader>(ref);
            }

            @Override
            protected MangoHttpResponse doInBackground(String... params)
            {
                return MangoHttp.downloadData(params[0], downloader.get().service.get());
            }

            @Override
            protected void onPostExecute(MangoHttpResponse data)
            {
                downloading = false;
                if (downloader.get().service.get() != null)
                {
                    if (data.exception)
                        callbackError(data);
                    else if (data.contentType.contains("text"))
                        callbackHtml(data);
                    else
                        callbackImage(data);
                }
            }
        }

        public ImageBackgroundDownloader(DownloaderService context)
        {
            service = new WeakReference<DownloaderService>(context);
        }

        public void startPauseTimer()
        {
            Mango.log("DownloaderService", "We've lost connectivity, downloading will be paused until connectivity is re-established.");
            if (resumeChecker == null)
            {
                resumeChecker = new Timer();
                resumeChecker.schedule(new ResumeCheckTask(), 1000, 1000);
            }
        }

        public void downloadImage()
        {
            downloadImage(currentUrl, currentPage, currentIndex, currentChapter);
        }

        public void downloadImage(String url, Page page, int index, int chapter)
        {
            if (downloading || index == -1)
            {
                Mango.log("DownloaderService", "downloadImage was called, but this loader is busy.");
                return;
            }

            currentChapter = chapter;
            currentIndex = index;
            currentPage = page;
            if (!modifiedUrl)
            {
                modifiedUrl = true;

                // MangaFox requires that we use Page.url instead of Page.id.
                // Mangable usually works fine if we just use Page.id + ".jpg", but sometimes
                // we need to use Page.url. To determine if this is the case, we'll just check
                // to see if mSubstringStart is an empty string.

                if (!mSubstringStart.equals(""))
                    url = service.get().mPageUrlPrefix + currentPage.url;
                else
                    url += ".jpg";
            }
            currentUrl = url;

            if (mIsPaused)
            {
                Mango.log("DownloaderService", "Downloading is paused, ImageBackgroundDownloader.downloadImage is returning.");
                return;
            }

            if (!MangoHttp.checkConnectivity(service.get()))
            {
                setForcePause(true);
                startPauseTimer();
                return;
            }

            downloaderTask = new DownloaderTask(this);
            downloaderTask.execute(currentUrl);
            downloading = true;
        }

        public void callbackHtml(MangoHttpResponse data)
        {
            String newUrl = extractUrlFromHtml(data.toString());
            try
            {
                URL url = new URL(newUrl);
            }
            catch (MalformedURLException e)
            {
                Mango.log("DownloaderService", "Extracted URL was not valid.");
                data.exception = true;
                data.data = new String("Extracted URL was not valid.").getBytes();
                callbackError(data);
                return;
            }
            currentUrl = newUrl;

            downloadImage(currentUrl, currentPage, currentIndex, currentChapter);
        }

        private String extractUrlFromHtml(String data)
        {
            try
            {
                if (mSubstringAltStart.equals(""))
                {
                    int srcStart = data.indexOf(service.get().mSubstringStart) + service.get().mSubstringStart.length();
                    int srcEnd = data.indexOf("\"", srcStart);
                    return data.substring(srcStart, srcEnd);
                }
                int substringOffset = data.indexOf(service.get().mSubstringAltStart) + service.get().mSubstringAltStart.length();
                substringOffset -= 150; // lolmagic literal
                int srcStart = data.indexOf(service.get().mSubstringStart, substringOffset) + service.get().mSubstringStart.length();
                int srcEnd = data.indexOf("\"", srcStart);
                return data.substring(srcStart, srcEnd);
            }
            catch (Exception ex)
            {
                return ex.getClass().getSimpleName();
            }
        }

        public void callbackError(MangoHttpResponse data)
        {
            if (!MangoHttp.checkConnectivity(service.get()))
            {
                setForcePause(true);
                startPauseTimer();
                return;
            }

            retries++;
            if (retries >= 3)
            {
                Mango.log("DownloaderService", "Page " + currentPage.id + " failed to download after three tries.");
                mFailedPages++;
                mReportText.append(mTargetChapter.manga.title + " " + mTargetChapter.chapter.id + " page " + currentPage.id + " failed. (" + data.toString() + ")\n");
                service.get().pageDownloadedCallback(currentIndex, currentChapter);
                currentIndex = -1;
                retries = 0;
                return;
            }

            Mango.log("DownloaderService", "Page " + currentPage.id + " failed to download, retrying.");
            downloadImage(currentUrl, currentPage, currentIndex, currentChapter);
        }

        public void callbackImage(MangoHttpResponse data)
        {
            if (currentChapter != service.get().mChapterHashCode)
            {
                Mango.log("DownloaderService", "Dropping page because we've changed chapters.");
                return;
            }

            data.writeEncodedImageToCache(1, mTargetChapter.path, currentPage.id);

            mSuccessfulPages++;
            if (resumeChecker != null)
                resumeChecker.cancel();
            service.get().pageDownloadedCallback(currentIndex, currentChapter);
            currentIndex = -1;
        }

        private class ResumeCheckTask extends TimerTask
        {
            @Override
            public void run()
            {
                if (mIsForcePaused && MangoHttp.checkConnectivity(service.get()))
                {
                    resumeChecker.cancel();
                    resumeChecker = null;
                    setForcePause(false);
                    downloadImage(currentUrl, currentPage, currentIndex, currentChapter);
                }
                else if (!mIsPaused)
                {
                    resumeChecker.cancel();
                    resumeChecker = null;
                    setPause(false);
                    downloadImage(currentUrl, currentPage, currentIndex, currentChapter);
                }
            }
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

    @Override
    public IBinder onBind(Intent intent)
    {
        Mango.log("DownloaderService", "onBind");
        return mBinder;
    }

    @Override
    public void onDestroy()
    {
        Mango.log("DownloaderService", "onDestroy");
        if (mDownloadQueue.size() != 0)
        {
            Notification notification = new Notification(R.drawable.icon_notify_done, "Downloader stopped.", System.currentTimeMillis());
            CharSequence contentTitle = "Mango - Download Paused by OS";
            CharSequence contentText = "Download paused. Tap to resume.";

            Intent notificationIntent = new Intent(this, DownloaderActivity.class);
            notificationIntent.putExtra("resume", "true");
            notificationIntent.setAction("resume");
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            notification.flags |= Notification.FLAG_AUTO_CANCEL;

            notification.setLatestEventInfo(this, contentTitle, contentText, contentIntent);
            mNotificationManager.notify(1338, notification);
        }
        cleanup();
        stopSelf();
        super.onDestroy();
    }

    private void cleanup()
    {
        Mango.log("DownloaderService", "Cleaning up...");
        Mango.reflect(this, "setForeground", false);
        this.stopForeground(true);
        if (mCooldownTimer != null)
            mCooldownTimer.cancel();
        if (mNotificationManager != null)
            mNotificationManager.cancel(1337);
        if (mDownloadQueue != null)
            mDownloadQueue.clear();
        mCurrentDownloader = null;
        mChapterHashCode = 0;

        clearCallbacks();
    }

    public void shutdownService()
    {
        Mango.log("DownloaderService", "Preparing DownloaderService for shutdown...");
        String report = "";
        if (mSuccessfulChapters != 0)
            report = mSuccessfulChapters + " chapters (" + mSuccessfulPages + " pages) downloaded successfully!\n";
        if (mPartialChapters != 0)
            report += mPartialChapters + " chapters downloaded successfully, but with one or more pages missing.\n";
        if (mFailedChapters != 0)
            report += mFailedChapters + " chapters were skipped because the pagelist could not be downloaded.\n";
        if (mReportText.length() != 0)
            report += "\nDetailed Report:\n" + mReportText.toString();
        if (mSuccessfulChapters == 0 && mPartialChapters == 0 && mFailedChapters == 0)
            report += "No chapters were downloaded.";
        mReportText = null;
        Mango.log("DownloaderService", "Writing download report...");
        MangoLibraryIO.writeReportData(report);
        report = null;
        System.gc();

        Mango.log("DownloaderService", "Deleting queue backup...");
        File file = new File(Mango.getDataDirectory() + "/Mango/cache/downloaderqueue.ser");
        if (file.exists())
            file.delete();

        Mango.log("DownloaderService", "Writing library backup...");
        MangoSqlite db = new MangoSqlite(this);
        db.open();
        MangoLibraryIO.writeLibraryBackup(db.getAllLibraryChapters(null));
        db.close();

        if (mDownloadQueue.size() <= 1)
        {
            Notification notification = new Notification(R.drawable.icon_notify_done, "Downloads completed.", System.currentTimeMillis());
            CharSequence contentTitle = "Mango";
            CharSequence contentText = "All chapters have finished downloading.";
            Intent notificationIntent = new Intent(this, DownloaderActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            notification.flags |= Notification.FLAG_AUTO_CANCEL;

            notification.setLatestEventInfo(this, contentTitle, contentText, contentIntent);
            mNotificationManager.notify(1338, notification);
        }

        cleanup();

        Mango.log("DownloaderService", "Calling stopSelf().");
        stopSelf();
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        Mango.reflect(this, "setForeground", true);

        Mango.log("DownloaderService", "onCreate");

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mDownloadQueue = new ArrayList<QueueItem>();

        mNotificationViews = new RemoteViews(getPackageName(), R.layout.queuenotification);
        mNotificationViews.setTextViewText(R.id.NotificationText, "null");

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, DownloaderActivity.class), 0);

        mQueueNotification = new Notification(R.drawable.icon_notify_progress, "Starting download...", System.currentTimeMillis());
        mQueueNotification.contentIntent = pendingIntent;
        mQueueNotification.contentView = mNotificationViews;
        mQueueNotification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR | Notification.FLAG_ONLY_ALERT_ONCE;

        mReportText = new StringBuilder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        File file = new File(Mango.getDataDirectory() + "/Mango/cache/downloaderqueue.ser");
        if (mDownloadQueue.size() == 0 && file.exists())
            readDownloadQueue();
        Mango.log("DownloaderService", "onStartCommand!");
        return Service.START_STICKY;
    }

    private void writeDownloadQueue()
    {
        String state = Environment.getExternalStorageState();
        if (!state.startsWith(Environment.MEDIA_MOUNTED))
        {
            return;
        }

        File file = new File(Mango.getDataDirectory() + "/Mango/cache/");
        ObjectOutputStream out = null;

        try
        {
            Mango.log("DownloaderService", "Backing up download queue");
            file.mkdirs();
            file = new File(Mango.getDataDirectory() + "/Mango/cache/downloaderqueue.ser");
            if (file.exists())
                file.delete();
            file.createNewFile();

            out = new ObjectOutputStream(new FileOutputStream(file));
            Mango.log("DownloaderService", "Serializing " + mDownloadQueue.size() + " queued items...");
            QueueItem[] q = new QueueItem[mDownloadQueue.size()];
            mDownloadQueue.toArray(q);
            out.writeObject(q);
            Mango.log("DownloaderService", "Writing to disk...");
            out.flush();
            Mango.log("DownloaderService", "Done!");
        }
        catch (Exception ioe)
        {
            Mango.log("DownloaderService", "Queue backup failed: " + ioe.toString());
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

    private void readDownloadQueue()
    {
        String state = Environment.getExternalStorageState();
        if (!state.startsWith(Environment.MEDIA_MOUNTED))
        {
            return;
        }

        File file;
        ObjectInputStream in = null;
        try
        {
            Mango.log("DownloaderService", "Trying to restore download queue...");
            file = new File(Mango.getDataDirectory() + "/Mango/cache/downloaderqueue.ser");
            if (!file.exists())
            {
                Mango.log("DownloaderService", "file doesn't exist.");
                return;
            }

            in = new ObjectInputStream(new FileInputStream(file));
            Mango.log("DownloaderService", "Reading and deserializing '/cache/downloaderqueue.ser'...");
            QueueItem[] q = (QueueItem[]) in.readObject();
            in.close();
            Mango.log("DownloaderService", "Adding " + q.length + " items.");
            mDownloadQueue = new ArrayList<QueueItem>();
            for (int i = 0; i < q.length; i++)
            {
                mDownloadQueue.add(q[i]);
            }

            if (mDownloadQueue.size() > 0)
                initializeChapter(mDownloadQueue.get(0));

            doChapterCallback();
        }
        catch (Exception e)
        {
            Mango.log("DownloaderService", "Queue restore failed: " + e.toString());
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
}
