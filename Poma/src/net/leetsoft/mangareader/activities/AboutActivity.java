package net.leetsoft.mangareader.activities;

import android.os.Bundle;
import android.widget.TextView;
import net.leetsoft.mangareader.Mango;
import net.leetsoft.mangareader.MangoActivity;
import net.leetsoft.mangareader.R;
import net.leetsoft.mangareader.ui.MangoAdWrapperView;

public class AboutActivity extends MangoActivity
{
    private TextView mText;
    private TextView mAckText;
    private TextView mLegalText;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setTitle("About Mango", null);
        inflateLayoutManager(this, R.layout.about);
        super.setAdLayout((MangoAdWrapperView) findViewById(R.id.aboutAdLayout));
        super.setJpBackground(R.drawable.jp_bg_about);

        mText = (TextView) this.findViewById(R.id.AboutText);
        mAckText = (TextView) this.findViewById(R.id.AboutAckText);
        mLegalText = (TextView) this.findViewById(R.id.AboutLegalText);

        StringBuilder builder = new StringBuilder();
        builder.append("Mango for Android v" + Mango.VERSION_FULL);
        builder.append("\n\nVersion Name:\n\t" + Mango.VERSION_BUILDID);
        builder.append("\nVersion ID:\n\t" + Mango.VERSION_NETID);
        builder.append("\nDevice ID:\n\t" + Mango.getPin());
        builder.append("\nPackage ID:\n\t" + getApplicationContext().getPackageName());
        builder.append("\n\nDeveloped by Leetsoft.\n\n");
        mText.setText(builder.toString());

        builder = new StringBuilder();
        builder.append("Mango uses the ActionBarSherlock library to implement Action Bar functionality on pre-Honeycomb platforms.  Check it out at <actionbarsherlock.com>.\n\n");
        builder.append("Mango uses a modified version of the ZoomableImageView class, originally written by Laurence Dawson, to implement multi-touch zooming.  Check it out at <github.com/laurencedawnson/ZoomableImageView>.\n\n");
        builder.append("All manga content, cover art, summaries, lists, and pages are retrieved from third-party websites.\n\n");
        mAckText.setText(builder.toString());

        builder = new StringBuilder();
        builder.append("This product and its developer are not affiliated with or endorsed by any of the third-party websites linked in this app in any way.\n\n");
        builder.append("All content is user-submitted and hosted by third parties.\n\n");
        builder.append("Android is a trademark of Google Inc. Use of this trademark is subject to Google Permissions.\n\n");
        builder.append("Privacy Notice: As with most other websites and online services, the Mango Service logs traffic passing through it. The information in these logs include your device ID, your Mango version ID, date/time of access, and the data requested. This information is used only for troubleshooting or for aggregated traffic analysis and is never shared or disclosed with any other party.\n\n");
        builder.append("Use of this app is subject to the Terms of Service, which can be read by going to:\n<http://mango.leetsoft.net/terms.php>\n\n");
        builder.append("\nFor support, product updates, or to contact the developer with comments or suggestions, just select Send Feedback from the menu or go to Settings and Help >> Send Feedback. You can also visit:\n<http://mango.leetsoft.net>\n\n");
        builder.append("Follow Mango on Facebook (/MangoApp) and Twitter (@MangoApp)!\n\n");
        builder.append("Copyright (c)2009-2014 Leetsoft.  All rights reserved.\n\n");
        builder.append("Thanks for using Mango!\n\n\n");
        mLegalText.setText(builder.toString());
    }
}
