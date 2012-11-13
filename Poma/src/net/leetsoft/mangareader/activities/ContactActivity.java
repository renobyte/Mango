package net.leetsoft.mangareader.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import net.leetsoft.mangareader.Mango;
import net.leetsoft.mangareader.MangoActivity;
import net.leetsoft.mangareader.MangoCache;
import net.leetsoft.mangareader.R;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class ContactActivity extends MangoActivity
{
    private LinearLayout mMenuLayout;
    private LinearLayout mEditLayout;

    private RadioGroup mRadioGroup;
    private RadioButton mOption1;
    private RadioButton mOption2;
    private RadioButton mOption3;
    private RadioButton mOption4;

    private Button mNext;
    private Button mBack;

    private TextView mMenuHeader;
    private TextView mMenuHelpText;
    private TextView mEditHeader;
    private TextView mEditHelpText;

    private EditText mEditText;
    private Button mSubmit;

    private int mMenuLevel = 0;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setTitle("Send Feedback", null);
        inflateLayoutManager(this, R.layout.contactscreen);
        super.setNoBackground(true);

        mMenuLayout = (LinearLayout) findViewById(R.id.feedbackMenuLayout);
        mEditLayout = (LinearLayout) findViewById(R.id.feedbackEditLayout);

        mMenuHeader = (TextView) findViewById(R.id.feedbackMenuHeader);
        mMenuHelpText = (TextView) findViewById(R.id.feedbackMenuHelpText);
        mEditHeader = (TextView) findViewById(R.id.feedbackEditHeader);
        mEditHelpText = (TextView) findViewById(R.id.feedbackEditHelpText);

        mRadioGroup = (RadioGroup) findViewById(R.id.feedbackMenuOptionGroup);
        mOption1 = (RadioButton) findViewById(R.id.feedbackMenuOption1);
        mOption2 = (RadioButton) findViewById(R.id.feedbackMenuOption2);
        mOption3 = (RadioButton) findViewById(R.id.feedbackMenuOption3);
        mOption4 = (RadioButton) findViewById(R.id.feedbackMenuOption4);

        mNext = (Button) findViewById(R.id.feedbackMenuNext);
        mNext.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                nextButtonPressed();
            }
        });
        mBack = (Button) findViewById(R.id.feedbackMenuBack);
        mBack.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                backButtonPressed();
            }
        });
        mSubmit = (Button) findViewById(R.id.feedbackEditSubmit);
        mSubmit.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                String body = "Please add a bit more text to your message!\n\nThanks. ^__^";

                int minLength = 20;
                if (mMenuLevel >= 5)
                {
                    body = "When reporting a bug, be as detailed as possible so I can track it down! Be sure to include the manga and chapter you were reading, if applicable. Reports with very little or no detail will be deleted. :'(\n\nThanks. ^__^";
                    minLength = 40;
                }
                if (mEditText.getText().length() <= minLength)
                {
                    Mango.alert(body, "Please type more!", ContactActivity.this);
                    return;
                }
                sendButtonPressed();
            }
        });

        mEditText = (EditText) findViewById(R.id.feedbackEditTextbox);

        setMenuState(0);
    }

    private void setMenuState(int menuId)
    {
        mMenuLevel = menuId;
        switch (menuId)
        {
            case 0: // root menu:
                mMenuLayout.setVisibility(View.VISIBLE);
                mEditLayout.setVisibility(View.GONE);
                mMenuHeader.setText("Send Feedback");
                mMenuHelpText.setText("Thanks for your interest in contacting Leetsoft!  What's the nature of your feedback?");
                mOption1.setText("I'm having an issue with the app.");
                mOption2.setText("I've got a cool suggestion for you!");
                mOption3.setText("I found some missing pages or chapters.");
                mOption4.setText("Something else (praise, other comments, 'how do I do this?')");
                mRadioGroup.setVisibility(View.VISIBLE);
                mOption1.setChecked(true);
                mNext.setEnabled(true);
                break;
            case 1: // issue:
                mMenuLayout.setVisibility(View.VISIBLE);
                mEditLayout.setVisibility(View.GONE);
                mMenuHeader.setText("Report an Issue");
                mMenuHelpText.setText("Sorry that you're having a problem with Mango.  Please choose the option below that describes your issue:");
                mOption1.setText("I can't view any pages at all.");
                mOption2.setText("I'm getting a 'Force Close' crash repeatedly.");
                mOption3.setText("I found some missing pages or chapters.");
                mOption4.setText("None of the above");
                mRadioGroup.setVisibility(View.VISIBLE);
                mOption1.setChecked(true);
                mNext.setEnabled(true);
                break;
            case 2: // suggestion
                mMenuLayout.setVisibility(View.GONE);
                mEditLayout.setVisibility(View.VISIBLE);
                mEditHeader.setText("Send a Suggestion");
                mEditHelpText.setText("Have a suggestion to make Mango even better?  Describe it below and we'll consider adding it!");
                break;
            case 3: // missing pages
            case 8:
                mMenuLayout.setVisibility(View.VISIBLE);
                mEditLayout.setVisibility(View.GONE);
                mMenuHeader.setText("Missing Pages");
                mMenuHelpText.setText("If there are occasional missing pages or chapters in a manga, you will need to contact the manga source (such as MangaFox.com or MangaReader.net) to ask them to fix them."
                        + "\n\nPlease note that the developer of Mango does not actually upload or host any content; Mango is sort of like a search engine that directs you to the manga you want to read.  Unfortuantely this means that missing pages or chapters are not under our control."
                        + "\n\nTry reading the manga on a different manga source (for example, if you're using MangaFox, try MangaReader instead) and see if the missing pages appear.");
                mNext.setEnabled(false);
                mRadioGroup.setVisibility(View.GONE);
                break;
            case 4: // other
                mMenuLayout.setVisibility(View.GONE);
                mEditLayout.setVisibility(View.VISIBLE);
                mEditHeader.setText("Other Feedback");
                mEditHelpText.setText("Have a random comment, or perhaps just some praise?  Or maybe you need some general help with using the app?  Let us know below!");
                break;
            case 5: // no pages
                mMenuLayout.setVisibility(View.GONE);
                mEditLayout.setVisibility(View.VISIBLE);
                mEditHeader.setText("Can't Read Anything");
                mEditHelpText.setText("If *all* pages are failing to load (and you've tried multiple manga sources), please let us know below.  If you've only tried one manga source, try another one; the first one might be temporarily down.");
                break;
            case 6: // fc
                mMenuLayout.setVisibility(View.GONE);
                mEditLayout.setVisibility(View.VISIBLE);
                mEditHeader.setText("Force Close Crashes");
                mEditHelpText.setText("If Mango is force closing (displaying a popup that says 'Sorry! The application Mango has stopped unexpectedly'), please describe the circumstances below.");
                break;
            case 7:
                mMenuLayout.setVisibility(View.GONE);
                mEditLayout.setVisibility(View.VISIBLE);
                mEditHeader.setText("Other Issues");
                mEditHelpText.setText("Having an issue with the app that wasn't listed?  Please describe your issue below, as detailed as you possibly can.");
                break;
        }
    }

    private void nextButtonPressed()
    {
        if (mMenuLevel == 0)
        {
            switch (mRadioGroup.getCheckedRadioButtonId())
            {
                case R.id.feedbackMenuOption1:
                    setMenuState(1);
                    break;
                case R.id.feedbackMenuOption2:
                    setMenuState(2);
                    break;
                case R.id.feedbackMenuOption3:
                    setMenuState(3);
                    break;
                case R.id.feedbackMenuOption4:
                    setMenuState(4);
                    break;
            }
        }
        else if (mMenuLevel == 1)
        {
            switch (mRadioGroup.getCheckedRadioButtonId())
            {
                case R.id.feedbackMenuOption1:
                    setMenuState(5);
                    break;
                case R.id.feedbackMenuOption2:
                    setMenuState(6);
                    break;
                case R.id.feedbackMenuOption3:
                    setMenuState(8);
                    break;
                case R.id.feedbackMenuOption4:
                    setMenuState(7);
                    break;
            }
        }
    }

    private void backButtonPressed()
    {
        switch (mMenuLevel)
        {
            case 0:
                finish();
                break;
            case 1:
            case 2:
            case 3:
            case 4:
                setMenuState(0);
                break;
            case 5:
            case 6:
            case 7:
            case 8:
                setMenuState(1);
                break;
        }
    }

    protected void sendButtonPressed()
    {

        String heapSize = (java.lang.Runtime.getRuntime().maxMemory() / 1024) / 1024 + "MB";

        Intent msg = new Intent(Intent.ACTION_SEND);
        String body = "Mango Version:\n\t[Android] " + Mango.VERSION_FULL + " (" + Mango.VERSION_BUILDID + ")\n";
        body += "OS Version:\n\t" + android.os.Build.VERSION.RELEASE + "\n";
        body += "Device Model:\n\t" + android.os.Build.MODEL + "\n";
        body += "Dalvik Heap Size:\n\t" + heapSize + "\n";
        body += "Data Folder:\n\t" + Mango.getDataDirectory() + ", Free: " + MangoCache.getFreeSpace() + " bytes\n\n";
        body += mEditText.getText().toString() + "\n\n";
        msg.putExtra(Intent.EXTRA_EMAIL, new String[]{"Mango@leetsoft.net"});

        msg.setType("message/rfc822");

        switch (mMenuLevel)
        {
            case 2: // suggestion
                msg.putExtra(Intent.EXTRA_SUBJECT, "Mango Feedback [SUGG] (v" + Mango.VERSION_FULL + ")");
                break;
            case 4: // other
                msg.putExtra(Intent.EXTRA_SUBJECT, "Mango Feedback [OTHR] (v" + Mango.VERSION_FULL + ")");
                break;
            case 5: // no pages
                msg.putExtra(Intent.EXTRA_SUBJECT, "Mango Feedback [NOPG] (v" + Mango.VERSION_FULL + ")");
                body += getLogcat();
                break;
            case 6: // fc
                msg.putExtra(Intent.EXTRA_SUBJECT, "Mango Feedback [FC] (v" + Mango.VERSION_FULL + ")");
                body += getLogcat();
                break;
            case 7: // other issue
                msg.putExtra(Intent.EXTRA_SUBJECT, "Mango Feedback [PROB] (v" + Mango.VERSION_FULL + ")");
                body += getLogcat();
                break;
        }

        msg.putExtra(Intent.EXTRA_TEXT, body);
        this.startActivity(Intent.createChooser(msg, "Send message via..."));

        Toast.makeText(this, "Starting email app, one sec...", Toast.LENGTH_SHORT).show();

        mSubmit.postDelayed(new Runnable()
        {

            @Override
            public void run()
            {
                setMenuState(0);
                Mango.alert(
                        "Thanks for your feedback!\n\nPlease note that while I do read every message, I get almost 100 per day and sadly I can't respond to each one.  If you really want a reply, posting on Mango's Facebook page is a good idea.\n\nwww.facebook.com/MangoApp\n\nThanks again!",
                        ContactActivity.this);
            }
        }, 7500);
    }

    private String getLogcat()
    {
        try
        {
            ArrayList<String> list = new ArrayList<String>();

            final StringBuilder log = new StringBuilder();
            ArrayList<String> commandLine = new ArrayList<String>();
            commandLine.add("logcat");//$NON-NLS-1$
            commandLine.add("-d");//$NON-NLS-1$
            ArrayList<String> arguments = null;
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
            log.insert(0, "Diagnostic Data:\n");
            return log.toString();
        }
        catch (Exception e)
        {
            // TODO: handle exception
        }
        return "";
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK && mMenuLevel != 0)
        {
            backButtonPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
