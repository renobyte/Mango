package net.leetsoft.mangareader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import net.leetsoft.mangareader.ui.MangoDecorHandler;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.util.zip.GZIPInputStream;

public class MangoHttp
{
    private static int BUFFER_SIZE = 8192;
    private static int CONNECTION_TIMEOUT = 10000;
    private static int SOCKET_TIMEOUT = 6000;


    public static boolean isOfflineMode()
    {
        return Mango.getSharedPreferences().getBoolean("offlineMode", false);
    }

    public static boolean isWifi(Context context)
    {
        ConnectivityManager mgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = mgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        return info.isConnected();
    }

    public static MangoHttpResponse downloadData(String url, Context c)
    {
        MangoHttpResponse response = new MangoHttpResponse();
        url = url.replace("%SERVER_URL%", Mango.getSharedPreferences().getString("serverUrl", "konata.leetsoft.net"));
        url = cleanString(url);

        if (url.contains("mangable") && !url.endsWith("?mango"))
            url += "?mango";

        try
        {
            response.requestUri = url;
            Mango.log("MangoHttp", "Requesting '" + url + "' [" + url.hashCode() + "]...");

            if (!MangoHttp.checkConnectivity(c))
                throw new Exception("No connection to the Internet is available.  Check your mobile data or WiFi connection.");
            if (isOfflineMode())
                throw new Exception("Mango is in offline mode. To disable offline mode, return to the main menu and press the Back key, then restart the app.");

            HttpGet httpGet = new HttpGet(url);
            HttpParams httpParameters = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(httpParameters, CONNECTION_TIMEOUT);
            HttpConnectionParams.setSoTimeout(httpParameters, SOCKET_TIMEOUT);
            HttpConnectionParams.setSocketBufferSize(httpParameters, BUFFER_SIZE);

            DefaultHttpClient httpClient = new DefaultHttpClient(httpParameters);
            HttpResponse httpResponse = httpClient.execute(httpGet);
            HttpEntity entity = httpResponse.getEntity();

            Header contentEncoding = httpResponse.getFirstHeader("Content-Encoding");
            if (contentEncoding != null && contentEncoding.getValue().equalsIgnoreCase("gzip"))
                throw new Exception("The server's response was gzip-encoded, which is not currently supported.  Please contact the developer.");

            response.responseCode = httpResponse.getStatusLine().getStatusCode();
            if (response.responseCode != 200)
                throw new Exception("HTTP " + response.responseCode + ": " + (response.responseCode == 404 ? "Not Found\nThe requested file wasn't found on the server." : "Mango couldn't access the requested file because the server returned an error.") + "Url: " + url);
            if (httpResponse.containsHeader("Content-Type"))
                response.contentType = httpResponse.getFirstHeader("Content-Type").getValue();
            if (httpResponse.containsHeader("Content-Length"))
                response.contentLength = Integer.parseInt(httpResponse.getFirstHeader("Content-Type").getValue());

            response.charset = EntityUtils.getContentCharSet(entity);
            if (response.charset == null)
                response.charset = "UTF-8";
            response.data = EntityUtils.toByteArray(entity);
        }
        catch (Exception ex)
        {
            Mango.log("MangoHttp", "Exception: " + ex.getMessage() +" [" + url.hashCode() + "]");
            response.data = ex.getMessage().getBytes();
            response.exception = true;
        }
        catch (OutOfMemoryError oom)
        {
            Mango.log("MangoHttp", "Ran into an OutOfMemoryError. [" + url.hashCode() + "]");
            System.gc();
            response.data = "Mango has run out of heap memory and can't download this data.  Please try restarting the app.".getBytes();
            response.exception = true;
        }
        finally
        {
            return response;
        }
    }

