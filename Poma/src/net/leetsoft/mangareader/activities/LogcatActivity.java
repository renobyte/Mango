package net.leetsoft.mangareader.activities;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import net.leetsoft.mangareader.Mango;
import net.leetsoft.mangareader.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class LogcatActivity extends Activity
{

    final int MAX_LOG_MESSAGE_LENGTH = 150000;

    private CollectLogTask mCollectLogTask;
    private String mAdditonalInfo;
    private String[] mFilterSpecs;
    private String mFormat;
    private String mBuffer;

    private TextView mLogText;
    private Button mSend;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.logcat);

        mLogText = (TextView) this.findViewById(R.id.LogcatLogText);
        mSend = (Button) this.findViewById(R.id.LogcatSend);
        mSend.setVisibility(View.GONE);

        genLogcat();
    }

    @SuppressWarnings("unchecked")
    void genLogcat()
    {
        /*Usage: logcat [options] [filterspecs]
          options include:
            -s              Set default filter to silent.
                            Like specifying filterspec '*:s'
            -f <filename>   Log to file. Default to stdout
            -r [<kbytes>]   Rotate log every kbytes. (16 if unspecified). Requires -f
            -n <count>      Sets max number of rotated logs to <count>, default 4
            -v <format>     Sets the log print format, where <format> is one of:

                            brief process tag thread raw time threadtime long

            -c              clear (flush) the entire log and exit
            -d              dump the log and then exit (don't block)
            -g              get the size of the log's ring buffer and exit
            -b <buffer>     request alternate ring buffer
                            ('main' (default), 'radio', 'events')
            -B              output the log in binary
          filterspecs are a series of
            <tag>[:priority]

          where <tag> is a log component tag (or * for all) and priority is:
            V    Verbose
            D    Debug
            I    Info
            W    Warn
            E    Error
            F    Fatal
            S    Silent (supress all output)

          '*' means '*:d' and <tag> by itself means <tag>:v

          If not specified on the commandline, filterspec is set from ANDROID_LOG_TAGS.
          If no filterspec is found, filter defaults to '*:I'

          If not specified with -v, format is set from ANDROID_PRINTF_LOG
          or defaults to "brief"*/

        ArrayList<String> list = new ArrayList<String>();

        if (mFormat != null)
        {
            list.add("-v");
            list.add(mFormat);
        }

        if (mBuffer != null)
        {
            list.add("-b");
            list.add(mBuffer);
        }

        if (mFilterSpecs != null)
        {
            for (String filterSpec : mFilterSpecs)
            {
                list.add(filterSpec);
            }
        }

        mCollectLogTask = (CollectLogTask) new CollectLogTask().execute(list);
    }

    private class CollectLogTask extends AsyncTask<ArrayList<String>, Void, StringBuilder>
    {
        @Override
        protected void onPreExecute()
        {
            // showProgressDialog(getString(R.string.acquiring_log_progress_dialog_message));
        }

        @Override
        protected StringBuilder doInBackground(ArrayList<String>... params)
        {
            final StringBuilder log = new StringBuilder();
            try
            {
                ArrayList<String> commandLine = new ArrayList<String>();
                commandLine.add("logcat");//$NON-NLS-1$
                commandLine.add("-d");//$NON-NLS-1$
                ArrayList<String> arguments = ((params != null) && (params.length > 0)) ? params[0] : null;
                if (null != arguments)
                {
                    commandLine.addAll(arguments);
                }
                commandLine.add("Mango:V AndroidRuntime:E *:S");

                Mango.log("Executing logcat -d Mango:V AndroidRuntime:E *:S");
                Process process = Runtime.getRuntime().exec(commandLine.toArray(new String[0]));
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = bufferedReader.readLine()) != null)
                {
                    int index = line.indexOf(":") + 1;
                    if (index == -1)
                        index = 1;
                    log.append(">");
                    log.append(line.substring(index));
                    log.append("\n");
                }
            } catch (IOException e)
            {
                Log.e("MangoLog", "CollectLogTask.doInBackground failed", e);//$NON-NLS-1$
            }

            return log;
        }

        @Override
        protected void onPostExecute(StringBuilder log)
        {
            if (null != log)
            {
                // truncate if necessary
                int keepOffset = Math.max(log.length() - MAX_LOG_MESSAGE_LENGTH, 0);
                if (keepOffset > 0)
                {
                    log.delete(0, keepOffset);
                }

                if (mAdditonalInfo != null)
                {
                    log.insert(0, "----------------------");
                    log.insert(0, mAdditonalInfo);
                }

                mLogText.setText(log.toString());
            }
            else
            {

            }
        }
    }

    void cancellCollectTask()
    {
        if (mCollectLogTask != null && mCollectLogTask.getStatus() == AsyncTask.Status.RUNNING)
        {
            mCollectLogTask.cancel(true);
            mCollectLogTask = null;
        }
    }

    @Override
    protected void onPause()
    {
        cancellCollectTask();

        super.onPause();
    }
}
