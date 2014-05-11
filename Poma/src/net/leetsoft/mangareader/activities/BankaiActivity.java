package net.leetsoft.mangareader.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ScrollView;
import net.leetsoft.mangareader.Mango;
import net.leetsoft.mangareader.MangoActivity;
import net.leetsoft.mangareader.R;
import net.robotmedia.billing.BillingController;
import net.robotmedia.billing.BillingRequest;
import net.robotmedia.billing.helper.AbstractBillingObserver;
import net.robotmedia.billing.model.Transaction;

import java.util.List;

public class BankaiActivity extends MangoActivity
{
    Button mButton;
    Button mButton2;
    ScrollView mMenuView;
    ScrollView mInfoView;
    int mDisplayMode;    // 0 menu, 1 retrieve, 2 info
    private AbstractBillingObserver mBillingObserver;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setTitle("Mango Bankai (ad-free)", null);
        inflateLayoutManager(this, R.layout.bankai);
        super.setNoBackground(true);
        mMenuView = (ScrollView) findViewById(R.id.bankaiMenuScrollView);
        mInfoView = (ScrollView) findViewById(R.id.bankaiInfoScrollView);
        mButton = (Button) findViewById(R.id.bankaiButton);
        mButton.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("http://mango.leetsoft.net/mybankai"));
                startActivity(intent);
                overridePendingTransition(R.anim.fadein, R.anim.expandout);
            }
        });
        mButton2 = (Button) findViewById(R.id.bankaiPurchase);
        mButton2.setOnClickListener(new

                                            OnClickListener()
                                            {

                                                @Override
                                                public void onClick(View v)
                                                {
                                                    Mango.alert("You're being transferred to Google Play to complete this transaction.  A receipt will be emailed to " + Mango.getPrimaryAccount() + ".  Thanks for your support!", "Mango Bankai", BankaiActivity.this, new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(DialogInterface dialogInterface, int i)
                                                        {
                                                            BillingController.requestPurchase(BankaiActivity.this, "android.test.purchased", true, Mango.getPin() + ";" + Mango.getPrimaryAccount());
                                                        }
                                                    });
                                                }
                                            });
        switchMode(0);
        if (Mango.DISABLE_ADS)
            switchMode(0);

        initializeBilling();
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
            mInfoView.setVisibility(View.VISIBLE);
            mButton.setVisibility(View.VISIBLE);
            mButton2.setVisibility(View.GONE);
        }
    }

    private void initializeBilling()
    {
        BillingController.setDebug(true);
        BillingController.setConfiguration(new BillingController.IConfiguration()
        {

            public byte[] getObfuscationSalt()
            {
                return new byte[]{-124, -2, 32, 77, -40, -107, 14, 98, 9, -59, 48, 47, 101, -108, -64, -11, -35, -116, 82, -120};
            }

            public String getPublicKey()
            {
                return "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDTkeaJwTdUCJmOxTj2WS9nG9J93mH9VHmqQi7lQLmeeZbbdhhg3LAYj6bg/iYGvJRhekJHJHCTyIFl84ZZYX8tkz6tn3GZ0tBBBPU4GDukACdqYJTHR7cTHHLYmRClKihSfBIDceHz3gZD9+S1VMZ7vkphf9r/S4eRMmp/ExuZ6wIDAQAB";
            }
        });

        mBillingObserver = new AbstractBillingObserver(this)
        {

            public void onBillingChecked(boolean supported)
            {
                BankaiActivity.this.onBillingChecked(supported);
            }

            public void onPurchaseStateChanged(String itemId, Transaction.PurchaseState state, String signedData, String signature)
            {
                BankaiActivity.this.onPurchaseStateChanged(itemId, state, signedData, signature);
            }

            public void onRequestPurchaseResponse(String itemId, BillingRequest.ResponseCode response)
            {
                BankaiActivity.this.onRequestPurchaseResponse(itemId, response);
            }

            public void onSubscriptionChecked(boolean supported)
            {
                BankaiActivity.this.onSubscriptionChecked(supported);
            }
        };

        BillingController.registerObserver(mBillingObserver);
        BillingController.checkBillingSupported(this);
    }

    private void updateOwnedItems()
    {
        List<Transaction> transactions = BillingController.getTransactions(this);
        if (transactions.size() > 0)
        {
            Transaction t = transactions.get(transactions.size() - 1);
            Mango.log(t.orderId + ", STATUS=" + t.purchaseState.name() + ", PAYLOAD=" + t.developerPayload);
        }
    }

    public void onPurchaseStateChanged(String itemId, Transaction.PurchaseState state, String signed, String sig)
    {
        updateOwnedItems();
        Mango.log(signed);
    }

    public void onRequestPurchaseResponse(String itemId, BillingRequest.ResponseCode response)
    {
    }

    public void onSubscriptionChecked(boolean supported)
    {

    }

    public void onBillingChecked(boolean supported)
    {
        if (!supported)
        {
            Mango.alert("Google Play's In-App Purchase service doesn't seem to be supported on this device.  You can still purchase Mango Bankai via PayPal by going to:\n\nhttp://mango.leetsoft.net/bankai.php", "In-App Purchases Not Supported!", this);
            mButton2.setOnClickListener(new

                                                OnClickListener()
                                                {

                                                    @Override
                                                    public void onClick(View v)
                                                    {
                                                        Intent intent = new Intent(Intent.ACTION_VIEW);
                                                        intent.setData(Uri.parse("http://" + Mango.getSharedPreferences().getString("serverUrl", "kagami.leetsoft.net") + "/buyBankai.aspx?did=" + Mango.getPin()));
                                                        startActivity(intent);
                                                        overridePendingTransition(R.anim.fadein, R.anim.expandout);
                                                    }
                                                });
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        // TODO Auto-generated method stub
        super.onConfigurationChanged(newConfig);
    }
}

    
