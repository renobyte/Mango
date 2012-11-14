package net.leetsoft.mangareader.activities;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import net.leetsoft.mangareader.Mango;
import net.leetsoft.mangareader.MangoHttp;
import net.leetsoft.mangareader.MangoHttpResponse;
import net.leetsoft.mangareader.R;

public class TermsDialog extends Dialog
{
    private Button mAgreeButton;
    private Button mDisagreeButton;
    private TextView mTermsTextView;

    private Activity mBaseActivity;

    private TermsDownloader mDownloader;

    private ProgressDialog mDialog;

    public TermsDialog(Context context)
    {
        super(context);
        Window window = this.getWindow();
        window.requestFeature(Window.FEATURE_NO_TITLE);
        this.setContentView(getLayoutInflater().inflate(R.layout.termsofservice, null));
        mAgreeButton = (Button) this.findViewById(R.id.TermsAgreeButton);
        mDisagreeButton = (Button) this.findViewById(R.id.TermsDisagreeButton);
        mTermsTextView = (TextView) this.findViewById(R.id.TermsText);
        mAgreeButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Mango.getSharedPreferences().edit().putBoolean("termsRead", true).commit();
                TermsDialog.this.dismiss();
                if (Mango.getSiteId() == -1)
                {
                    Mango.getSharedPreferences().edit().putInt("mangaSite", 2).commit();
                }
            }
        });
        mDisagreeButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                mBaseActivity.finish();
            }
        });
        mAgreeButton.setEnabled(false);
        mDisagreeButton.setEnabled(false);
    }

    public void startDownloadingTerms(Activity activity)
    {
        mBaseActivity = activity;
        mDownloader = new TermsDownloader(this);
        mDownloader.execute("http://%SERVER_URL%/MangoTerms.txt");
        mDialog = new ProgressDialog(this.getContext());
        mDialog.setTitle("Retrieving Terms of Service...");
        mDialog.setMessage("Wall of Text crits you for 9001 damage!");
        mDialog.setIndeterminate(true);
        mDialog.setCancelable(true);
        Mango.DIALOG_DOWNLOADING = mDialog;
        mDialog.show();
    }

    public void callback(MangoHttpResponse data)
    {
        try
        {
            mDialog.dismiss();
        } catch (Exception e)
        {
            Mango.log("Couldn't dismiss dialog (" + e.toString() + ")");
        }
        mTermsTextView.setText(data.toString());
        if (data.exception)
        {
            mTermsTextView.setText("Mango was unable to download the Terms of Service.\n\nBy clicking I Agree below, you agree to the terms as outlined at the following web page:\nhttp://Mango.leetsoft.net/terms.php\n\n("
                    + data + ")");
        }
        mAgreeButton.setEnabled(true);
        mDisagreeButton.setEnabled(true);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            mBaseActivity.finish();
            return false;
        }
        else
            return super.onKeyDown(keyCode, event);
    }

    private class TermsDownloader extends AsyncTask<String, Void, MangoHttpResponse>
    {
        TermsDialog dialog = null;

        public TermsDownloader(TermsDialog dialog)
        {
            attach(dialog);
        }

        @Override
        protected MangoHttpResponse doInBackground(String... params)
        {
            return MangoHttp.downloadData(params[0], dialog.getContext());
        }

        @Override
        protected void onPostExecute(MangoHttpResponse data)
        {
            if (dialog != null)
                dialog.callback(data);
        }

        void detach()
        {
            dialog = null;
        }

        void attach(TermsDialog dialog)
        {
            this.dialog = dialog;
        }
    }
}
