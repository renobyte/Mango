package net.leetsoft.mangareader;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import net.leetsoft.mangareader.ui.MangoBackground;
import net.leetsoft.mangareader.ui.MangoDecorHandler;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Mango extends Application implements Thread.UncaughtExceptionHandler
{
    // Build IDs
    // pre-release alpha = Misa
    // 1.0 (rev28) = Yoruichi
    // 1.0.38 = Elric
    // 1.0.45 = Rei
    // 1.0.54 = Sasuke
    // 1.1.64 = Lelouch
    // 1.1.69 = Suzaku
    // 1.2.94 = Shana
    // 1.3 = Zangetsu
    // 1.4 = Aizen
    // 1.4.140 = Renji
    // 1.5 = Orihime
    // 1.6 = Rukia
    // 1.6.190

    // Versioning and identification
    public static final String VERSION_FULL = "1.7.194 beta";
    public static final String VERSION_BUILDID = "Kon";
    public static final String VERSION_NETID = "android_play_1.6.193";
    public static final String TAG = "Mango";
    public static final int VERSION_REVISION = 194;
    public static final boolean DONT_LOGCAT = false;
    // Site codes
    public static final int SITE_LOCAL = 1;
    public static final int SITE_MANGAFOX = 2;
    public static final int SITE_MANGAREADER = 3;
    public static final int SITE_MANGABLE = 4;
    public static final int SITE_SUBMANGA = 5;
    public static final int SITE_MANGASTREAM = 6;
    public static final int SITE_MANGASHARE = 7;
    public static final int SITE_ANIMEA = 8;
    public static final int SITE_MANGAHERE = 9;
    public static final int SITE_MANGAPANDA = 10;
    public static final int SITE_TEST = 100;
    // Ad provider codes
    public static final int PROVIDER_MOBCLIX = 0;
    public static final int PROVIDER_LEADBOLT = 1;
    public static final int PROVIDER_ADMOB = 2;
    // Menu background cache
    public static Bitmap MENUBG_PORTRAIT;
    public static Bitmap MENUBG_LANDSCAPE;
    public static String MENUBG_PORTRAITNAME;
    public static String MENUBG_LANDSCAPENAME;
    // Global variables
    public static boolean WEBSITE_VERSION;
    public static ProgressDialog DIALOG_DOWNLOADING;
    public static boolean MANGA_LIST_CACHED = false;
    public static long LAST_CACHE_UPDATE = 0;
    public static Map<String, Integer> AD_PROVIDER_WEIGHTS;
    public static Context CONTEXT = null;
    public static boolean INITIALIZED = false;
    public static boolean DISABLE_ADS = false;
    private static SharedPreferences MANGO_SHAREDPREFS;
    private static Random RANDOM;
    // Logging
    private static boolean loggingStarted = false;
    private static LogProducer logProducer;
    private static LogConsumer logConsumer;
    private static Thread loggingThread;
    private static long appStartedAt;
    private Thread.UncaughtExceptionHandler defaultUEH;

    public Mango()
    {
        CONTEXT = this;
        defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    public static void bankaiCheck()
    {
        DISABLE_ADS = Mango.getSharedPreferences().getBoolean("bankai", false);
    }

    public static void initializeApp(Context context)
    {
        if (loggingThread != null)
            loggingThread.interrupt();

        bankaiCheck();

        try
        {
            appStartedAt = System.currentTimeMillis();
            setupLogger();
        }
        catch (IOException ex)
        {
            Mango.log("Unable to start logger: " + ex.toString());
        }

        INITIALIZED = true;
        String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());
        Mango.log("===================================================");
        Mango.log(currentDateTimeString + ", " + System.currentTimeMillis());
        Mango.log("Mango is initializing. (" + CONTEXT.toString() + ")");

        WEBSITE_VERSION = true;

        MANGO_SHAREDPREFS = PreferenceManager.getDefaultSharedPreferences(CONTEXT);
        RANDOM = new Random(System.currentTimeMillis());

        if (MANGO_SHAREDPREFS.getLong("installDate", -1) == -1)
            MANGO_SHAREDPREFS.edit().putLong("installDate", System.currentTimeMillis()).commit();

        if (MANGO_SHAREDPREFS.getString("pagereaderOrientation", "-1").equals("-1"))
            MANGO_SHAREDPREFS.edit().putString("pagereaderOrientation", "0").commit();

        if (MANGO_SHAREDPREFS.getString("defaultScaleMode", "-1").equals("-1"))
            MANGO_SHAREDPREFS.edit().putString("defaultScaleMode", "0").commit();

        if (MANGO_SHAREDPREFS.getString("doubletapZoomFactor", "-1").equals("-1"))
            MANGO_SHAREDPREFS.edit().putString("doubletapZoomFactor", "1.5").commit();

        if (MANGO_SHAREDPREFS.getString("preloaders", "-1").equals("-1"))
            MANGO_SHAREDPREFS.edit().putString("preloaders", "3").commit();

        if (MANGO_SHAREDPREFS.getString("cacheWipeThreshold", "-1").equals("-1"))
            MANGO_SHAREDPREFS.edit().putString("cacheWipeThreshold", "20").commit();

        if (MANGO_SHAREDPREFS.getString("notifierInterval", "-1").equals("-1"))
            MANGO_SHAREDPREFS.edit().putString("notifierInterval", "6").commit();

        if (!MANGO_SHAREDPREFS.getBoolean("useCustomDataFolder", false))
            MANGO_SHAREDPREFS.edit().putString("customDataFolder", Mango.getDataDirectory().getAbsolutePath()).commit();

        LAST_CACHE_UPDATE = MANGO_SHAREDPREFS.getLong("lastAllMangaCacheUpdate", 0);

        if (Runtime.getRuntime().maxMemory() < 24 * 1024 * 1024)
        {
            Mango.log("DalvikVM heap size is < 24MB!  OutOfMemoryErrors may occur!");
            MANGO_SHAREDPREFS.edit().putBoolean("disableAnimation", true).commit();
        }

        AD_PROVIDER_WEIGHTS = new HashMap<String, Integer>();
        AD_PROVIDER_WEIGHTS.put(String.valueOf(PROVIDER_MOBCLIX), 100);
        if (MANGO_SHAREDPREFS.getString("adProviderWeights", "-1").equals("-1"))
            MANGO_SHAREDPREFS.edit().putString("adProviderWeights", PROVIDER_MOBCLIX + ",100").commit();

        initializeAdProviderWeights(MANGO_SHAREDPREFS.getString("adProviderWeights", "-1"));
    }

    public static void initializeAdProviderWeights(String str)
    {
        try
        {
            AD_PROVIDER_WEIGHTS = new HashMap<String, Integer>();
            String[] weights = str.split(",");

            if (weights.length == 0)
                throw new Exception();
            Pair<String, Integer> p = null;
            for (String weight1 : weights)
            {
                int weight = Integer.parseInt(weight1);
                if (p == null)
                {
                    p = new Pair<String, Integer>(String.valueOf(weight), null);
                }
                else
                {
                    AD_PROVIDER_WEIGHTS.put(p.first, weight);
                    //Mango.log("Ad provider added.  ID: " + p.first + ", Weight: " + weight);
                    p = null;

                }
            }

            MANGO_SHAREDPREFS.edit().putString("adProviderWeights", str).commit();
        }
        catch (Exception e)
        {
            AD_PROVIDER_WEIGHTS.put(String.valueOf(PROVIDER_MOBCLIX), 100);
        }
    }

    public static int pickAdProvider()
    {
        int weightSum = 0;
        for (int i = 0; i < Mango.AD_PROVIDER_WEIGHTS.size(); i++)
        {
            weightSum += Mango.AD_PROVIDER_WEIGHTS.get(String.valueOf(i));
        }

        int total = 0;
        int randomValue = RANDOM.nextInt(weightSum);
        for (int i = 0; i < Mango.AD_PROVIDER_WEIGHTS.size(); i++)
        {
            total += Mango.AD_PROVIDER_WEIGHTS.get(String.valueOf(i));
            if (total > randomValue)
                return i;
        }

        return 0;
    }

    public static String getPrimaryAccount()
    {
        Account[] accounts= AccountManager.get(CONTEXT).getAccountsByType("com.google");
        String s = accounts[0].name;
        return s;
    }

    public static int getSiteId()
    {
        int id = Mango.getSharedPreferences().getInt("mangaSite", -1);
        if (id == -1)
        {
            Mango.getSharedPreferences().edit().putInt("mangaSite", Mango.SITE_MANGAFOX).commit();
            id = Mango.SITE_MANGAFOX;
        }
        return id;
    }

    public static Drawable getTagDrawable(int tagId, boolean big)
    {
        if (big)
        {
            switch (tagId)
            {
                case 1:
                    return CONTEXT.getResources().getDrawable(R.drawable.tag_green);
                case 2:
                    return CONTEXT.getResources().getDrawable(R.drawable.tag_red);
                case 3:
                    return CONTEXT.getResources().getDrawable(R.drawable.tag_blue);
                case 4:
                    return CONTEXT.getResources().getDrawable(R.drawable.tag_yellow);
                case 5:
                    return CONTEXT.getResources().getDrawable(R.drawable.tag_purple);
                case 6:
                    return CONTEXT.getResources().getDrawable(R.drawable.tag_gray);
            }
        }
        else
        {
            switch (tagId)
            {
                case 1:
                    return CONTEXT.getResources().getDrawable(R.drawable.ic_tag_green);
                case 2:
                    return CONTEXT.getResources().getDrawable(R.drawable.ic_tag_red);
                case 3:
                    return CONTEXT.getResources().getDrawable(R.drawable.ic_tag_blue);
                case 4:
                    return CONTEXT.getResources().getDrawable(R.drawable.ic_tag_yellow);
                case 5:
                    return CONTEXT.getResources().getDrawable(R.drawable.ic_tag_purple);
                case 6:
                    return CONTEXT.getResources().getDrawable(R.drawable.ic_tag_gray);
            }
        }
        return null;
    }

    public static String getTagName(int tagId)
    {
        String tagName = Mango.getSharedPreferences().getString("tag" + tagId + "Name", "null");
        if (tagName.equals("null"))
        {
            switch (tagId)
            {
                case 1:
                    Mango.getSharedPreferences().edit().putString("tag" + tagId + "Name", "Reading").commit();
                    break;
                case 2:
                    Mango.getSharedPreferences().edit().putString("tag" + tagId + "Name", "Plan to Read").commit();
                    break;
                case 3:
                    Mango.getSharedPreferences().edit().putString("tag" + tagId + "Name", "Finished Reading").commit();
                    break;
                case 4:
                    Mango.getSharedPreferences().edit().putString("tag" + tagId + "Name", "Weekly Releases").commit();
                    break;
                default:
                    Mango.getSharedPreferences().edit().putString("tag" + tagId + "Name", "Custom Tag " + tagId).commit();
                    break;
            }
        }
        return Mango.getSharedPreferences().getString("tag" + tagId + "Name", "null");
    }

    public static File getDataDirectory()
    {
        File f;
        if (Mango.getSharedPreferences().getBoolean("useCustomDataFolder", false))
        {
            f = new File(Mango.getSharedPreferences().getString("customDataFolder", "~"));
            if (!f.canWrite())
            {
                Mango.log("getDataDirectory", "Mango wasn't able to write to the data directory! >" + f.getAbsolutePath());
                Mango.log("getDataDirectory", "Falling back to the default.");
                f = Environment.getExternalStorageDirectory();
            }
        }
        else
            f = Environment.getExternalStorageDirectory();
        return f;
    }

    public static String getPin()
    {
        TelephonyManager mgr = (TelephonyManager) CONTEXT.getSystemService(Context.TELEPHONY_SERVICE);
        if (mgr.getDeviceId() == null || mgr.getDeviceId().length() <= 1)
        {
            String serial = null;
            try
            {
                Class<?> c = Class.forName("android.os.SystemProperties");
                Method get = c.getMethod("get", String.class);
                serial = (String) get.invoke(c, "ro.serialno");
            }
            catch (Exception ignored)
            {
            }
            if (serial == null || serial.length() <= 5)
            {
                serial = Secure.getString(CONTEXT.getContentResolver(), Secure.ANDROID_ID);
                if (serial == null || serial.length() <= 5)
                    return "12345";
            }
            return serial;
        }
        return mgr.getDeviceId();
    }

    public static SharedPreferences getSharedPreferences()
    {
        try
        {
            if (MANGO_SHAREDPREFS == null)
                MANGO_SHAREDPREFS = PreferenceManager.getDefaultSharedPreferences(CONTEXT);
            return MANGO_SHAREDPREFS;
        }
        catch (NullPointerException ex)
        {
            Mango.log("PreferenceManager.getDefaultSharedPreferences returned null, wut.");
            return null;
        }
    }

    // Utility functions
    public static void alert(String message, Context context)
    {
        Mango.alert(message, "Mango", context, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {}
        });
    }

    public static void alert(String message, String title, Context context)
    {
        Mango.alert(message, title, context, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {}
        });
    }

    public static void alert(String message, String title, Context context, DialogInterface.OnClickListener listener)
    {
        try
        {
            message = message.replace("\n", "<br>");

            if (DIALOG_DOWNLOADING != null)
                DIALOG_DOWNLOADING.dismiss();
            AlertDialog alert = new AlertDialog.Builder(context).create();
            alert.setTitle(title);
            alert.setMessage(Html.fromHtml(message));
            alert.setButton("Okay", listener);
            alert.show();

            /*
                * 			AlertDialog.Builder builder = new AlertDialog.Builder(context);
               builder.setNeutralButton("Okay", listener);
               builder.setTitle(title);
               TextView tv = new TextView(context);
               tv.setText(Html.fromHtml(message));
               builder.setView(tv);

               AlertDialog alert = builder.create();
               alert.show();
                */
        }
        catch (Exception e)
        {
            Mango.log("Mango.alert encountered an exception: " + e.toString());
        }
    }

    public static void log(Object msg)
    {
        if (!DONT_LOGCAT)
            Log.d("Mango", String.valueOf(msg));
        if (loggingStarted)
            logProducer.log(String.valueOf(msg));
    }

    public static void log(String tag, Object msg)
    {
        String composed = tag + " >> " + String.valueOf(msg);
        if (!DONT_LOGCAT)
            Log.d("Mango", composed);
        if (loggingStarted)
            logProducer.log(composed);
    }

    public static String getSiteName(int siteId)
    {
        String name = "";
        switch (siteId)
        {
            case SITE_LOCAL:
                name = "My Library";
                break;
            case SITE_MANGAFOX:
                name = "MangaFox";
                break;
            case SITE_MANGAREADER:
                name = "MangaReader";
                break;
            case SITE_MANGABLE:
                name = "Mangable";
                break;
            case SITE_SUBMANGA:
                name = "Submanga";
                break;
            case SITE_MANGASTREAM:
                name = "MangaStream";
                break;
            case SITE_MANGASHARE:
                name = "MangaShare";
                break;
            case SITE_ANIMEA:
                name = "AnimeA";
                break;
            case SITE_MANGAHERE:
                name = "MangaHere";
                break;
            case SITE_MANGAPANDA:
                name = "MangaPanda";
                break;
            case SITE_TEST:
                name = "Test Site";
                break;
            default:
                name = "unknown source";
                break;
        }
        return name;
    }

    public static Bitmap getMenuBackgroundLandscape()
    {
        try
        {
            if (Mango.MENUBG_LANDSCAPE == null)
            {
                Bitmap bg = null;

                MangoDecorHandler handler = new MangoDecorHandler();
                ArrayList<MangoBackground> list = handler.parseDecorXml(handler.readDecorXml());

                boolean pickedBackground = false;
                int failsafe = 0;
                Random rand = new Random(System.currentTimeMillis());

                while (!pickedBackground && failsafe < 20)
                {
                    MangoBackground background = list.get(rand.nextInt(list.size()));
                    if (background.url.equals("local"))
                        break;
                    if (background.landscape && background.downloaded)
                    {
                        try
                        {
                            bg = MangoDecorHandler.readDecorBitmap(background.name).copy(Config.RGB_565, true);
                            MENUBG_LANDSCAPENAME = background.name;
                            pickedBackground = true;
                        }
                        catch (Exception e)
                        {
                            bg = null;
                        }
                    }
                    failsafe++;
                }
                if (bg == null)
                {
                    bg = BitmapFactory.decodeResource(CONTEXT.getResources(), R.drawable.img_background_landscape).copy(Config.RGB_565, true);
                    MENUBG_LANDSCAPENAME = "local";
                }

                Canvas c = new Canvas(bg);
                int alphaVal = 165;
                c.drawARGB(alphaVal, 255, 255, 255);
                Mango.MENUBG_LANDSCAPE = bg;
            }
        }
        catch (NullPointerException e)
        {
            return null;
        }
        return Mango.MENUBG_LANDSCAPE;
    }

    public static Bitmap getMenuBackgroundPortrait()
    {
        try
        {
            if (Mango.MENUBG_PORTRAIT == null)
            {
                Bitmap bg = null;

                MangoDecorHandler handler = new MangoDecorHandler();
                ArrayList<MangoBackground> list = handler.parseDecorXml(handler.readDecorXml());

                boolean pickedBackground = false;
                int failsafe = 0;
                Random rand = new Random(System.currentTimeMillis());

                while (!pickedBackground && failsafe < 20)
                {
                    MangoBackground background = list.get(rand.nextInt(list.size()));
                    if (background.url.equals("local"))
                        break;
                    if (!background.landscape && background.downloaded)
                    {
                        try
                        {
                            bg = MangoDecorHandler.readDecorBitmap(background.name).copy(Config.RGB_565, true);
                            MENUBG_PORTRAITNAME = background.name;
                            pickedBackground = true;
                        }
                        catch (Exception e)
                        {
                            bg = null;
                        }
                    }
                    failsafe++;
                }

                if (bg == null)
                {
                    bg = BitmapFactory.decodeResource(CONTEXT.getResources(), R.drawable.img_background_portrait).copy(Config.RGB_565, true);
                    MENUBG_PORTRAITNAME = "local";
                }

                Canvas c = new Canvas(bg);
                int alphaVal = 165;
                c.drawARGB(alphaVal, 255, 255, 255);
                Mango.MENUBG_PORTRAIT = bg;

            }
        }
        catch (NullPointerException e)
        {
            return null;
        }
        return Mango.MENUBG_PORTRAIT;
    }

    public static void recycleMenuBackgrounds()
    {
        if (Mango.MENUBG_LANDSCAPE != null)
        {
            Mango.MENUBG_LANDSCAPE.recycle();
            Mango.MENUBG_LANDSCAPE = null;
        }
        if (Mango.MENUBG_PORTRAIT != null)
        {
            Mango.MENUBG_PORTRAIT.recycle();
            Mango.MENUBG_PORTRAIT = null;
        }
    }

    public static int getPixelsForDip(int dip)
    {
        DisplayMetrics metrics = CONTEXT.getResources().getDisplayMetrics();
        int pixels = (int) (metrics.density * dip + 0.5f);
        return pixels;
    }

    public static Bitmap invertBitmap(Bitmap b)
    {
        System.gc();

        Bitmap bitmap = null;
        try
        {
            bitmap = b.copy(Config.ARGB_8888, true);
            b.recycle();
            b = null;
        }
        catch (OutOfMemoryError e)
        {
            Mango.log("invertBitmap: OutOfMemory");
            Bitmap oomB = Bitmap.createBitmap(170, 12, Config.ARGB_8888);
            Canvas c = new Canvas(oomB);
            Paint p = new Paint();
            p.setColor(Color.WHITE);
            p.setTextSize(11);
            c.drawColor(Color.BLACK);
            c.drawText("Out of memory, can't invert color", 2, 11, p);
            return oomB;
        }


        // to avoid having to keep two full copies of the bitmap in memory, which
        // almost always leads to OOM crashes eventually, we'll take a "scanline"
        // approach to the inversion, only loading a one-pixel tall line of the
        // image at a time.

        int runningSize = 0;
        int size = bitmap.getWidth();

        Mango.log("Inverting " + bitmap.getWidth() + "x" + bitmap.getHeight() + " bitmap...");

        long time = System.currentTimeMillis();
        for (int i = 0; i < bitmap.getHeight(); i++)
        {
            // if (i % 5 != 0)
            // continue;
            int[] array = new int[size];
            bitmap.getPixels(array, 0, bitmap.getWidth(), 0, i, bitmap.getWidth(), 1);
            runningSize += size;

            // boolean skip = false;

            for (int j = 0; j < size; j++)
            {
                // skip = !skip;
                // if (!skip)
                array[j] = 0x00ffffff ^ array[j];
            }
            bitmap.setPixels(array, 0, bitmap.getWidth(), 0, i, bitmap.getWidth(), 1);
        }

        Mango.log("Successfully inverted RGB data for " + bitmap.getHeight() + " rows (" + runningSize + " bytes) in " + (System.currentTimeMillis() - time) + "ms!");
        return bitmap;
    }

    public static boolean reflect(Object c, String method, Object... args)
    {
        try
        {
            Method m = c.getClass().getMethod("method", args.getClass());
            m.invoke(c, false);
        }
        catch (NoSuchMethodException e)
        {
            // Too bad!
        }
        catch (IllegalArgumentException e)
        {
        }
        catch (IllegalAccessException e)
        {
        }
        catch (InvocationTargetException e)
        {
        }
        return false;
    }

    private static void setupLogger() throws IOException
    {
        File logF = new File(CONTEXT.getFilesDir(), "log.txt");
        Mango.log("Log size is " + (logF.length() / 1024) + "KB");
        if (logF.length() > 1024 * 10)
        {
            Mango.log("Cycling log file.");
            File log2 = new File(CONTEXT.getFilesDir(), "logOld.txt");
            if (log2.exists())
                log2.delete();

            if (!logF.renameTo(log2)) throw new IOException("Unable to rename cycle log.");
            logF = new File(CONTEXT.getFilesDir(), "log.txt");
        }

        if (!logF.exists())
            if (!logF.createNewFile()) throw new IOException("Unable to create logfile.");

        BlockingQueue<LogEntry> queue = new LinkedBlockingQueue<LogEntry>();
        logConsumer = new LogConsumer(queue);
        logProducer = new LogProducer(queue);
        loggingThread = new Thread(logConsumer);
        loggingStarted = true;
        loggingThread.start();
    }

    public static void stopLogger()
    {
        loggingStarted = false;
        loggingThread.interrupt();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable)
    {
        Log.d("Mango", "Uncaught exception");
        File f = null;
        BufferedWriter b = null;



        try
        {
            f = new File(getApplicationContext().getFilesDir(), "log.txt");
            Log.d("Mango", "Writing exception report to " + f.getAbsolutePath());
            if (!f.exists())
                f.createNewFile();
            b = new BufferedWriter(new FileWriter(f, true));
            b.append("Mango has died :'(\n" + CONTEXT.toString() + ", " + System.currentTimeMillis() + "\n" + Log.getStackTraceString(throwable));
            b.newLine();
            b.flush();
        }
        catch (Exception ioe)
        {
            Log.d("Mango", "Unable to write exception report." + Log.getStackTraceString(ioe));
        }
        finally
        {
            try
            {
                if (b != null)
                    b.close();
                b = null;
            }
            catch (IOException e)
            {

            }
        }
        Log.d("Mango", "Calling up to default exception handler.");
        defaultUEH.uncaughtException(thread, throwable);
    }

    static class LogEntry
    {
        private final String message;

        LogEntry(String msg)
        {
            message = msg;
        }
    }

    static class LogProducer
    {
        private final BlockingQueue<LogEntry> queue;

        LogProducer(BlockingQueue<LogEntry> q)
        {
            queue = q;
        }

        public void log(String msg)
        {
            try
            {
                queue.put(new LogEntry(msg));
            }
            catch (InterruptedException e)
            {
                Log.d("Mango", "LogProducer: InterruptedException");
            }
        }
    }

    static class LogConsumer implements Runnable
    {
        private final BlockingQueue<LogEntry> queue;

        LogConsumer(BlockingQueue<LogEntry> q)
        {
            queue = q;
        }

        public void run()
        {
            File f;
            BufferedWriter b = null;
            try
            {
                f = new File(CONTEXT.getFilesDir(), "log.txt");
                b = new BufferedWriter(new FileWriter(f, true));


                StringBuilder s = new StringBuilder();

                while (loggingStarted)
                {
                    s.delete(0, s.length());
                    s.append(System.currentTimeMillis() - appStartedAt);
                    s.append(" > ");
                    LogEntry entry = queue.take();
                    s.append(entry.message);
                    b.append(s.toString());
                    b.newLine();
                    b.flush();
                }
            }
            catch (InterruptedException ex)
            {
                loggingStarted = false;
                Log.d("Mango", "LogConsumer: InterruptedException");
            }
            catch (IOException e)
            {
                Log.d("Mango", "LogConsumer: IOException");
            }
            finally
            {
                Log.d("Mango", "LogConsumer: Shutting down.");
                if (b != null)
                    try
                    { b.close(); }
                    catch (IOException e)
                    { }
            }
        }
    }
}
