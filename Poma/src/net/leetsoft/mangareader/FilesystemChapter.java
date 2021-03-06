package net.leetsoft.mangareader;

import java.io.File;
import java.io.FileFilter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FilesystemChapter implements Serializable
{
    /**
     *
     */
    private static final long serialVersionUID = -7707221346405126862L;
    public File fileObj;
    public boolean isChapter;
    public int pages;

    public boolean isParentFolder;

    public void isValidChapterFolder()
    {
        if (!fileObj.exists())
            return;

        isChapter = false;
        pages = 0;

        File[] imagefiles = fileObj.listFiles(new FileFilter()
        {

            @Override
            public boolean accept(File pathname)
            {
                if (pathname.isDirectory())
                    return true;
                if (pathname.getName().endsWith(".jpg") || pathname.getName().endsWith(".png"))
                    return true;
                return false;
            }
        });

        if (imagefiles == null || imagefiles.length == 0)
            return;

        for (int i = 0; i < imagefiles.length; i++)
        {
            if (imagefiles[i].isDirectory())
                return;

            pages++;
        }

        if (pages < 3)
            isChapter = false;
        else
            isChapter = true;
    }

    public Page[] generatePageArray()
    {
        if (!isChapter)
            return null;

        ArrayList<Page> pages = new ArrayList<Page>();
        float highestIndex = -1;

        // Enumerate valid files
        File[] imagefiles = fileObj.listFiles(new FileFilter()
        {

            @Override
            public boolean accept(File pathname)
            {
                if (pathname.getName().endsWith(".jpg") || pathname.getName().endsWith(".png"))
                    return true;
                return false;
            }
        });

        if (imagefiles == null || imagefiles.length < 3)
            return null;

        Arrays.sort(imagefiles, new SimpleFileComparator());

        String prefix = getCommonPrefix(imagefiles);

        for (int i = 0; i < imagefiles.length; i++)
        {
            try
            {
                Page newpage = new Page();
                String imgName = stripExtension(imagefiles[i].getName());
                imgName = imgName.substring(prefix.length());
                pages.add(newpage);
                newpage.id = imgName;
                newpage.url = imagefiles[i].getName();

            } catch (Exception e)
            {
                Mango.log("Problem processing " + imagefiles[i].getAbsolutePath() + ".");
                return null;
            }
        }

        Page[] pageArray = new Page[pages.size()];
        pages.toArray(pageArray);
        Arrays.sort(pageArray, new PageComparator());

        return pageArray;
    }

    public static String stripExtension(String str)
    {
        return str.substring(0, str.lastIndexOf('.'));
    }

    public static String getCommonPrefix(File[] strings)
    {
        String prefix = strings[0].getName();
        for (int i = 0; i < strings.length; i++)
        {
            String str = strings[i].getName();
            if (str.startsWith(prefix))
                continue;
            while (prefix.length() > 0)
            {
                // Remove last character from prefix and retest
                prefix = prefix.substring(0, prefix.length() - 1);
                if (str.startsWith(prefix))
                    break;
            }
            // prefix is empty so there is no common prefix
            if (prefix.length() == 0)
                break;
        }
        return prefix;
    }

    private class SimpleFileComparator implements Comparator<File>
    {
        @Override
        public int compare(File o1, File o2)
        {
            String tmp1 = o1.getName();
            String tmp2 = o2.getName();
            return tmp1.compareTo(tmp2);
        }
    }

    private class PageComparator implements Comparator<Page>
    {
        @Override
        public int compare(Page o1, Page o2)
        {
            float pagenum1 = 0;
            float pagenum2 = 0;

            boolean nonNumeric1 = false;
            boolean nonNumeric2 = false;

            Pattern regex = Pattern.compile("^.*?(\\d*\\.??\\d)\\D*?$");
            Matcher regexMatcher = regex.matcher(o1.id);
            if (regexMatcher.find())
                pagenum1 = Float.parseFloat(regexMatcher.group(1));
            else
                nonNumeric1 = true;

            regexMatcher = regex.matcher(o2.id);
            if (regexMatcher.find())
                pagenum2 = Float.parseFloat(regexMatcher.group(1));
            else
                nonNumeric2 = true;

            // Non numeric
            if (nonNumeric1 || nonNumeric2)
            {
                if (nonNumeric1 && nonNumeric2)
                    return o1.id.compareTo(o2.id);
                else if (nonNumeric1)
                    return 1;
                else
                    return -1;
            }

            // To support page numbers like 1.5, we'll multiply by 10 before truncating the decimal.
            return (int) ((pagenum1 - pagenum2) * 10);
        }
    }
}
