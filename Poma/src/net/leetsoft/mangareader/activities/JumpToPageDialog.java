package net.leetsoft.mangareader.activities;

import android.app.Dialog;
import android.content.Context;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.graphics.Paint.Align;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import net.leetsoft.mangareader.*;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;

public class JumpToPageDialog extends Dialog
{
    private Gallery mGallery;
    private Paint mPaint;
    private JumpToPageListener mListener;

    private boolean mOffline;
    private boolean mFilesystemChapter;

    private String mMangaId;
    private String mChapterId;
    private Page[] mPages;

    //Lazy loader
    private HashMap<String, SoftReference<Bitmap>> mThumbnailCache = new HashMap<String, SoftReference<Bitmap>>();
    private boolean[] mQueued;
    private volatile ArrayList<Integer> mPendingThumbnails;
    private Runnable mLoaderRunnable;
    private ThumbnailLoader mLoader;


    public JumpToPageDialog(Context context, JumpToPageListener listener)
    {
        super(context);
        Window window = this.getWindow();
        window.requestFeature(Window.FEATURE_NO_TITLE);
        this.setContentView(getLayoutInflater().inflate(R.layout.jumptopage, null));
        mGallery = (Gallery) findViewById(R.id.JumpToPageGallery);
        mGallery.setOnItemClickListener(new OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id)
            {
                if (mListener != null)
                    mListener.jumpToPageCallback(position);
                JumpToPageDialog.this.dismiss();
                mGallery = null;
            }
        });
        mListener = listener;
        Paint p = new Paint();
        p.setTextSize(75f);
        p.setColor(Color.GRAY);
        p.setFlags(Paint.ANTI_ALIAS_FLAG);
        p.setTextAlign(Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        mPaint = p;
    }

    @Override
    protected void onStop()
    {
        mListener = null;
        System.gc();
        super.onStop();
    }

    public void initializeAdapter(String mangaId, String chapterId, Page[] pages, int currentPage, boolean offline, boolean filesystemChapter)
    {
        mMangaId = mangaId;
        mChapterId = chapterId;
        mPages = pages;
        mOffline = offline;
        mFilesystemChapter = filesystemChapter;

        mPendingThumbnails = new ArrayList<Integer>();
        mQueued = new boolean[mPages.length];

        // mPages = new Bitmap[mPageObjects.length];
        // // 180 * 260
        mGallery.setAdapter(new JumpToPageAdapter(this.getContext()));
        mGallery.setSelection(currentPage);
    }

    private Bitmap getDefaultThumbnail(int index)
    {
        Bitmap bm = Bitmap.createBitmap(180, 260, Config.RGB_565);
        Canvas c = new Canvas(bm);
        c.drawARGB(255, 255, 255, 255);
        c.drawText(String.valueOf(mPages[index].id), bm.getWidth() / 2, (bm.getHeight() / 2) + (mPaint.getTextSize() / 4), mPaint);
        return bm;
    }

    private Bitmap getThumbnailBitmap(int index)
    {
        if (mGallery == null)
            return null;

        if (mFilesystemChapter)
        {
            Bitmap bm = MangoLibraryIO.readBitmapFromDisk(mMangaId, mPages[index].url, 7, false);
            if (bm != null)
                return Bitmap.createScaledBitmap(bm, 180, 260, false);
        }
        else if (mOffline)
        {
            Bitmap bm = MangoLibraryIO.readBitmapFromDisk("/Mango/library/" + mMangaId + "/" + mChapterId + "/", mPages[index].id, 7, true);
            if (bm != null)
                return Bitmap.createScaledBitmap(bm, 180, 260, false);
        }
        else if (MangoCache.checkCacheForImage("page/", Mango.getSiteId() + mMangaId + mChapterId + mPages[index].id))
        {
            return Bitmap.createScaledBitmap(MangoCache.readBitmapFromCache("page/", Mango.getSiteId() + mMangaId + mChapterId + mPages[index].id, 7), 180, 260, false);
        }

        return getDefaultThumbnail(index);
    }

    public class JumpToPageAdapter extends BaseAdapter
    {
        int mGalleryItemBackground;
        private Context mContext;
        private LayoutInflater mInflater;

        public JumpToPageAdapter(Context c)
        {
            mContext = c;
            mInflater = JumpToPageDialog.this.getLayoutInflater();
            // TypedArray a = obtainStyledAttributes(android.R.style.Theme);
            // mGalleryItemBackground = a.getResourceId(android.R.style.theme.Theme_galleryItemBackground, 0);
            // a.recycle();

        }

        @Override
        public int getCount()
        {
            if (mPages == null)
                return 0;
            return mPages.length;
        }

        @Override
        public Object getItem(int position)
        {
            return position;
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            if (convertView == null)
                convertView = mInflater.inflate(R.layout.jtplayout, null);
            else
            {
                // Gallery is broken and never uses convertView, so this doesn't work.
                ((ImageView) convertView.findViewById(R.id.JumpToPageThumbnail)).getDrawable().setCallback(null);
                ((ImageView) convertView.findViewById(R.id.JumpToPageThumbnail)).setImageBitmap(null);
            }

            ImageView iv = (ImageView) convertView.findViewById(R.id.JumpToPageThumbnail);
            TextView tv = (TextView) convertView.findViewById(R.id.JumpToPageNumber);

            if (mGallery == null)
                return convertView;

            if (!mThumbnailCache.containsKey(mPages[position].id))
            {
                iv.setBackgroundColor(Color.BLACK);
                iv.setImageBitmap(getDefaultThumbnail(position));
                iv.setScaleType(ImageView.ScaleType.CENTER);
                if (!mQueued[position])
                {
                    mPendingThumbnails.add(0, new Integer(position));
                    int limit = (mGallery.getLastVisiblePosition() - mGallery.getFirstVisiblePosition()) + 3;
                    if (mPendingThumbnails.size() > limit)
                    {
                        mQueued[mPendingThumbnails.get(mPendingThumbnails.size() - 1).intValue()] = false;
                        mPendingThumbnails.remove(mPendingThumbnails.size() - 1);
                    }
                    mQueued[position] = true;
                }

                if (mLoader == null)
                {
                    if (mLoaderRunnable != null)
                        mGallery.removeCallbacks(mLoaderRunnable);
                    mLoaderRunnable = new Runnable()
                    {

                        @Override
                        public void run()
                        {
                            mLoader = new ThumbnailLoader(JumpToPageDialog.this);
                            mLoaderRunnable = null;
                            mLoader.execute((Void[]) null);
                        }
                    };
                    mGallery.postDelayed(mLoaderRunnable, 150);
                }
            }
            else
            {
                if (mThumbnailCache.get(mPages[position].id).get() == null)
                {
                    mThumbnailCache.remove(mPages[position].id);
                    return getView(position, convertView, parent);
                }
                else
                {
                    iv.setBackgroundColor(Color.BLACK);
                    iv.setImageBitmap(mThumbnailCache.get(mPages[position].id).get());
                    iv.setScaleType(ImageView.ScaleType.CENTER);
                }
            }

            tv.setText(String.valueOf(mPages[position].id));

            return convertView;
        }
    }

    private class ThumbnailLoader extends AsyncTask<Void, Void, Void>
    {
        JumpToPageDialog dialog = null;

        @Override
        protected void onProgressUpdate(Void... values)
        {
            ((BaseAdapter) dialog.mGallery.getAdapter()).notifyDataSetChanged();
            super.onProgressUpdate(values);
        }

        public ThumbnailLoader(JumpToPageDialog dialog)
        {
            attach(dialog);
        }

        @Override
        protected Void doInBackground(Void... params)
        {
            try
            {
                while (mPendingThumbnails.size() > 0)
                {
                    int index = mPendingThumbnails.get(0).intValue();
                    mPendingThumbnails.remove(0);
                    Bitmap b = getThumbnailBitmap(index);

                    dialog.mThumbnailCache.put(mPages[index].id, new SoftReference<Bitmap>(b));

                    if (index >= dialog.mGallery.getFirstVisiblePosition() && index <= dialog.mGallery.getLastVisiblePosition())
                        this.publishProgress((Void[]) null);
                    mQueued[index] = false;
                }
            }
            catch (NullPointerException ex)
            {
                //popup has been closed
            }
            mLoader = null;
            return null;
        }

        void detach()
        {
            dialog = null;
        }

        void attach(JumpToPageDialog activity)
        {
            this.dialog = activity;
        }
    }


    // step 1 - to return values from dialog
    public interface JumpToPageListener
    {
        public void jumpToPageCallback(int selection);
    }
}
