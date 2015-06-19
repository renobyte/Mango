package net.leetsoft.mangareader.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import net.leetsoft.mangareader.Mango;
import net.leetsoft.mangareader.MangoHttp;
import net.leetsoft.mangareader.MangoHttpResponse;
import net.leetsoft.mangareader.R;
import net.leetsoft.mangareader.ui.MangoDecorHandler;

import java.security.MessageDigest;

public class StartupActivity extends SherlockActivity
{
    private TextView statusLabel;
    private LinearLayout layout;
    private ConnectionTask mConnectionTask;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0)
        {
            Mango.log("Intent flag FLAG_ACTIVITY_BROUGHT_TO_FRONT, finishing StartupActivity...");
            finish();
            return;
        }

        Mango.initializeApp(this);
        setContentView(R.layout.startup);
        layout = (LinearLayout) findViewById(R.id.StartupLayout);
        this.setTitle("Welcome to Mango!");
        statusLabel = (TextView) findViewById(R.id.startupStatus);
        statusLabel.setText("Connecting to the server...");

        Mango.getSharedPreferences().edit().putBoolean("offlineMode", false).commit();
        Mango.getSharedPreferences().edit().putString("serverUrl", "tsukasa.leetsoft.net").commit();

        //		try
        //		{
        //
        //			SyncManager.reflectFavorite(new Favorite(), SyncManager.parseSyncQuerystring(""));
        //		}
        //		catch (Exception e)
        //		{
        //			Mango.log(Log.getStackTraceString(e));
        //		}

        // Download latest server URL
        Thread t = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                MangoHttpResponse resp = MangoHttp.downloadData("http://www.leetsoft.net/mangoweb/serverurl.txt", StartupActivity.this);
                String serverurl = resp.toString();
                if (resp.exception)
                    serverurl = "tsukasa.leetsoft.net";
                Mango.getSharedPreferences().edit().putString("serverUrl", serverurl).commit();
            }
        });
        t.start();
        layout.postDelayed(new Runnable()
        {

            @Override
            public void run()
            {
                initializeConnection();
            }
        }, 300);

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT)
        {
            Mango.getMenuBackgroundPortrait();
            if (Mango.MENUBG_PORTRAITNAME.equals("local"))
                layout.setBackgroundResource(R.drawable.img_background_portrait);
            else
                layout.setBackgroundDrawable(new BitmapDrawable(getResources(), MangoDecorHandler.readDecorBitmap(Mango.MENUBG_PORTRAITNAME)));
        }
        else
        {
            Mango.getMenuBackgroundLandscape();
            if (Mango.MENUBG_LANDSCAPENAME.equals("local"))
                layout.setBackgroundResource(R.drawable.img_background_landscape);
            else
                layout.setBackgroundDrawable(new BitmapDrawable(getResources(), MangoDecorHandler.readDecorBitmap(Mango.MENUBG_LANDSCAPENAME)));
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.startupoffline, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.menuOfflineMode)
        {
            Mango.getSharedPreferences().edit().putBoolean("offlineMode", true).commit();
            Intent myIntent = new Intent();
            myIntent.putExtra("updateavailable", false);
            myIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            myIntent.setClass(Mango.CONTEXT, MainMenuActivity.class);
            startActivity(myIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void initializeConnection()
    {
        Mango.bankaiCheck();
        mConnectionTask = new ConnectionTask(this);
        mConnectionTask.execute("http://%SERVER_URL%/validate.aspx?pin=" + Mango.getPin() + "&ver=" + Mango.VERSION_NETID + "&os=" + android.os.Build.VERSION.RELEASE + "&model="
                + android.os.Build.MODEL + "&bankai=" + Mango.DISABLE_ADS);
        Thread t = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                String response = MangoHttp.downloadData("http://%SERVER_URL%/getbankaistatus.aspx?did=" + Mango.getPin(), StartupActivity.this).toString();
                String target = Mango.getPin() + "asalt";
                byte[] bhash;

                try
                {
                    bhash = MessageDigest.getInstance("MD5").digest(target.getBytes("UTF-8"));
                }
                catch (Exception e)
                {
                    throw new RuntimeException("Bankai activation problem.", e);
                }

                StringBuilder hex = new StringBuilder(bhash.length * 2);
                for (byte b : bhash)
                {
                    if ((b & 0xFF) < 0x10)
                        hex.append("0");
                    hex.append(Integer.toHexString(b & 0xFF));
                }

                target = hex.toString();

                if (response.equals(target))
                {
                    Mango.DISABLE_ADS = true;
                    Mango.getSharedPreferences().edit().putBoolean("bankai", true).commit();
                    if (Mango.getSharedPreferences().getLong("bankaiInstallDate", -1) == -1)
                        Mango.getSharedPreferences().edit().putLong("bankaiInstallDate", System.currentTimeMillis()).commit();
                }
                else
                    Mango.DISABLE_ADS = false;
            }
        });
        t.start();
    }

    public void callback(MangoHttpResponse mhr)
    {
        String data = mhr.toString();
        String errorText = null;

        if (mhr.exception)
        {
            statusLabel.setText("Connection failed");
            errorText = "Mango couldn't connect to the internet. Check your mobile data or Wi-Fi connection and try again.\n" + data;
        }
        if (data.startsWith("2"))
        {
            statusLabel.setText("Device is blocked");
            errorText = "This device has been blocked from the server due to abuse. For more information please see:\nhttp://mango.leetsoft.net/banned.php\n[Error 2]";
        }
        if (data.startsWith("3"))
        {
            statusLabel.setText("Unrecognized version ID");
            errorText = "The server doesn't recognize this version. Please reinstall the newest version of Mango from mango.leetsoft.net.\n[Error 3]";
        }
        if (data.startsWith("4"))
        {
            statusLabel.setText("Outdated version");
            errorText = "There is a new version of Mango available! This version no longer works, so please update Mango from mango.leetsoft.net.\n[Error 4]";
        }

        if (data.startsWith("error"))
        {
            statusLabel.setText("Server is temporarily offline");
            errorText = "The server is temporarily offline for maintenance. Read the message below for more info.\n\n" + data;
        }

        if (data.startsWith("1") || data.startsWith("6"))
        {
            int adStringStart = data.indexOf("ads(") + 4;
            String adString = data.substring(adStringStart, data.indexOf(")"));
            Mango.initializeAdProviderWeights(adString);
            statusLabel.setText("Connected!");
            Mango.getSharedPreferences().edit().putBoolean("offlineMode", false).commit();
            Intent myIntent = new Intent();
            if (data.startsWith("6"))
                myIntent.putExtra("updateavailable", true);
            else
                myIntent.putExtra("updateavailable", false);
            myIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            myIntent.setClass(Mango.CONTEXT, MainMenuActivity.class);
            startActivity(myIntent);
            finish();
            overridePendingTransition(R.anim.fadein, R.anim.expandout);
            return;
        }

        if (errorText == null)
        {
            statusLabel.setText("Unexpected response");
            errorText = "Mango received an unexpected response from the server. It may be experiencing server issues.\n\n" + data;
        }

        statusLabel.setText(statusLabel.getText() + "\n\nTap the globe icon in the action bar to enter Offline Mode.");

        if (errorText.length() > 0)
        {
            AlertDialog alert = new AlertDialog.Builder(StartupActivity.this).create();
            alert.setTitle("Error");
            alert.setMessage(errorText);
            if (data.startsWith("3") || data.startsWith("4"))
            {
                alert.setButton(DialogInterface.BUTTON_POSITIVE, "Update", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        new Thread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                MangoHttpResponse resp = MangoHttp.downloadData("http://%SERVER_URL%/getupdateurl.aspx?ver=" + Mango.VERSION_NETID, StartupActivity.this);
                                String url = resp.toString();
                                if (resp.exception)
                                    url = "http://Mango.leetsoft.net/install-android.php";
                                final String finalUrl = url;
                                layout.post(new Runnable() {
                                    @Override
                                    public void run()
                                    {
                                        launchUpdateIntent(finalUrl);
                                    }
                                });
                            }
                        }).start();


                    }
                });
            }
            else
            {
                alert.setButton(DialogInterface.BUTTON_POSITIVE, "Okay", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {

                    }
                });
            }
            try
            {
                alert.show();
            }
            catch (Exception e)
            {
                // catch and ignore BadTokenException if activity is closed
            }
        }
    }

    private void launchUpdateIntent(String url)
    {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
        overridePendingTransition(R.anim.fadein, R.anim.expandout);
    }

    private class ConnectionTask extends AsyncTask<String, Void, MangoHttpResponse>
    {
        StartupActivity activity = null;

        public ConnectionTask(StartupActivity activity)
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

        void attach(StartupActivity activity)
        {
            this.activity = activity;
        }
    }
}