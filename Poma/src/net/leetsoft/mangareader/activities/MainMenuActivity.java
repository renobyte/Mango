package net.leetsoft.mangareader.activities;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import net.leetsoft.mangareader.*;
import net.leetsoft.mangareader.services.NotifierService;
import net.leetsoft.mangareader.ui.MangoAdWrapperView;
import net.leetsoft.mangareader.ui.MangoDecorHandler;
import net.leetsoft.mangareader.ui.MangoTutorialHandler;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class MainMenuActivity extends MangoActivity
{
    MenuChoice[] MENU_CHOICES = new MenuChoice[]{new MenuChoice("Browse Manga", 0, R.drawable.ic_book_open),
            new MenuChoice("My Library", 1, R.drawable.ic_libraryseries),
            new MenuChoice("Favorites", 2, R.drawable.ic_favorites),
            new MenuChoice("History", 3, R.drawable.ic_history),
            new MenuChoice("Settings and Help", 4, R.drawable.ic_options)};

    class MenuChoice
    {
        String text;
        int id;
        int icon;

        MenuChoice(String t, int i, int iconId)
        {
            id = i;
            text = t;
            icon = iconId;
        }
    }

    Alert[] ALERTS;

    class Alert
    {
        String text;
        String urlToLaunch;
        Bitmap icon;

        Alert(String t, String url, Bitmap i)
        {
            text = t;
            urlToLaunch = url;
            icon = i;
        }
    }

    String[] TIPS = new String[]{"Don't like how pages keep scrolling after you lift your finger? Enable 'Reduce Scroll Momentum' from Preferences.",
            "When you add a manga to your Favorites, Mango will automatically track your progress as you read!",
            "Select Send Feedback from the menu to get in touch with Mango's developer. It even goes straight to his phone! :]",
            "Have friends who like manga? Tell them about Mango! (also available for BlackBerry!)",
            "Use the History screen to quickly resume reading a manga you don't have in your Favorites.",
            "Turn on Sticky Zoom from the General Settings menu to retain the zoom level when switching pages.",
            "Become a fan of Mango on Facebook and learn about new features and development progress!\n<www.facebook.com/MangoApp>",
            "Have a suggestion? See something you think could be improved? Let me know via Send Feedback from the menu!",
            "Hate ads? Upgrade to Mango Bankai to get rid of them and support the developer at the same time! ;)",
            "Save chapters to your Library so you can read them later without an internet connection!",
            "Going on a trip? Don't forget to save a bunch of manga to your Library so that you'll have something to read on the plane. :P",
            "Please support mangaka and publishers by buying official manga volumes when they're licensed in English!",
            "Support English publishers and mangaka by buying licensed manga! Besides, even Mango isn't as good as a real book. ;P",
            "Share a cool page with your friends on Facebook or Twitter by going to Menu >> More >> Share!",
            "Enable Notifications to have Mango automatically check for new chapters of your favorite manga!",
            "Mango is also available on BlackBerry. World domination is imminent!",
            "Support Japanese mangaka and English publishers by buying licensed manga when it's available in your local bookstore!",
            "Want to get in touch with the developer of Mango? Use the Send Feedback function to send a message straight to his phone!",
            "Mango's Advanced Search is really powerful! Use it to include or exclude certain genres and to search through manga summaries!",
            "Sick of Bleach, Naruto, and One Piece? Use the Advanced Search feature to find new manga from genres you like!",
            "The Mango Service checks for new chapters every hour. If a brand new chapter doesn't show up, just wait a bit for it to be discovered.",
            "The Mango Service checks for new manga daily at 3:00 AM EST. If a new manga is not in the All Manga list yet, it should be there by the next day."};

    private TextSwitcher tipSwitcher;
    private TextSwitcher alertSwitcher;
    private ImageView alertIcon;
    private RelativeLayout alertLayout;

    private ListView mainMenuList;

    private Random rand = new Random(System.currentTimeMillis());

    private Handler tipRotator = new Handler();
    private Runnable tipRotateTask = new Runnable()
    {
        @Override
        public void run()
        {
            tipSwitcher.setText("\n" + TIPS[rand.nextInt(TIPS.length - 1)]);
            tipRotator.postDelayed(this, 8000);
        }
    };
    private Handler alertRotator = new Handler();
    private Runnable alertRotateTask = new Runnable()
    {
        @Override
        public void run()
        {

            try
            {
                if (activeAlert == -1)
                    return;
                activeAlert++;
                if (activeAlert >= ALERTS.length)
                    activeAlert = 0;
                alertSwitcher.setText(ALERTS[activeAlert].text);
                alertIcon.setImageBitmap(ALERTS[activeAlert].icon);
            }
            catch (ArrayIndexOutOfBoundsException ex)
            {
                alertSwitcher.setText("ArrayIndexOutOfBoundsException: Error loading server alerts.");
                alertIcon.setImageBitmap(null);
            }
            alertRotator.removeCallbacks(alertRotateTask);
            alertRotator.postDelayed(this, 9000);
        }
    };
    private int activeAlert = -1;

    private boolean mUpdateAvailable = false;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setTitle("Mango", "v" + Mango.VERSION_FULL + " (" + Mango.VERSION_BUILDID + ")");

        // Initialize UI stuff
        inflateLayoutManager(this, R.layout.mainmenu);
        super.setAdLayout((MangoAdWrapperView) findViewById(R.id.mainmenuAdLayout));
        super.setJpBackground(R.drawable.jp_bg_mainmenu);

        tipSwitcher = (TextSwitcher) View.inflate(this, R.layout.tipswitcher, null);
        tipSwitcher.setInAnimation(this, android.R.anim.fade_in);
        tipSwitcher.setOutAnimation(this, android.R.anim.fade_out);

        alertLayout = (RelativeLayout) View.inflate(this, R.layout.alertswitcher, null);
        alertSwitcher = (TextSwitcher) alertLayout.findViewById(R.id.alertSwitcher);
        alertSwitcher.setInAnimation(this, android.R.anim.fade_in);
        alertSwitcher.setOutAnimation(this, android.R.anim.fade_out);
        alertIcon = (ImageView) alertLayout.findViewById(R.id.alertIcon);

        mainMenuList = (ListView) findViewById(R.id.MainMenuList);
        mainMenuList.addFooterView(alertLayout);
        alertLayout.setVisibility(View.GONE);
        mainMenuList.addFooterView(tipSwitcher);
        mainMenuList.setAdapter(new MainMenuAdapter(this));
        mainMenuList.setOnItemClickListener(new OnItemClickListener()
        {

            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id)
            {
                int itemId = -1;
                try
                {
                    itemId = MENU_CHOICES[position].id;
                }
                catch (Exception e)
                {
                    // they clicked on a serveralert
                }
                if (itemId == 0)
                {
                    Intent myIntent = new Intent();
                    myIntent.putExtra("finishOnSelect", false);
                    myIntent.setClass(Mango.CONTEXT, SiteSelectorActivity.class);
                    startActivity(myIntent);
                }
                if (itemId == 1)
                {
                    Intent myIntent = new Intent();
                    myIntent.setClass(Mango.CONTEXT, LibraryBrowserActivity.class);
                    startActivity(myIntent);
                }
                else if (itemId == 3)
                {
                    Intent myIntent = new Intent();
                    myIntent.setClass(Mango.CONTEXT, HistoryActivity.class);
                    startActivity(myIntent);
                }
                else if (itemId == 4)
                {
                    Intent myIntent = new Intent();
                    myIntent.setClass(Mango.CONTEXT, SettingsMenuActivity.class);
                    startActivity(myIntent);
                }
                else if (itemId == 2)
                {
                    Intent myIntent = new Intent();
                    myIntent.setClass(Mango.CONTEXT, FavoritesActivity.class);
                    startActivity(myIntent);
                }
                else if (itemId == 50)
                {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    MangoHttpResponse resp = MangoHttp.downloadData("http://%SERVER_URL%/getupdateurl.aspx?ver=" + Mango.VERSION_NETID, MainMenuActivity.this);
                    String url;
                    if (resp.exception)
                        url = "http://Mango.leetsoft.net/install-android.php";
                    else
                        url = resp.toString();
                    intent.setData(Uri.parse(url));
                    startActivity(intent);
                    overridePendingTransition(R.anim.fadein, R.anim.expandout);
                }
                else if (itemId == -1)
                {
                    try
                    {
                        HashMap<String, String> parameters = new HashMap<String, String>();
                        parameters.put("Url", String.valueOf(ALERTS[activeAlert].urlToLaunch));
                        MainMenuActivity.this.logEvent("Click ServerAlert", parameters);

                        if (ALERTS[activeAlert].urlToLaunch.contains("bankai.php"))
                        {
                            Intent myIntent = new Intent();
                            myIntent.setClass(Mango.CONTEXT, BankaiActivity.class);
                            startActivity(myIntent);
                        }
                        else
                        {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(ALERTS[activeAlert].urlToLaunch));
                            startActivity(intent);
                        }
                        overridePendingTransition(R.anim.fadein, R.anim.expandout);
                    }
                    catch (Exception ex)
                    {
                        Mango.alert("Mango wasn't able to open the link. :'(\n\nYou can manually type it into the Browser instead:\n" + ALERTS[activeAlert].urlToLaunch, MainMenuActivity.this);
                    }
                }
            }
        });

        // Show tutorial if needed
        if (!Mango.getSharedPreferences().getBoolean("tutorial" + MangoTutorialHandler.MAIN_MENU + "Done", false))
            MangoTutorialHandler.startTutorial(MangoTutorialHandler.MAIN_MENU, this);

        // Bankai nag
        if (!Mango.DISABLE_ADS && Mango.getSharedPreferences().getLong("nextNag", System.currentTimeMillis() - 1) < System.currentTimeMillis()
                && Mango.getSharedPreferences().getInt("chaptersRead", 0) > 30)
        {
            View.OnClickListener l = new View.OnClickListener()
            {

                @Override
                public void onClick(View v)
                {
                    Intent myIntent = new Intent();
                    myIntent.setClass(Mango.CONTEXT, BankaiActivity.class);
                    startActivity(myIntent);
                }
            };
            setToast("Want to get rid of ads, and support Mango's developer at the same time?  Tap here to upgrade to Mango Bankai!", l, true);
            showToast(20000);
        }

        // Display update available message
        Bundle arguments = getIntent().getExtras();
        if (arguments != null && arguments.getBoolean("updateavailable"))
        {
            mUpdateAvailable = true;
            Toast toast = Toast.makeText(MainMenuActivity.this, "An update for Mango is available!", Toast.LENGTH_LONG);
            toast.show();
            MenuChoice[] temp = new MenuChoice[MENU_CHOICES.length + 1];
            for (int i = 0; i < MENU_CHOICES.length; i++)
            {
                temp[i] = MENU_CHOICES[i];
            }
            temp[MENU_CHOICES.length] = new MenuChoice("Update available!", 50, R.drawable.ic_error);
            MENU_CHOICES = temp;
            mainMenuList.setAdapter(new MainMenuAdapter(this));
        }

        // Download server alerts
        if (Mango.getSharedPreferences().getLong("nextAlertCheck", 0) < System.currentTimeMillis() || !MangoCache.checkCacheForData("serveralerts.txt"))
        {
            DownloadAlertsTask task = new DownloadAlertsTask(this);
            task.execute("http://www.leetsoft.net/mangoweb/alerts/" + Mango.VERSION_BUILDID + ".txt");
        }

        // Check for new decor backgrounds
        if (Mango.getSharedPreferences().getLong("nextDecorCheck", 0) < System.currentTimeMillis())
        {
            DecorDownloader d = new DecorDownloader();
            d.execute((Void[]) null);
        }

        // Parse serveralerts
        if (MangoCache.checkCacheForData("serveralerts.txt"))
            parseAlerts();

        // Check for Mango import
        File f = new File(Mango.getDataDirectory() + "/PocketManga");

        if (f.exists())
        {
            try
            {
                f = new File(Mango.getDataDirectory() + "/PocketManga/library");
                int pomaSize = 0;
                if (f.exists())
                    pomaSize = f.list().length;
                f = new File(Mango.getDataDirectory() + "/Mango/library");
                int mangoSize = 0;
                if (f.exists())
                    mangoSize = f.list().length;

                if (pomaSize > mangoSize)
                {
                    f = new File(Mango.getDataDirectory() + "/Mango");
                    boolean retval = f.renameTo(new File(Mango.getDataDirectory() + "/MangoOld"));
                    if (!retval)
                        throw new IOException("Unable to rename " + f.getAbsolutePath() + " to MangoOld.  (File.renameTo returned false)");
                    Mango.log("Renamed /Mango/ to /MangoOld/.");

                    f = new File(Mango.getDataDirectory() + "/PocketManga");
                    retval = f.renameTo(new File(Mango.getDataDirectory() + "/Mango"));
                    if (!retval)
                        throw new IOException("Unable to rename " + f.getAbsolutePath() + " to Mango.  (File.renameTo returned false)");
                    Mango.log("Renamed /PocketManga/ to /Mango/.");
                }
            }
            catch (Exception e)
            {
                Mango.log("There was a problem performing folder migration: " + Log.getStackTraceString(e));
            }
        }

        // Make sure Notifier isn't stuck in the past
        long time = Mango.getSharedPreferences().getLong("notifierNextRun", 0);
        if (time < System.currentTimeMillis() && Mango.getSharedPreferences().getBoolean("notifierEnabled", false))
        {
            long ONE_HOUR = 1000 * 60 * 60;
            long interval = ONE_HOUR * Integer.parseInt(Mango.getSharedPreferences().getString("notifierInterval", "6"));
            Intent intent = new Intent(this, NotifierService.class);
            intent.setAction("UPDATECHECK");
            PendingIntent pending = PendingIntent.getService(this, 0, intent, 0);
            AlarmManager alarm = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            alarm.cancel(pending);
            Mango.log("Notifier next run time has already passed.  Re-scheduling notifier for 30 seconds from now.");
            Mango.getSharedPreferences().edit().putLong("notifierNextRun", System.currentTimeMillis() + (1000 * 30)).commit();
            alarm.setRepeating(AlarmManager.RTC, System.currentTimeMillis() + (1000 * 30), interval, pending);
        }

        // Make sure we can write data to our data directory
        MangoCache.writeDataToCache(Mango.CONTEXT.toString(), "iocheck");
        if (MangoCache.readDataFromCache("iocheck") == null || !MangoCache.readDataFromCache("iocheck").equals(Mango.CONTEXT.toString()))
            Mango.alert(
                    "Mango cannot write to the cache folder!  It's possible that your SD card is full, mounted, or write-locked.  You will not be able to read manga until Mango can access the cache folder again.\n\n<strong>Possible Solutions:</strong>\n<small>-Unmount the SD card, if it is mounted\n-Unplug your device if it is plugged into a computer\n-Make sure the SD card isn't full\n-Restart your device</small>",
                    "Error", MainMenuActivity.this);
        else if (MangoCache.getFreeSpace() < 20 && MangoCache.getFreeSpace() > 2)
            Mango.alert(
                    "Your external storage is almost full! ("
                            + ((int) MangoCache.getFreeSpace())
                            + "MB remaining)\n\nIf it becomes full, Mango probably won't function properly.\n\nTry to delete some stuff from your external storage, such as camera photos, music, or My Library chapters until you're above 20MB of available space.",
                    "Warning!", MainMenuActivity.this);
        else if (MangoCache.getFreeSpace() <= 2)
            Mango.alert(
                    "Your external storage is full! Mango probably won't work properly.\n\nTry deleting some stuff from your external storage, such as camera photos, music, or My Library chapters to free up space.",
                    "Warning!", MainMenuActivity.this);

        // Flurry opt in
        if (!Mango.getSharedPreferences().getBoolean("popupEnableFlurry", false))
        {
            AlertDialog alert = new AlertDialog.Builder(MainMenuActivity.this).create();
            alert.setTitle("Enable Analytics?");
            alert.setMessage("Would you like to enable Flurry Analytics?\n\nThis will help make Mango even more awesome in the future by anonymously sending usage statistics and crash reports.  No information about the manga you read or download is collected.\n\nYou can change this setting at any time from Preferences.");
            alert.setButton(DialogInterface.BUTTON_POSITIVE, "Sure", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    Mango.getSharedPreferences().edit().putBoolean("analyticsEnabled", true).commit();
                    Mango.getSharedPreferences().edit().putBoolean("popupEnableFlurry", true).commit();
                }
            });
            alert.setButton(DialogInterface.BUTTON_NEGATIVE, "Nah", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    Mango.getSharedPreferences().edit().putBoolean("enableAnalytics", false).commit();
                    Mango.getSharedPreferences().edit().putBoolean("popupEnableFlurry", true).commit();
                }
            });
            alert.show();
        }

        // Terms of Service
        if (!Mango.getSharedPreferences().getBoolean("termsRead", false))
        {
            TermsDialog terms = new TermsDialog(this);
            terms.show();
            terms.startDownloadingTerms(this);
            Mango.getSharedPreferences().edit().putInt("lastInstalledRevision", Mango.VERSION_REVISION).commit();
            return;
        }

        // Changelog popup
        if (Mango.getSharedPreferences().getInt("lastInstalledRevision", -1) != Mango.VERSION_REVISION)
        {
            StringBuilder changelog = new StringBuilder();
            changelog.append("<b>New in v1.6.183</b><br>");
            changelog.append("<small>");
            changelog.append("<b>-Added: option to disable double-tap zoom</b><br>");
            changelog.append("A new option in the preferences screen allows you to turn off the double-tap zoom.  Single-tapping to change a page will be faster as a result.<br><br>");
            changelog.append("<b>-Added: My Library loads much more quickly</b><br>");
            changelog.append("Reduced the loading time for the My Library screen by about 60-70%.  Users with very large libraries should especially notice improved performance.<br><br>");
            changelog.append("<b>-Added: Optimizations to Pagereader</b><br>");
            changelog.append("Reduced the delay between changing pages slightly, from about 350-400ms to 250-300ms on my Galaxy S3.  More performance improvements will be added soon.<br><br>");
            changelog.append("<b>-Fixed: My Library/Downloader crashes</b><br>");
            changelog.append("Fixed an issue where the downloader would sometimes crash while downloading a chapterlist.<br><br>");
            changelog.append("<b>-Fixed: Jump-to-Page crashes</b><br>");
            changelog.append("Fixed sparodic jump-to-page crashes on some devices.<br><br>");
            changelog.append("<b>-Changed: ad network</b><br>");
            changelog.append("Added support for a few different ad networks to reduce my reliance upon a single network (who've had issues paying lately)<br><br>");
            changelog.append("</small>");
            // MangoCache.writeDataToCache(changelog.toString(), "changelog");
            Mango.alert(changelog.toString(), "What's new in this update?", MainMenuActivity.this, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    Mango.getSharedPreferences().edit().putInt("lastInstalledRevision", Mango.VERSION_REVISION).commit();
                }
            });
        }
    }

    @Override
    public void onDestroy()
    {
        tipRotator.removeCallbacks(tipRotateTask);
        alertRotator.removeCallbacks(alertRotateTask);
        super.onDestroy();
    }

    @Override
    public void onPause()
    {
        tipRotator.removeCallbacks(tipRotateTask);
        alertRotator.removeCallbacks(alertRotateTask);
        super.onPause();
    }

    @Override
    public void onResume()
    {
        tipRotateTask.run();
        alertRotateTask.run();
        super.onResume();
    }

    class ViewHolder
    {
        TextView text;
        ImageView icon;
        ImageView star;
    }

    class MainMenuAdapter extends ArrayAdapter<MenuChoice>
    {
        LayoutInflater mInflater = null;

        public MainMenuAdapter(Activity context)
        {
            super(context, R.layout.iconlistrow, MENU_CHOICES);
            mInflater = context.getLayoutInflater();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent)
        {
            ViewHolder holder;
            if (convertView == null)
            {
                convertView = mInflater.inflate(R.layout.iconlistrow, null);
                holder = new ViewHolder();
                holder.text = (TextView) convertView.findViewById(R.id.label);
                holder.icon = (ImageView) convertView.findViewById(R.id.icon);
                holder.star = (ImageView) convertView.findViewById(R.id.star);
                convertView.setTag(holder);
            }
            else
            {
                holder = (ViewHolder) convertView.getTag();
            }
            holder.text.setText(MENU_CHOICES[position].text);
            holder.icon.setImageResource(MENU_CHOICES[position].icon);
            holder.star.setVisibility(View.INVISIBLE);
            return convertView;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.mainmenumenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {

        if (item.getItemId() == R.id.menuAbout)
        {
            Intent myIntent = new Intent();
            myIntent.setClass(Mango.CONTEXT, AboutActivity.class);
            startActivity(myIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class DownloadAlertsTask extends AsyncTask<String, Void, MangoHttpResponse>
    {
        MainMenuActivity activity = null;

        public DownloadAlertsTask(MainMenuActivity activity)
        {
            attach(activity);
        }

        @Override
        protected MangoHttpResponse doInBackground(String... params)
        {
            return MangoHttp.downloadData(params[0], activity);
        }

        @Override
        protected void onPostExecute(MangoHttpResponse data)
        {
            if (activity == null)
            {
                Mango.log("AsyncTask skipped onPostExecute because no activity is attached!");
            }
            else
            {
                activity.callback(data);
            }
            detach();
        }

        void detach()
        {
            activity = null;
        }

        void attach(MainMenuActivity activity)
        {
            this.activity = activity;
        }
    }

    public void callback(MangoHttpResponse data)
    {
        if (data.exception)
        {
            Mango.log("Exception when downloading serveralerts: " + data.toString());
            Mango.getSharedPreferences().edit().putLong("nextAlertCheck", System.currentTimeMillis() + (1000 * 30)).commit();
            return;
        }
        MangoCache.writeDataToCache(data.toString(), "serveralerts.txt");
        parseAlerts();
    }

    private void parseAlerts()
    {
        try
        {
            String alertData = MangoCache.readDataFromCache("serveralerts.txt");
            BufferedReader reader = new BufferedReader(new StringReader(alertData));
            String lineIn = "";
            ArrayList<Alert> alerts = new ArrayList<Alert>();
            while (lineIn != null)
            {
                lineIn = reader.readLine();
                if (lineIn == null)
                    continue;
                if (lineIn.startsWith("#"))
                    continue;
                if (lineIn.contains("bankai") && Mango.DISABLE_ADS)
                    continue;
                int pipeChar = lineIn.indexOf("|");
                int secondPipeChar = lineIn.indexOf("|", pipeChar + 1);
                byte[] decodedString = Base64.decode(lineIn.substring(secondPipeChar + 1), Base64.DEFAULT);
                ByteArrayInputStream is = new ByteArrayInputStream(decodedString);
                // decodeByteArray does not perform DPI scaling (http://code.google.com/p/android/issues/detail?id=7538)
                // so we wrap our byte array in an inputstream and call decodeStream instead.

                BitmapFactory.Options options = new Options();
                options.inScaled = true;
                options.inDensity = DisplayMetrics.DENSITY_MEDIUM;
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(metrics);
                options.inTargetDensity = metrics.densityDpi;
                Bitmap decodedByte = BitmapFactory.decodeStream(is, null, options);
                Alert newalert = new Alert(lineIn.substring(0, pipeChar), lineIn.substring(pipeChar + 1, secondPipeChar), decodedByte);
                alerts.add(newalert);
            }
            ALERTS = new Alert[alerts.size()];
            alerts.toArray(ALERTS);

            activeAlert = ALERTS.length;
            alertRotator.removeCallbacks(alertRotateTask);
            alertLayout.setVisibility(View.VISIBLE);
            alertRotateTask.run();
            Mango.getSharedPreferences().edit().putLong("nextAlertCheck", System.currentTimeMillis() + (1000 * 60 * 60)).commit();
        }
        catch (Exception ex)
        {
            Mango.log("parseAlerts: " + ex.toString());
            Mango.log(MangoCache.readDataFromCache("serveralerts.txt"));
            Mango.getSharedPreferences().edit().putLong("nextAlertCheck", System.currentTimeMillis() + (1000 * 30)).commit();
        }
    }

    private class DecorDownloader extends AsyncTask<Void, Void, Void>
    {
        @Override
        protected Void doInBackground(Void... arg0)
        {
            MangoDecorHandler h = new MangoDecorHandler();
            h.downloadDecorXml(MainMenuActivity.this);
            if (Mango.getSharedPreferences().getLong("nextDecorCheck", 0) < System.currentTimeMillis())
                Mango.getSharedPreferences().edit().putLong("nextDecorCheck", System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 3)).commit();
            h.downloadMissingBackground(MainMenuActivity.this);
            return null;
        }
    }

    public View getTutorialHighlightView(int index)
    {
        return mainMenuList.getChildAt(index);
    }
}
