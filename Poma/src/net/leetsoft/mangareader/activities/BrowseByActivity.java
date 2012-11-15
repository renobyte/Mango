package net.leetsoft.mangareader.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import net.leetsoft.mangareader.Mango;
import net.leetsoft.mangareader.MangoActivity;
import net.leetsoft.mangareader.R;
import net.leetsoft.mangareader.ui.MangoAdWrapperView;
import net.leetsoft.mangareader.ui.MangoTutorialHandler;

public class BrowseByActivity extends MangoActivity
{
	private MenuChoice[] MENU_CHOICES = new MenuChoice[] { new MenuChoice("All Manga", R.drawable.ic_book_closed),
	        new MenuChoice("Genre", R.drawable.ic_genre),
	        new MenuChoice("Popularity", R.drawable.ic_popular),
	        new MenuChoice("Latest Updates", R.drawable.ic_book_recent),
	        new MenuChoice("Artist", R.drawable.ic_artist),
	        new MenuChoice("Advanced Search", R.drawable.ic_search), };

	class MenuChoice
	{
		String text;
		int    icon;

		MenuChoice(String t, int iconId)
		{
			text = t;
			icon = iconId;
		}
	}

	private ListView browseByList;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setTitle("Browse by...", Mango.getSiteName(Mango.getSiteId()));
		inflateLayoutManager(this, R.layout.mainmenu);
		super.setAdLayout((MangoAdWrapperView) findViewById(R.id.mainmenuAdLayout));
		super.setJpBackground(R.drawable.jp_bg_browseby);

		browseByList = (ListView) findViewById(R.id.MainMenuList);
		browseByList.setAdapter(new BrowseByAdapter(this));
		browseByList.setOnItemClickListener(new OnItemClickListener()
		{

			@Override
			public void onItemClick(AdapterView<?> parent, View v, int position, long id)
			{
				if (position == 0)
				{
					Intent myIntent = new Intent();
                    myIntent.setClass(Mango.CONTEXT, AllMangaActivity.class);
					startActivity(myIntent);
				}
				else if (position == 1)
				{
					Intent intent = new Intent();
                    intent.setClass(Mango.CONTEXT, GenreActivity.class);
					startActivity(intent);
				}
				else if (position == 4)
				{
					Intent intent = new Intent();
                    intent.setClass(Mango.CONTEXT, ArtistActivity.class);
					startActivity(intent);
				}
				else if (position == 2)
				{
					Intent intent = new Intent();
                    intent.setClass(Mango.CONTEXT, FilteredMangaActivity.class);
					intent.putExtra("mode", FilteredMangaActivity.MODE_POPULAR);
					startActivity(intent);
				}
				else if (position == 3)
				{
					Intent intent = new Intent();
                    intent.setClass(Mango.CONTEXT, NewReleasesActivity.class);
					startActivity(intent);
				}
				else if (position == 5)
				{
					Intent intent = new Intent();
                    intent.setClass(Mango.CONTEXT, SearchActivity.class);
					startActivity(intent);
				}
				else
				{
					Mango.alert("This menu item doesn't exist! :o", BrowseByActivity.this);
				}
			}
		});

		if (!Mango.getSharedPreferences().getBoolean("tutorial" + MangoTutorialHandler.BROWSE_BY + "Done", false))
			MangoTutorialHandler.startTutorial(MangoTutorialHandler.BROWSE_BY, this);
	}

	public View getTutorialHighlightView(int index)
	{
		return browseByList.getChildAt(index);
	}

	class ViewHolder
	{
		TextView  text;
		ImageView icon;
		ImageView star;
	}

	class BrowseByAdapter extends ArrayAdapter<MenuChoice>
	{
		LayoutInflater mInflater = null;

		public BrowseByAdapter(MangoActivity context)
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
}
