package net.leetsoft.mangareader;

import android.content.Context;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Pattern;

public class MangoLibraryIO
{
    public static String writeNoMedia(String path)
    {
        if (path.startsWith("/PocketManga/"))
            path = path.replace("/PocketManga/", "/Mango/");
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
        {
            File file = new File(Mango.getDataDirectory() + path);

            try
            {
                file = new File(Mango.getDataDirectory() + path + ".nomedia");
                if (file.exists())
                    file.delete();
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            catch (Exception e)
            {
                // swallow the exception if it happens for the nomedia file, since
                // we'd like to continue anyway.
            }
        }
        else
        {
            return "SD card not available for writing";
        }
        return "okay";
    }

    public static String writeIndexData(String path, String data)
    {
        if (path.startsWith("/PocketManga/"))
            path = path.replace("/PocketManga/", "/Mango/");
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
        {
            File file = new File(Mango.getDataDirectory() + path);
            BufferedWriter out = null;

            MangoLibraryIO.writeNoMedia(path);

            try
            {
                file = new File(Mango.getDataDirectory() + path + "index.xml");
                if (file.exists())
                    file.delete();
                file.getParentFile().mkdirs();
                file.createNewFile();
                out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
                out.write(data);
            }
            catch (IOException ioe)
            {
                Mango.log("MangoLibraryIO", "IOException when writing to disk! (" + String.valueOf(file.getAbsolutePath()) + ", " + path + "index.xml, " + ioe.getMessage() + ")");
                return ioe.toString();
            }
            finally
            {
                try
                {
                    if (out != null)
                        out.close();
                    out = null;
                }
                catch (IOException e)
                {

                }
            }
        }
        else
        {
            Mango.log("MangoLibraryIO", "SD card is not accessible.");
            return "SD card not available for writing";
        }
        return "okay";
    }

    public static String deleteIndexData(String path)
    {
        if (path != null && path.startsWith("/PocketManga/"))
            path = path.replace("/PocketManga/", "/Mango/");
        Mango.log("Deleting index data for " + path);
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
        {
            File file = new File(Mango.getDataDirectory() + path);

            file.mkdirs();
            file = new File(Mango.getDataDirectory() + path + "index.xml");
            if (file.exists())
                file.delete();
        }
        else
        {
            Mango.log("MangoLibraryIO", "SD card is not accessible.");
            return "SD card not available for writing";
        }
        return "okay";
    }

    public static String readIndexData(String path)
    {
        if (path.startsWith("/PocketManga/"))
            path = path.replace("/PocketManga/", "/Mango/");
        String state = Environment.getExternalStorageState();
        if (state.startsWith(Environment.MEDIA_MOUNTED))
        {
            File file;
            BufferedReader br = null;
            try
            {

                file = new File(Mango.getDataDirectory() + path + "index.xml");
                if (!file.exists())
                    return null;
                br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[8192];
                int charsRead = 0;
                while ((charsRead = br.read(buffer)) > 0)
                {
                    builder.append(buffer, 0, charsRead);
                    buffer = new char[8192];
                }
                return builder.toString();
            }
            catch (IOException ioe)
            {
                Mango.log("MangoLibraryIO", "IOException when reading index from disk! (" + path + "index.xml, " + ioe.getMessage() + ")");
            }
            finally
            {
                try
                {
                    if (br != null)
                        br.close();
                    br = null;
                }
                catch (IOException e)
                {

                }
            }
        }
        else
        {
            Mango.log("MangoLibraryIO", "Could not read " + path + "index.xml from disk because external media is not available for reading.");
        }
        return null;
    }

    public static String writeEncodedImageToDisk(String path, String filename, byte[] data)
    {
        if (path.startsWith("/PocketManga/"))
            path = path.replace("/PocketManga/", "/Mango/");
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state))
        {
            File file = new File(Mango.getDataDirectory() + path);
            FileOutputStream out = null;
            try
            {
                file.mkdirs();
                file = new File(Mango.getDataDirectory() + path + filename + "_temp");
                if (file.exists())
                    file.delete();
                file.createNewFile();
                out = new FileOutputStream(file);
                long time = System.currentTimeMillis();
                out.write(data);
                Mango.log("MangoLibraryIO", "Wrote byte array to disk in " + String.valueOf(System.currentTimeMillis() - time) + "ms. (" + filename + ")");
                file.renameTo(new File(Mango.getDataDirectory() + path + filename + ".jpg"));
            }
            catch (IOException ioe)
            {
                Mango.log("MangoLibraryIO", "IOException when writing byte array to disk! (" + String.valueOf(file.getAbsolutePath()) + ", " + filename + ", " + ioe.getMessage() + ")");
                return ioe.toString();
            }
            finally
            {
                try
                {
                    if (out != null)
                        out.close();
                    out = null;
                }
                catch (IOException e)
                {

                }
            }
        }
        else
        {
            Mango.log("MangoLibraryIO", "SD card is not accessible.");
            return "SD card not available for writing";
        }
        return "okay";
    }

    public static boolean checkBitmap(String path, String filename)
    {
        if (path.startsWith("/PocketManga/"))
            path = path.replace("/PocketManga/", "/Mango/");
        String state = Environment.getExternalStorageState();
        if (state.startsWith(Environment.MEDIA_MOUNTED))
        {
            File file;
            try
            {
                file = new File(Mango.getDataDirectory() + path + filename);
                if (!file.exists())
                    file = new File(Mango.getDataDirectory() + path + filename + ".jpg");

                if (!file.exists())
                    throw new Exception("File does not exist (" + file.getAbsolutePath() + ")");

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                if (options.outMimeType == null)
                    throw new Exception("Couldn't decode file " + file.getAbsolutePath());
                return true;
            }
            catch (Exception ioe)
            {
                Mango.log("MangoLibraryIO", "Exception when checking for bitmap. " + ioe.getMessage() + ")");
                return false;
            }
        }
        else
        {
            Mango.log("MangoLibraryIO", "Could not read " + path + filename + " from disk because external media is not available for reading.");
            return false;
        }
    }

    public static Bitmap readBitmapFromDisk(String path, String filename, int sampleSize, boolean prepend)
    {
        if (path.startsWith("/PocketManga/"))
            path = path.replace("/PocketManga/", "/Mango/");
        String state = Environment.getExternalStorageState();
        if (state.startsWith(Environment.MEDIA_MOUNTED))
        {
            File file;
            try
            {
                file = new File((prepend ? Mango.getDataDirectory() : "") + path + filename);
                if (!file.exists())
                    file = new File((prepend ? Mango.getDataDirectory() : "") + path + filename + ".jpg");

                if (!file.exists())
                    throw new Exception("File does not exist (" + file.getAbsolutePath() + ")");

                // long time = System.currentTimeMillis();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;

                Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                if (bm == null)
                    throw new Exception("(" + file.getAbsolutePath() + " is not a valid bitmap or file couldn't be accessed)");
                return bm;
            }
            catch (Exception ioe)
            {
                Mango.log("MangoLibraryIO", "Exception when reading bitmap from disk! " + ioe.getMessage() + ")");
            }
            catch (OutOfMemoryError oom)
            {
                if (sampleSize > 3)
                {
                    Mango.log("Mango went /oom while decoding bitmap. Blame the tank :'(");
                    Mango.log("Device heap size is " + (java.lang.Runtime.getRuntime().maxMemory() / 1024 / 1024) + "MB");
                    Mango.log("Returning null.");
                    return null;
                }
                else
                {
                    Mango.log("Downsampling image because there is not enough memory. (" + path + filename + ")");
                    return MangoLibraryIO.readBitmapFromDisk(path, filename, sampleSize + 1, prepend);
                }
            }
        }
        else
        {
            Mango.log("MangoLibraryIO", "Could not read " + path + filename + " from disk because external media is not available for reading.");
        }
        return BitmapFactory.decodeResource(Mango.CONTEXT.getResources(), R.drawable.img_decodefailure);
    }

    public static String readReportData()
    {
        String state = Environment.getExternalStorageState();
        if (state.startsWith(Environment.MEDIA_MOUNTED))
        {
            File file;
            BufferedReader br = null;
            try
            {
                file = new File(Mango.getDataDirectory() + "/Mango/library/report.txt");
                if (!file.exists())
                    return null;
                br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[8192];
                int charsRead = 0;
                while ((charsRead = br.read(buffer)) > 0)
                {
                    builder.append(buffer, 0, charsRead);
                    buffer = new char[8192];
                }
                return builder.toString();
            }
            catch (IOException ioe)
            {
                Mango.log("MangoLibraryIO", "IOException when reading index from disk! (/Mango/library/report.txt, " + ioe.getMessage() + ")");
            }
            finally
            {
                try
                {
                    if (br != null)
                        br.close();
                    br = null;
                }
                catch (IOException e)
                {

                }
            }
        }
        else
        {
            Mango.log("MangoLibraryIO", "Could not read /Mango/library/report.txt from disk because external media is not available for reading.");
        }
        return null;
    }

    public static String writeReportData(String data)
    {
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
        {
            File file = new File(Mango.getDataDirectory() + "/Mango/library/");
            BufferedWriter out = null;

            try
            {
                file.mkdirs();
                file = new File(Mango.getDataDirectory() + "/Mango/library/report.txt");
                if (file.exists())
                    file.delete();
                file.createNewFile();
                out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
                out.write(data);
            }
            catch (IOException ioe)
            {
                Mango.log("MangoLibraryIO", "IOException when writing to disk! (" + String.valueOf(file.getAbsolutePath()) + ", " + "/Mango/library/report.txt, " + ioe.getMessage() + ")");
                return ioe.toString();
            }
            finally
            {
                try
                {
                    if (out != null)
                        out.close();
                    out = null;
                }
                catch (IOException e)
                {

                }
            }
        }
        else
        {
            Mango.log("MangoLibraryIO", "SD card is not accessible.");
            return "SD card not available for writing";
        }
        return "okay";
    }

    public static boolean checkForLibraryBackup()
    {
        File file = new File(Mango.getDataDirectory() + "/Mango/library/database.xml");
        if (file.exists())
            return true;
        file = new File(Mango.getDataDirectory() + "/Mango/library/backup.xml");
        if (file.exists())
            return true;
        return false;
    }

    public static void writeLibraryBackup(final LibraryChapter[] chapters)
    {
        Runnable r = new Runnable()
        {

            @Override
            public void run()
            {

                File file = new File(Mango.getDataDirectory() + "/Mango/library/");
                BufferedWriter out = null;

                try
                {
                    file.mkdirs();
                    file = new File(Mango.getDataDirectory() + "/Mango/library/database.xml");
                    if (file.exists())
                        file.delete();
                    if (chapters.length == 0)
                    {
                        Mango.log("Deleting database.xml because the library is now empty.");
                        return;
                    }
                    file.createNewFile();
                    out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
                    out.write("<!--Warning: editing this file and then trying to restore it may crash Mango, break your library, destroy your phone, kill your dog,"
                            + " burn down your house, cause a nuclear reactor meltdown, or otherwise result in all manner of undesirable effects. Don't do it.\nIf all"
                            + " hell breaks lose, or if you can't open your library screen after restoring, select Clear User Data from the Settings Menu.-->\r\n\r\n<librarychapters>");
                    long time = System.currentTimeMillis();
                    Mango.log("Backing up " + chapters.length + " items.");
                    for (int i = 0; i < chapters.length; i++)
                    {
                        LibraryChapter temp = chapters[i];
                        out.append("<chapter rowid=\"" + String.valueOf(chapters[i].rowId) + "\">\n");
                        out.append("<mId value=\"" + String.valueOf(temp.manga.id) + "\"/>\n");
                        out.append("<mName value=\"" + getXmlFormattedString(temp.manga.title) + "\"/>\n");
                        out.append("<cIndex value=\"" + String.valueOf(temp.chapterIndex) + "\"/>\n");
                        out.append("<cId value=\"" + String.valueOf(temp.chapter.id) + "\"/>\n");
                        out.append("<cName value=\"" + getXmlFormattedString(temp.chapter.title) + "\"/>\n");
                        out.append("<path value=\"" + String.valueOf(chapters[i].path) + "\"/>\n");
                        out.append("<site value=\"" + String.valueOf(chapters[i].siteId) + "\"/>\n");
                        out.append("</chapter>\n");
                    }
                    out.append("</librarychapters>");
                    Mango.log("Finished writing backup in " + (System.currentTimeMillis() - time) + "ms.");
                }
                catch (IOException ioe)
                {
                    Mango.log("Failed to write library backup. " + ioe.toString());
                }
                finally
                {
                    try
                    {
                        if (out != null)
                            out.close();
                        out = null;
                    }
                    catch (IOException e)
                    {

                    }
                }
            }
        };
        Thread thread = new Thread(r);
        thread.start();
    }

    private static String getXmlFormattedString(String str)
    {
        if (str == null)
            return "null";
        str = str.replace("\"", "&quot;");
        str = str.replace("&", "&amp;");
        str = str.replace("<", "&lt;");
        str = str.replace(">", "&gt;");
        return str;
    }

    public static boolean readLibraryBackup(final Context context)
    {
        String state = Environment.getExternalStorageState();
        if (!state.startsWith(Environment.MEDIA_MOUNTED))
        {
            Mango.log("Unable to restore library data because SD card isn't available for reading.");
            return false;
        }
        File file;
        BufferedReader br = null;
        try
        {
            file = new File(Mango.getDataDirectory() + "/Mango/library/database.xml");
            if (!file.exists())
            {
                file = new File(Mango.getDataDirectory() + "/Mango/library/backup.xml");
                if (!file.exists())
                {
                    Mango.log("File does not exist (" + file.getAbsolutePath() + ")");
                    return false;
                }
            }
            Mango.log("Reading database.xml into memory...");
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            int charsRead = 0;
            while ((charsRead = br.read(buffer)) > 0)
            {
                builder.append(buffer, 0, charsRead);
                buffer = new char[8192];
            }

            String xmlData = builder.toString();
            builder = null;

            Mango.log("Parsing database.xml...");

            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            SAXParser parser = saxFactory.newSAXParser();
            XMLReader reader = parser.getXMLReader();
            LibrarySaxHandler handler = new LibrarySaxHandler();
            reader.setContentHandler(handler);
            reader.parse(new InputSource(new StringReader(xmlData)));

            ArrayList<LibraryChapter> arrayList = new ArrayList<LibraryChapter>();
            arrayList.addAll(handler.getAllChapters());

            if (arrayList.size() == 0)
            {
                writeLibraryBackup(new LibraryChapter[0]);
                return false;
            }

            Mango.log("Inserting " + arrayList.size() + " records into database...");

            MangoSqlite db = new MangoSqlite(context);
            Pattern p = Pattern.compile("[^a-z0-9]");

            try
            {
                db.open();
                db.db.execSQL("DELETE FROM tLibrary WHERE 1=1");
                for (Iterator<LibraryChapter> iterator = arrayList.iterator(); iterator.hasNext(); )
                {
                    LibraryChapter lc = iterator.next();
                    lc.manga.generateSimpleName(p);
                    db.insertLibraryChapter(lc.manga.id, lc.manga.title, lc.manga.simpleName, lc.chapterIndex, lc.chapter.title, lc.chapter.id, lc.chapterCount, lc.chapter.url, lc.path, lc.siteId);
                }
            }
            catch (SQLException ex)
            {
                throw ex;
            }
            finally
            {
                db.close();
            }
            Mango.log("Successfully imported library data.");
        }
        catch (Exception ioe)
        {
            Mango.log("Failed to import data. " + ioe.getClass().getSimpleName() + ": " + ioe.getMessage());
        }
        finally
        {
            try
            {
                if (br != null)
                    br.close();
                br = null;
            }
            catch (IOException e)
            {

            }
        }
        MangoLibraryIO.importSiteIds(context);
        return true;
    }

    public static class LibrarySaxHandler extends DefaultHandler
    {
        ArrayList<LibraryChapter> allChapters;
        LibraryChapter thisChapter;

        public ArrayList<LibraryChapter> getAllChapters()
        {
            return this.allChapters;
        }

        @Override
        public void startDocument() throws SAXException
        {
            super.startDocument();
            allChapters = new ArrayList<LibraryChapter>();
            thisChapter = null;
        }

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException
        {
            if (localName.equalsIgnoreCase("chapter"))
            {
                this.thisChapter = new LibraryChapter();
                thisChapter.manga = new Manga();
                thisChapter.chapter = new Chapter();
            }
            else if (localName.equalsIgnoreCase("mId"))
            {
                thisChapter.manga.id = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("mName"))
            {
                thisChapter.manga.title = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("cIndex"))
            {
                thisChapter.chapterIndex = Integer.parseInt(attributes.getValue(0));
            }
            else if (localName.equalsIgnoreCase("cId"))
            {
                thisChapter.chapter.id = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("cName"))
            {
                thisChapter.chapter.title = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("cUrl"))
            {
                thisChapter.chapter.url = attributes.getValue(0);
            }
            else if (localName.equalsIgnoreCase("cCount"))
            {
                thisChapter.chapterCount = Integer.parseInt(attributes.getValue(0));
            }
            else if (localName.equalsIgnoreCase("site"))
            {
                thisChapter.siteId = Integer.parseInt(attributes.getValue(0));
            }
            else if (localName.equalsIgnoreCase("path"))
            {
                thisChapter.path = attributes.getValue(0);
            }
        }

        @Override
        public void endElement(String uri, String localName, String name) throws SAXException
        {
            super.endElement(uri, localName, name);
            if (this.thisChapter != null)
            {
                if (localName.equalsIgnoreCase("chapter"))
                {
                    allChapters.add(thisChapter);
                }
            }
        }
    }

    public static void deleteFiles(LibraryChapter chapter)
    {
        String state = Environment.getExternalStorageState();
        try
        {
            if (state.startsWith(Environment.MEDIA_MOUNTED))
            {
                File file;
                file = new File(Mango.getDataDirectory() + "/Mango/library/" + chapter.manga.id + "/" + chapter.chapter.id + "/");
                if (file.exists())
                {
                    File[] allFiles = file.listFiles();
                    for (int i = 0; i < allFiles.length; i++)
                    {
                        allFiles[i].delete();
                    }
                    while (file.getParent() != null)
                    {
                        if (file.list().length == 0)
                        {
                            Mango.log("Deleting directory " + file.getAbsolutePath());
                            file.delete();
                        }
                        file = new File(file.getParent());
                    }

                }
            }
            else
            {
                Mango.log("MangoLibraryIO", "SD Card is not accessible.");
            }
        }
        catch (Exception e)
        {
            Mango.log("MangoLibraryIO", "Failed to delete file or directory. " + e.toString());
        }
    }

    public static void importSiteIds(Context context)
    {
        Mango.log("Checking for chapters with missing siteId...");
        MangoSqlite db = new MangoSqlite(context);
        db.open();
        Mango.log("Getting chapters from database...");
        LibraryChapter[] l = db.getAllLibraryChapters(null);
        Mango.log("Verifying " + l.length + " chapters...");
        for (int i = 0; i < l.length; i++)
        {
            LibraryChapter c = l[i];

            if (c.siteId == 0)
            {
                String indexdata = MangoLibraryIO.readIndexData(c.path);
                if (indexdata == null)
                {
                    Mango.log(c.manga.title + " " + c.chapter.id + " is missing a siteid, but has no path");
                    continue;
                }
                if (indexdata.contains("mangafox"))
                    c.siteId = Mango.SITE_MANGAFOX;
                else if (indexdata.contains("mangable"))
                    c.siteId = Mango.SITE_MANGABLE;
                else if (indexdata.contains("animea"))
                    c.siteId = Mango.SITE_ANIMEA;
                else if (indexdata.contains("mangashare"))
                    c.siteId = Mango.SITE_MANGASHARE;
                else if (indexdata.contains("submanga"))
                    c.siteId = Mango.SITE_SUBMANGA;
                else if (indexdata.contains("mangastream"))
                    c.siteId = Mango.SITE_MANGASTREAM;
                Mango.log(c.manga.title + " " + c.chapter.id + " is now tagged as " + Mango.getSiteName(c.siteId));
                db.updateLibraryChapter(c);
            }
        }
        db.close();
        Mango.log("Done.");
    }
}
