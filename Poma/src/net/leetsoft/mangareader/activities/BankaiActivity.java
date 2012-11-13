package net.leetsoft.mangareader.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import net.leetsoft.mangareader.Mango;
import net.leetsoft.mangareader.MangoActivity;
import net.leetsoft.mangareader.MangoHttp;
import net.leetsoft.mangareader.R;

public class BankaiActivity extends MangoActivity
{
    Button mButton;
    Button mButton2;
    TextView mDeviceId;
    TextView mInfoText;
    EditText mOrderNumberText;

    ScrollView mMenuView;
    ScrollView mRetrieveView;
    ScrollView mInfoView;

    int mDisplayMode;    // 0 menu, 1 retrieve, 2 info

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setTitle("Mango Bankai (ad-free)", null);
        inflateLayoutManager(this, R.layout.bankai);
        super.setNoBackground(true);
        mMenuView = (ScrollView) findViewById(R.id.bankaiMenuScrollView);
        mRetrieveView = (ScrollView) findViewById(R.id.bankaiRetrieveScrollView);
        mInfoView = (ScrollView) findViewById(R.id.bankaiInfoScrollView);
        mDeviceId = (TextView) findViewById(R.id.bankaiTextView);
        mDeviceId.setText(Mango.getPin());
        mInfoText = (TextView) findViewById(R.id.bankaiInfoText);
        mOrderNumberText = (EditText) findViewById(R.id.bankaiOrderNum);
        mOrderNumberText.setText(Mango.getSharedPreferences().getString("bankaiOrderNumber", ""));
        mOrderNumberText.setHint("Type or paste order number here");
        mButton = (Button) findViewById(R.id.bankaiButton);
        mButton.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                buttonClicked();
            }
        });
        mButton2 = (Button) findViewById(R.id.bankaiPurchase);
        mButton2.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("http://" + Mango.getSharedPreferences().getString("serverUrl", "174.137.55.109") + "/buyBankai.aspx?did=" + Mango.getPin()));
                startActivity(intent);
                overridePendingTransition(R.anim.fadein, R.anim.expandout);
            }
        });
        switchMode(0);
        if (Mango.DISABLE_ADS)
            switchMode(2);
    }

    @Override
    public void onResume()
    {
        super.onResume();
    }

    private void switchMode(int mode)
    {
        mDisplayMode = mode;
        mMenuView.setVisibility(View.GONE);
        mRetrieveView.setVisibility(View.GONE);
        mInfoView.setVisibility(View.GONE);
        mButton.setVisibility(View.GONE);
        mButton2.setVisibility(View.GONE);
        if (mode == 0)
        {
            mMenuView.setVisibility(View.VISIBLE);
            mButton.setVisibility(View.VISIBLE);
            mButton2.setVisibility(View.VISIBLE);
        }
        else if (mode == 1)
        {
            mRetrieveView.setVisibility(View.VISIBLE);
            mButton.setVisibility(View.VISIBLE);
        }
        else
        {
            mInfoView.setVisibility(View.VISIBLE);
            mInfoText.setText("DID: " + Mango.getPin());
        }
    }

    private void buttonClicked()
    {
        if (mDisplayMode == 0)
        {
            switchMode(1);
        }
        else
        {
            AlertDialog alert = new AlertDialog.Builder(BankaiActivity.this).create();
            alert.setTitle("Important!");
            alert.setMessage("Each order number can only be registered to one device.  To re-register this order number later, you'll need to email us.  Are you sure you'd like to associate this order number with the device you're using now?");
            alert.setButton(DialogInterface.BUTTON_POSITIVE, "Yep", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    Mango.getSharedPreferences().edit().putString("bankaiOrderNumber", mOrderNumberText.getText().toString()).commit();
                    doRegistration();
                }
            });
            alert.setButton(DialogInterface.BUTTON_NEGATIVE, "Nah", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    switchMode(0);
                }
            });
            alert.show();

        }
    }

    private void doRegistration()
    {
        Thread t = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                final String retval = MangoHttp.downloadData("http://%SERVER_URL%/registerbankai.aspx?pin=" + Mango.getPin() + "&order=" + mOrderNumberText.getText().toString(), BankaiActivity.this);
                if (!retval.startsWith("okay"))
                {
                    mRetrieveView.post(new Runnable()
                    {

                        @Override
                        public void run()
                        {
                            Mango.alert("Mango wasn't able to activate your Bankai key for the following reason:\n\n" + retval, "Uh oh!", BankaiActivity.this);
                        }
                    });
                }
                else
                {
                    mRetrieveView.post(new Runnable()
                    {

                        @Override
                        public void run()
                        {
                            Mango.alert("Mango Bankai has been successfully activated on this device!  Ads should be gone once you reset the app.", "Success!", BankaiActivity.this);
                            Mango.DISABLE_ADS = true;
                        }
                    });
                }
            }
        });
        t.start();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        // TODO Auto-generated method stub
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK && mDisplayMode == 1)
        {
            switchMode(0);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
