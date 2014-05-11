package net.leetsoft.mangareader.activities;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import net.leetsoft.mangareader.R;

import java.io.*;

public class LogcatActivity extends Activity
{
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

        File f = new File(getFilesDir(), "logOld.txt");
        BufferedReader br = null;
        String logStr = "";
        try
        {
            if (f.exists())
                logStr = readFile(f);
            f = new File(getFilesDir(), "log.txt");
            logStr += readFile(f);
        }
        catch (Exception ex)
        {
            logStr = "<b>Unable to read logfile.<br>" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + "<br>" + f.getAbsolutePath() + "</b>";
            mLogText.setText(Html.fromHtml(logStr));
            return;
        }
        mLogText.setText(logStr);
    }

    private String readFile(File f) throws IOException
    {
        BufferedReader br = null;
        br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[8192];
        int charsRead = 0;
        while ((charsRead = br.read(buffer)) > 0)
        {
            builder.append(buffer, 0, charsRead);
            buffer = new char[8192];
        }
        return builder.toString();
    }
}