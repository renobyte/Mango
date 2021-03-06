package net.leetsoft.mangareader.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;

public class MangoFileSelector extends AlertDialog
{
    Context c;
    String curPath;
    FileSelectorListener listener;
    public boolean allowFolders;
    public String title;

    public abstract static class FileSelectorListener
    {
        public abstract void onClick(String path);
    }

    public MangoFileSelector(Context context)
    {
        super(context);
        c = context;
    }

    public void setListener(FileSelectorListener l)
    {
        listener = l;
    }

    public void showSelector(final String path, final FileFilter filter)
    {
        curPath = path;
        Builder a = new Builder(c);
        if (allowFolders)
            a.setTitle(curPath);
        else
            a.setTitle(title);
        File f = new File(curPath);
        File[] files = f.listFiles(filter);
        int sizeAddition = 0;
        if (!curPath.equals("/") && allowFolders)
            sizeAddition = 1;
        if (files == null)
            files = new File[0];

        final String[] fStrings = new String[files.length + sizeAddition];

        if (files.length == 0 && allowFolders)
        {
            fStrings[0] = " Parent Folder";
        }
        else
        {
            for (int i = 0; i < fStrings.length; i++)
            {

                if (i == 0 && !curPath.equals("/") && allowFolders)
                {
                    fStrings[i] = " Parent Folder";
                    continue;
                }
                if (files[i - sizeAddition].isDirectory())
                    fStrings[i] = "/" + files[i - sizeAddition].getName();
                else
                    fStrings[i] = files[i - sizeAddition].getName();
            }
            Arrays.sort(fStrings);
        }
        a.setItems(fStrings, new OnClickListener()
        {

            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                String fileName = fStrings[which];
                if (fStrings[which].startsWith("/"))
                    fStrings[which] = fStrings[which].substring(1);
                if (fileName.equals(" Parent Folder"))
                {
                    File file = new File(curPath);
                    curPath = file.getParent();
                    showSelector(curPath, filter);
                }
                else
                {
                    File file = new File(curPath + "/" + fileName);
                    if (file.isDirectory())
                    {
                        curPath = file.getAbsolutePath();
                        showSelector(curPath, filter);
                    }
                    else
                    {
                        listener.onClick(file.getAbsolutePath());
                    }
                }
            }
        });
        a.show();
    }
}
