package net.leetsoft.mangareader.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import net.leetsoft.mangareader.Mango;
import net.leetsoft.mangareader.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TreeMap;

public class SharedPrefsActivity extends Activity
{
    ListView mList;
    EditText mEdit;
    TextView mText;

    SPEAdapter mAdapter;

    int mEditIndex = -1;
    private TreeMap<String, Object> mSpMap;
    private String[] mSpKeys;
    private Object[] mSpValues;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.sharedprefseditor);
        this.setTitle("SharedPreference Editor");

        mText = (TextView) this.findViewById(R.id.sharedPrefsText);
        mEdit = (EditText) this.findViewById(R.id.sharedPrefsEdit);
        mList = (ListView) this.findViewById(R.id.sharedPrefsList);

        initialize();

        Mango.alert("This screen allows you to change Mango's internal SharedPreferences data.  You should only mess with this if you know what you're doing or if you're receiving instructions from support.<br /><br /><strong>Be careful; entering invalid values here can cause the app to stop working!</strong>", "Here be dragons!", this);
    }

    private void initialize()
    {
        mEditIndex = -1;
        mEdit.setEnabled(true);
        mEdit.setText("");
        mSpMap = new TreeMap<String, Object>(Mango.getSharedPreferences().getAll());

        ArrayList<String> k = new ArrayList<String>();
        ArrayList<Object> v = new ArrayList<Object>();

        Object[] array = new Object[mSpMap.size()];
        mSpMap.keySet().toArray(array);
        Object[] array2 = new Object[mSpMap.size()];
        mSpMap.values().toArray(array2);
        for (int i = 0; i < array.length; i++)
        {
            String str = array[i].toString();
            Object obj = array2[i];
            if (!str.contains("bankai"))
            {
                k.add(str);
                v.add(obj);
            }
        }

        mSpKeys = new String[k.size()];
        mSpValues = new Object[v.size()];
        k.toArray(mSpKeys);
        v.toArray(mSpValues);

        mAdapter = new SPEAdapter(this);
        mList.setAdapter(mAdapter);
        mList.setOnItemClickListener(new OnItemClickListener()
        {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3)
            {
                if (arg2 == mEditIndex)
                    commit();

                mText.setText("Editing " + mSpKeys[arg2] + "(" + mSpValues[arg2].getClass().getSimpleName() + ")" + "\nPress Back to cancel\nPress Menu or tap green row to commit\nCommit a blank string to delete");
                if (mSpValues[arg2].getClass().getSimpleName().equalsIgnoreCase("long"))
                    mText.setText(mText.getText() + "\n" + "Time input format is Unix time");
                mEdit.setText(mSpValues[arg2].toString());
                mEditIndex = arg2;
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    class SPEViewHolder
    {
        TextView text;
        ImageView icon;
        ImageView star;
        RelativeLayout overlay;
    }

    class SPEAdapter extends ArrayAdapter<String>
    {
        LayoutInflater mInflater;

        public SPEAdapter(Activity context)
        {
            super(context, R.layout.iconlistrow, mSpKeys);
            mInflater = context.getLayoutInflater();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent)
        {
            SPEViewHolder holder;
            if (convertView == null || convertView.getTag() == null)
            {
                convertView = mInflater.inflate(R.layout.iconlistrow, null);
                holder = new SPEViewHolder();
                holder.overlay = (RelativeLayout) convertView.findViewById(R.id.rowHolder);
                holder.icon = (ImageView) convertView.findViewById(R.id.icon);
                holder.star = (ImageView) convertView.findViewById(R.id.star);
                holder.text = (TextView) convertView.findViewById(R.id.label);
                convertView.setTag(holder);
                holder.icon.setImageResource(R.drawable.ic_librarychapter);
            }
            else
            {
                holder = (SPEViewHolder) convertView.getTag();
            }

            holder.icon.setVisibility(View.GONE);
            holder.star.setVisibility(View.GONE);
            holder.text.setText(String.valueOf(mSpKeys[position]) + ": " + String.valueOf(mSpValues[position].toString()));

            try
            {
                if (mSpValues[position].getClass().getSimpleName().equalsIgnoreCase("long"))
                    holder.text.setText(String.valueOf(mSpKeys[position]) + ": " + new SimpleDateFormat("MM/dd/yyyy hh:mm:ss").format(((Long) mSpValues[position]).longValue()));
            } catch (Exception ex)
            {
                holder.text.setText(String.valueOf(mSpKeys[position]) + ": " + String.valueOf(mSpValues[position].toString()));
            }

            if (mEditIndex == position)
            {
                holder.overlay.setBackgroundColor(Color.argb(60, 0, 255, 110));
            }
            else
            {
                holder.overlay.setBackgroundColor(Color.TRANSPARENT);
            }

            return convertView;
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (mEditIndex != -1)
            {
                mEditIndex = -1;
                mEdit.setText("");
                mText.setText("Tap on an item to edit it.");
                mAdapter.notifyDataSetChanged();
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU)
        {
            commit();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void commit()
    {
        if (mEdit.getText().toString().toLowerCase().equals("bankai"))
        {
            Intent myIntent = new Intent();
            myIntent.setClass(Mango.CONTEXT, BankaiActivity.class);
            startActivity(myIntent);
            return;
        }

        if (mEditIndex == -1)
            return;

        try
        {
            if (mEdit.getText().length() == 0)
            {
                Mango.getSharedPreferences().edit().remove(mSpKeys[mEditIndex]).commit();
            }
            else if (mSpValues[mEditIndex].getClass().getSimpleName().equalsIgnoreCase("string"))
            {
                Mango.getSharedPreferences().edit().putString(mSpKeys[mEditIndex], mEdit.getText().toString()).commit();
            }
            else if (mSpValues[mEditIndex].getClass().getSimpleName().equalsIgnoreCase("integer"))
            {
                Mango.getSharedPreferences().edit().putInt(mSpKeys[mEditIndex], Integer.parseInt(mEdit.getText().toString())).commit();
            }
            else if (mSpValues[mEditIndex].getClass().getSimpleName().equalsIgnoreCase("long"))
            {
                Mango.getSharedPreferences().edit().putLong(mSpKeys[mEditIndex], Long.parseLong(mEdit.getText().toString())).commit();
            }
            else if (mSpValues[mEditIndex].getClass().getSimpleName().equalsIgnoreCase("boolean"))
            {
                Mango.getSharedPreferences().edit().putBoolean(mSpKeys[mEditIndex], Boolean.parseBoolean(mEdit.getText().toString())).commit();
            }
            mEditIndex = -1;
            mEdit.setText("");
            mText.setText("Tap on an item to edit it.");
            Toast.makeText(this, "Edit comitted.", Toast.LENGTH_SHORT).show();
            initialize();
        } catch (Exception e)
        {
            Mango.alert("Invalid input (wrong type or format)", this);
        }
    }
}
