package net.leetsoft.mangareader;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

public class MangoHttp
{
    private static int BUFFER_SIZE = 8192;
    private static int CONNECTION_TIMEOUT = 7000;
    private static int SOCKET_TIMEOUT = 11000;


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
        url = url.replace("%SERVER_URL%", Mango.getSharedPreferences().getString("serverUrl", "kagami.leetsoft.net"));
        url = cleanString(url);

        if (url.contains("mangable") && !url.endsWith("?mango"))
            url += "?mango";

        try
        {
            response.requestUri = url;
            Mango.log("MangoHttp", "Requesting '" + url + "' [" + url.hashCode() + "]...");

            if (!MangoHttp.checkConnectivity(c))
                throw new Exception("No connection to the Internet is available.  Check your mobile data or Wi-Fi connection.");
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
                throw new Exception("HTTP " + response.responseCode + ": " + (response.responseCode == 404 ? "Not Found\nThe requested file wasn't found on the server." : "Mango couldn't access the requested file because the server returned an error.") + " Url: " + url);
            if (httpResponse.containsHeader("Content-Type"))
                response.contentType = httpResponse.getFirstHeader("Content-Type").getValue();
            if (httpResponse.containsHeader("Content-Length"))
                response.contentLength = Integer.parseInt(httpResponse.getFirstHeader("Content-Length").getValue());

            response.charset = EntityUtils.getContentCharSet(entity);
            if (response.charset == null)
                response.charset = "UTF-8";
            response.data = EntityUtils.toByteArray(entity);

            if (response.data == null)
                throw new Exception("The response from the server was empty.  Please try your request again.  If the issue reoccurs, the server may be experiencing technical difficulties.");
        }
        catch (Exception ex)
        {
            Mango.log("MangoHttp", "<!> " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getClass().getSimpleName() + " - " +  ex.getMessage()) + " [" + url.hashCode() + "]");
            response.data = (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()).getBytes();
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
