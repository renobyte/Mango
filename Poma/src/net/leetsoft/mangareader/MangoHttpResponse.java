package net.leetsoft.mangareader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import net.leetsoft.mangareader.ui.MangoDecorHandler;
import org.apache.http.Header;

import java.io.UnsupportedEncodingException;

/**
 * Created with IntelliJ IDEA.
 * User: Victor
 * Date: 11/13/12
 * Time: 12:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class MangoHttpResponse
{
    //URL
    public String requestUri;

    //Headers
    public String contentType = "";
    public int contentLength = 0;
    public int responseCode = 200;

    //Data
    public String charset = "UTF-8";
    public byte[] data;

    public boolean exception = false;


    @Override
    public String toString()
    {
        try
        {
            if (data != null)
                return new String(data, charset);
            return "";
        }
        catch (UnsupportedEncodingException e)
        {
            return "Unsupported character encoding: " + charset + ", " + requestUri;
        }
    }

    public Bitmap toBitmap()
    {
        Bitmap bitmap = null;

        try
        {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            options.inDither = false;

            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            return bitmap;
        }
        catch (OutOfMemoryError e)
        {
            Mango.log("MangoHttpResponse", "Mango ran out of memory while decoding a downloaded image. " + requestUri);
            Bitmap b = Bitmap.createBitmap(10, 10, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(b);
            c.drawColor(Color.MAGENTA);
            if (bitmap != null)
                bitmap.recycle();
            return b;
        }
    }

    public void writeEncodedImageToCache(int mode, String filepath, String filename)
    {
        if (data == null)
        {
            Mango.log("MangoHttpResponse", "writeEncodedImageToCache: No data to write!");
            return;
        }
        if (mode == 0)
            MangoCache.writeEncodedImageToCache(data, filepath, filename);
        else if (mode == 1)
            MangoLibraryIO.writeEncodedImageToDisk(filepath, filename, data);
        else if (mode == 2)
            MangoDecorHandler.writeDecorImageToDisk(data, filename);
    }
}
