package net.leetsoft.mangareader.activities;
import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import net.leetsoft.mangareader.R;

public class SiteInfoDialog extends Dialog
{
	ImageView mLogoView;
	TextView  mDescriptionView;
	Button    mCloseButton;

	public SiteInfoDialog(Context context)
	{
		super(context);
		Window window = this.getWindow();
		window.requestFeature(Window.FEATURE_NO_TITLE);
		this.setContentView(getLayoutInflater().inflate(R.layout.siteinfopopup, null));
		mLogoView = (ImageView) findViewById(R.id.siteInfoLogoImage);
		mDescriptionView = (TextView) findViewById(R.id.siteInfoDescription);
		mCloseButton = (Button) findViewById(R.id.siteInfoCloseButton);
		mCloseButton.setOnClickListener(new View.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				SiteInfoDialog.this.dismiss();
			}
		});
	}
	
	public void initializeInfoPopup(int logoResource, String descriptionText)
	{
		mLogoView.setImageResource(logoResource);
		mDescriptionView.setText(descriptionText);
	}
}
