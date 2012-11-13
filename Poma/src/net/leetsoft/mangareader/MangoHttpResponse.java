package net.leetsoft.mangareader;

import android.graphics.Bitmap;

/**
 * Created with IntelliJ IDEA.
 * User: Victor
 * Date: 11/13/12
 * Time: 12:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class MangoHttpResponse
{
    String requestUri;
    String responseUri;

    String headers;
    int responseCode;

    byte[] data;

    public String decodeDataAsString()
    {
        return "";
    }

    public Bitmap decodeDataAsBitmap()
    {
        return null;
    }

    public String writeEncodedImageToCache()
    {

        return "ok";
    }
}