    public static String downloadHtml(String url, Context context)
    {
        url = url.replace("%SERVER_URL%", Mango.getSharedPreferences().getString("serverUrl", "konata.leetsoft.net"));

        InputStream is = null;
        InputStreamReader isr;

        url = cleanString(url);

        if (url.contains("mangable") && !url.endsWith("?mango"))
            url += "?mango";

        try
        {
            Mango.log("MangoHttp", "Requesting " + url + "");

            if (!MangoHttp.checkConnectivity(context))
                throw new Exception("No mobile data connectivity.");
            if (isOfflineMode())
                throw new Exception("Mango is in offline mode. To disable offline mode, return to the main menu and press the Back key, then restart the app.");

            HttpGet httpGet = new HttpGet(url);
            HttpParams httpParameters = new BasicHttpParams();
            int timeoutConnection = 12000;
            int timeoutSocket = 12000;
            HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
            HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
            HttpConnectionParams.setSocketBufferSize(httpParameters, 8192);

            DefaultHttpClient httpClient = new DefaultHttpClient(httpParameters);
            //httpGet.addHeader("Referer", url);
            //httpGet.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/536.11 (KHTML, like Gecko) Chrome/20.0.1132.34 Safari/536.11");

            HttpResponse response = httpClient.execute(httpGet);
            HttpEntity entity = response.getEntity();

            boolean gzip = false;

            Header contentEncoding = response.getFirstHeader("Content-Encoding");
            if (contentEncoding != null && contentEncoding.getValue().equalsIgnoreCase("gzip"))
                gzip = true;

            is = entity.getContent();

            if (is == null)
            {
                return "Exception: No response from server.";
            }

            StringBuilder content = new StringBuilder();

            if (gzip)
            {
                Mango.log("MangoHttp", "gzip encoding up in this mofo");
                GZIPInputStream gin = new GZIPInputStream(is);
                int charRead;
                byte[] inputBuffer = new byte[BUFFER_SIZE];

                while ((charRead = gin.read(inputBuffer)) > 0)
                {
                    // TODO: calling trim() isn't really the right way to do this since it would fuck up instances
                    // where there is legitimately a space at the beginning or end. a better solution would
                    // be to only decode 'charRead' bytes from the string, but I've got no idea how to do that
                    content.append(new String(inputBuffer, "UTF-8").trim());
                    inputBuffer = new byte[BUFFER_SIZE];
                }
            }
            else
            {
                isr = new InputStreamReader(is, "UTF-8");

                int charRead;
                char[] inputBuffer = new char[BUFFER_SIZE];

                while ((charRead = isr.read(inputBuffer)) > 0)
                {
                    content.append(inputBuffer, 0, charRead);
                    inputBuffer = new char[BUFFER_SIZE];
                }
            }

            if (response.getStatusLine().getStatusCode() >= 400) // check if response code is 4xx or 5xx
                return "Exception: HTTP " + response.getStatusLine().getStatusCode() + (response.getStatusLine().getStatusCode() == 404 ? " - No page exists at " + url : ".\n" + content.toString()) + ".\n";

            return content.toString();
        }
        catch (OutOfMemoryError oom)
        {
            Mango.log("MangoHttp", "OutOfMemoryError in downloadHtml.  Calling System.gc.");
            System.gc();
            return "Exception: OutOfMemoryError!  Mango is running low on heap memory... try restarting the app.";
        }
        catch (Exception ex)
        {
            Mango.log("MangoHttp", "Exception: " + ex.toString() + "\nStack Trace: " + Log.getStackTraceString(ex));
            return "Exception: " + ex.toString();
        }
        finally
        {
            if (is != null)
                try
                {
                    is.close();
                }
                catch (IOException e)
                {
                }
            is = null;
        }
    }

