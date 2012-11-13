package net.leetsoft.mangareader.ui;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import net.leetsoft.mangareader.R;
import com.mobclix.android.sdk.MobclixAdView;

public class MangoAdWrapperView extends RelativeLayout
{
    Bitmap mCloseButton;
    Paint mPaint = new Paint();
    Matrix mMatrix = new Matrix();
    Runnable mCloseListener;

    View mAdHitbox;

    public MangoAdWrapperView(Context context)
    {
        super(context);
    }

    public MangoAdWrapperView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        mCloseButton = BitmapFactory.decodeResource(context.getResources(), R.drawable.toast_close);
        mMatrix.setScale(0.70f, 0.70f);
        mPaint.setColor(0xffff0000);
        mPaint.setAlpha(200);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(3);
    }

    public void setAdHitbox(View v)
    {
        mAdHitbox = v;
    }

    public void setCloseListener(Runnable r)
    {
        mCloseListener = r;
    }

    @Override
    public void dispatchDraw(Canvas c)
    {
        // Mango.Log("drawing " + this.getHeight());
        // c.save();
        // c.rotate(270, 1, 1);
        // c.translate(-480, 0);
        super.dispatchDraw(c);
        if (mAdHitbox == null)
            return;
        Matrix m = new Matrix();
        m.setScale(0.60f, 0.60f);
        m.postTranslate(mAdHitbox.getLeft(), 0.01f);
        c.drawBitmap(mCloseButton, m, mPaint);
        //c.drawRect(new Rect(mAdHitbox.getLeft(), mAdHitbox.getTop(), mAdHitbox.getLeft() + (int) (mCloseButton.getWidth() * 0.7), mAdHitbox.getTop() + mCloseButton.getHeight()), mPaint);
        //c.drawRect(new Rect(mAdHitbox.getLeft(), mAdHitbox.getTop(), mAdHitbox.getLeft() + (int) (mAdHitbox.getWidth()), mAdHitbox.getTop() + mAdHitbox.getHeight()), mPaint);
        // c.restore();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev)
    {
        if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_DOWN)
        {
            int x = (int) ev.getX();
            int y = (int) ev.getY();
            if (x >= mAdHitbox.getLeft() && x <= mAdHitbox.getLeft() + (mCloseButton.getWidth() * 0.7))
                if (y >= mAdHitbox.getTop() && y <= mAdHitbox.getTop() + mCloseButton.getHeight() * 0.7)
                {
                    mCloseListener.run();
                    return true;
                }
        }

        return super.dispatchTouchEvent(ev);
    }

}