    public static Object downloadBitmap(String url, Context context)
    {
        url = url.replace("%SERVER_URL%", Mango.getSharedPreferences().getString("serverUrl", "konata.leetsoft.net"));

        InputStream is = null;
        BufferedInputStream bis = null;
        ByteArrayOutputStream dataStream = null;
        BufferedOutputStream out = null;
        byte[] buffer = new byte[BUFFER_SIZE];

        if (url.contains("mangable") && !url.endsWith("?mango"))
            url += "?mango";

        url = cleanString(url);

        try
        {
            Mango.log("MangoHttp", "Requesting " + url + "");

            if (!MangoHttp.checkConnectivity(context))
                throw new Exception("No mobile data connectivity.");
            if (isOfflineMode())
                throw new Exception("Mango is in offline mode. To disable offline mode, return to the main menu and press the Back key, then restart the app.");

            HttpGet httpGet = new HttpGet(url);
            HttpParams httpParameters = new BasicHttpParams();
            int timeoutConnection = 4000;
            HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
            int timeoutSocket = 6000;
            HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);

            DefaultHttpClient httpClient = new DefaultHttpClient(httpParameters);
            //httpGet.addHeader("Referer", url);
            //httpGet.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/536.11 (KHTML, like Gecko) Chrome/20.0.1132.34 Safari/536.11");

            HttpResponse response = httpClient.execute(httpGet);
            HttpEntity entity = response.getEntity();

            is = entity.getContent();

            if (is == null)
                return "Exception: No response from server.";

            bis = new BufferedInputStream(is, BUFFER_SIZE);
            dataStream = new ByteArrayOutputStream();
            out = new BufferedOutputStream(dataStream, BUFFER_SIZE);

            int bytesRead = 0;
            while (bytesRead != -1)
            {

                bytesRead = bis.read(buffer);
                if (bytesRead != -1)
                    out.write(buffer, 0, bytesRead);
            }
            out.flush();

            byte[] data;
            Bitmap bitmap;
            data = dataStream.toByteArray();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;

            options.inDither = false;
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            return bitmap;

        }
        catch (OutOfMemoryError oom)
        {
            Mango.log("MangoHttp", "Mango went /oom. Blame the tank.");
            System.gc();
            return "Exception (" + oom.getClass().getSimpleName() + "): " + oom.getMessage();
        }
        catch (Exception ex)
        {
            Mango.log("MangoHttp", "(Bitmap) EXCEPTION! (" + ex.getClass() + "): " + ex.getMessage());
            return "Exception (" + ex.getClass().getSimpleName() + "): " + ex.getMessage();
        }
        finally
        {
            try
            {
                bis.close();
                bis = null;
            }
            catch (Exception e)
            {

            }
            try
            {
                dataStream.close();
                dataStream = null;
            }
            catch (Exception e)
            {

            }
            try
            {
                out.close();
                out = null;
            }
            catch (Exception e)
            {

            }
        }
    }

    public static String downloadEncodedImage(String url, final String filepath, final String filename, int mode, Context context)
    {
        if (url == null)
            url = "";
        url = url.replace("%SERVER_URL%", Mango.getSharedPreferences().getString("serverUrl", "konata.leetsoft.net"));

        InputStream is = null;
        BufferedInputStream bis = null;
        ByteArrayOutputStream dataStream = null;
        BufferedOutputStream out = null;
        byte[] buffer = new byte[BUFFER_SIZE];

        if (url.contains("mangable") && !url.endsWith("?mango"))
            url += "?mango";

        url = cleanString(url);

        try
        {
            // Mango.Log("MangoHttp", "Connecting... (Request 0x" + Integer.toHexString(url.hashCode()) + ")");
            Mango.log("MangoHttp", "Requesting " + url + "");

            if (!MangoHttp.checkConnectivity(context))
                throw new Exception("No mobile data connectivity.");
            if (Mango.getSharedPreferences().getBoolean("offlineMode", false))
                throw new Exception("Mango is in offline mode. To disable offline mode, return to the main menu and press the Back key, then restart the app.");

            HttpGet httpGet = new HttpGet(url);
            HttpParams httpParameters = new BasicHttpParams();
            int timeoutConnection = 5000;
            HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
            int timeoutSocket = 6000;
            HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);

            DefaultHttpClient httpClient = new DefaultHttpClient(httpParameters);
            //httpGet.addHeader("Referer", url);
            //httpGet.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/536.11 (KHTML, like Gecko) Chrome/20.0.1132.34 Safari/536.11");

            HttpResponse response = httpClient.execute(httpGet);
            HttpEntity entity = response.getEntity();

            if (response.getStatusLine().getStatusCode() >= 400) // check if response code is 4xx or 5xx
                return "Exception: HTTP " + response.getStatusLine().getStatusCode() + (response.getStatusLine().getStatusCode() == 404 ? " - No image exists at " + url : "") + ".\n";

            is = entity.getContent();

            if (is == null)
                return "Exception: No response from server.";

            bis = new BufferedInputStream(is, BUFFER_SIZE);
            dataStream = new ByteArrayOutputStream();
            out = new BufferedOutputStream(dataStream, BUFFER_SIZE);

            //Mango.Log("MangoHttp", "Reading image data into buffer... (Request 0x" + Integer.toHexString(url.hashCode()) + ")");
            //long time = System.currentTimeMillis();
            int bytesRead = 0;
            while (bytesRead != -1)
            {

                bytesRead = bis.read(buffer);
                if (bytesRead != -1)
                    out.write(buffer, 0, bytesRead);
            }
            out.flush();
            //Mango.Log("MangoHttp", "Image data downloaded successfully in " + String.valueOf(System.currentTimeMillis() - time) + "ms. Saving...");
            byte[] data;
            data = dataStream.toByteArray();
            if (mode == 0)
                MangoCache.writeEncodedImageToCache(data, filepath, filename);
            else if (mode == 1)
                MangoLibraryIO.writeEncodedImageToDisk(filepath, filename, data);
            else if (mode == 2)
                MangoDecorHandler.writeDecorImageToDisk(data, filename);
            return "ok";

        }
        catch (Exception ex)
        {
            Mango.log("MangoHttp", "(EncodedImage) EXCEPTION! (" + ex.getClass() + "): " + ex.getMessage());
            return "Exception (" + ex.getClass().getSimpleName() + "): " + ex.getMessage();
        }
        finally
        {
            try
            {
                bis.close();
                bis = null;
            }
            catch (Exception e)
            {

            }
            try
            {
                dataStream.close();
                dataStream = null;
            }
            catch (Exception e)
            {

            }
            try
            {
                out.close();
                out = null;
            }
            catch (Exception e)
            {

            }
        }
    }

    public static String cleanString(String str)
    {
        str = str.replace("[", "%5B");
        str = str.replace("]", "%5D");
        str = str.replace(" ", "%20");
        return str;
    }

    public static boolean checkConnectivity(Context context)
    {
        try
        {
            ConnectivityManager mgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = mgr.getActiveNetworkInfo();

            if (info == null || !info.isConnected() || !info.isAvailable())
                return false;
        }
        catch (Exception e)
        {
            Mango.log("checkConnectivity hit an exception: " + Log.getStackTraceString(e));
        }
        return true;
    }
}
