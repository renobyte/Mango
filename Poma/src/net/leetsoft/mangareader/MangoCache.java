package net.leetsoft.mangareader;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.StatFs;

import java.io.*;

public class MangoCache
{
    public static double getFreeSpace()
    {
        StatFs stat = new StatFs(Mango.getDataDirectory().getPath());
        double sdAvailSize = (double) stat.getAvailableBlocks() * (double) stat.getBlockSize();
        double megabytesAvailable = sdAvailSize / 1048576;
        return megabytesAvailable;
    }

    public static void wipeImageCache(boolean forceClear)
    {
        if (forceClear)
            Mango.log("Force-clearing the cache.");
        String state = Environment.getExternalStorageState();
        if (state.startsWith(Environment.MEDIA_MOUNTED))
        {
            File file;
            file = new File(Mango.getDataDirectory() + "/Mango/cache/img/page/");
            if (file.exists())
            {
                long time = System.currentTimeMillis();
                File[] allFiles = file.listFiles();
                if (allFiles.length < Integer.parseInt(Mango.getSharedPreferences().getString("cacheWipeThreshold", "20")) && !forceClear)
                {
                    Mango.log("MangoCache", "Skipping cache wipe because there are not enough items in it. (only " + allFiles.length + ", need > " + Mango.getSharedPreferences().getString("cacheWipeThreshold", "20") + ")");
                    return;
                }
                for (int i = 0; i < allFiles.length; i++)
                {
                    allFiles[i].delete();
                }
                Mango.log("MangoCache", "Deleted " + allFiles.length + " files in " + String.valueOf(System.currentTimeMillis() - time) + "ms.");
            }
        }
        else
        {
            Mango.log("MangoCache", "Couldn't access cache for cleanup because SD Card is not accessible.");
        }
    }

    public static void wipeCoverArtCache()
    {
        String state = Environment.getExternalStorageState();
        if (state.startsWith(Environment.MEDIA_MOUNTED))
        {
            File file;
            file = new File(Mango.getDataDirectory() + "/Mango/cache/img/cover/");
            if (file.exists())
            {
                File[] allFiles = file.listFiles();
                for (int i = 0; i < allFiles.length; i++)
                {
                    allFiles[i].delete();
                }
            }
        }
        else
        {
            Mango.log("MangoCache", "Couldn't access cache for cleanup because SD Card is not accessible.");
        }
    }

    public static boolean checkCacheForImage(String filepath, String fileName)
    {
        fileName = Integer.toHexString(fileName.hashCode());

        String state = Environment.getExternalStorageState();
        if (state.startsWith(Environment.MEDIA_MOUNTED))
        {
            File file;
            file = new File(Mango.getDataDirectory() + "/Mango/cache/img/" + filepath + fileName);
            if (!file.exists())
                return false;
            else
                return true;
        }
        else
        {
            Mango.log("MangoCache", "Couldn't access cache because SD Card is not accessible. (" + fileName + ")");
            return false;
        }
    }

    public static boolean copyPageToUserFolder(String fileName, String destination)
    {
        fileName = Integer.toHexString(fileName.hashCode());

        destination.replace("/", "_");
        destination.replace("\\", "_");

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state))
        {
            File file = new File(Mango.getDataDirectory() + "/Mango/cache/img/page/");
            InputStream in = null;
            OutputStream out = null;

            try
            {
                file.mkdirs();
                file = new File(Mango.getDataDirectory() + "/Mango/cache/img/page/" + fileName);
                if (!file.exists())
                    throw new IOException("File does not exist.");

                in = new FileInputStream(file);

                file = new File(Mango.getDataDirectory() + "/Mango/user/" + destination);
                file.mkdirs();
                if (file.exists())
                    file.delete();
                file.createNewFile();

                out = new FileOutputStream(file);

                byte[] buffer = new byte[1024];

                Mango.log("Copying file...");
                int len;
                while ((len = in.read(buffer)) > 0)
                {
                    out.write(buffer, 0, len);
                }
                Mango.log("File copied.");

            } catch (IOException ioe)
            {
                Mango.log("MangoCache", "IOException when writing to cache! (" + String.valueOf(file.getAbsolutePath()) + ", " + fileName + ", " + ioe.getMessage() + ")");
                return false;
            } finally
            {
                try
                {
                    if (in != null)
                        in.close();
                    in = null;
                    if (out != null)
                        out.close();
                    out = null;
                } catch (IOException e)
                {

                }
            }
        }
        else
        {
            return false;
        }
        return true;
    }

    public static void writeDataToCache(String data, String fileName)
    {
        fileName = Integer.toHexString(fileName.hashCode());

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state))
        {
            File file = new File(Mango.getDataDirectory() + "/Mango/cache/");
            BufferedWriter out = null;

            try
            {
                file.mkdirs();
                file = new File(Mango.getDataDirectory() + "/Mango/cache/" + fileName);
                if (file.exists())
                    file.delete();
                file.createNewFile();
                out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
                out.write(data);
            } catch (IOException ioe)
            {
                Mango.log("MangoCache", "IOException when writing to cache! (" + String.valueOf(file.getAbsolutePath()) + ", " + fileName + ", " + ioe.getMessage() + ")");
            } finally
            {
                try
                {
                    if (out != null)
                        out.close();
                    out = null;
                } catch (IOException e)
                {

                }
            }
        }
        else
        {
            Mango.log("MangoCache", "Could not write " + fileName + " to cache because external media is not available for writing.");
        }
    }

    public static void writeEncodedImageToCache(byte[] img, String filepath, String fileName)
    {
        fileName = Integer.toHexString(fileName.hashCode());

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state))
        {
            File file = new File(Mango.getDataDirectory() + "/Mango/cache/img/" + filepath);
            FileOutputStream out = null;
            try
            {
                file.mkdirs();
                file = new File(Mango.getDataDirectory() + "/Mango/cache/img/" + filepath + fileName + "_temp");
                if (file.exists())
                    file.delete();
                file.createNewFile();
                out = new FileOutputStream(file);
                long time = System.currentTimeMillis();
                out.write(img);
                Mango.log("MangoCache", "Wrote byte array to disk in " + String.valueOf(System.currentTimeMillis() - time) + "ms. (" + fileName + ")");
                file.renameTo(new File(Mango.getDataDirectory() + "/Mango/cache/img/" + filepath + fileName));
            } catch (IOException ioe)
            {
                Mango.log("MangoCache", "IOException when writing byte array to cache! (" + String.valueOf(file.getAbsolutePath()) + ", " + fileName + ", " + ioe.getMessage() + ")");
            } finally
            {
                try
                {
                    if (out != null)
                        out.close();
                    out = null;
                } catch (IOException e)
                {

                }
            }
        }
        else
        {
            Mango.log("MangoCache", "Could not write " + fileName + " to cache because external media is not available for writing.");
        }
    }

    public static void writeImageToCache(Bitmap img, String filepath, String fileName)
    {
        fileName = Integer.toHexString(fileName.hashCode());

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state))
        {
            File file = new File(Mango.getDataDirectory() + "/Mango/cache/img/" + filepath);
            FileOutputStream out = null;
            try
            {
                file.mkdirs();
                file = new File(Mango.getDataDirectory() + "/Mango/cache/img/" + filepath + fileName + "_temp");
                if (file.exists())
                    file.delete();
                file.createNewFile();
                out = new FileOutputStream(file);
                long time = System.currentTimeMillis();
                img.compress(CompressFormat.JPEG, 100, out);
                Mango.log("MangoCache", "Wrote image to disk in " + String.valueOf(System.currentTimeMillis() - time) + "ms. (" + fileName + ")");
                file.renameTo(new File(Mango.getDataDirectory() + "/Mango/cache/img/" + filepath + fileName));
            } catch (IOException ioe)
            {
                Mango.log("MangoCache", "IOException when writing to cache! (" + String.valueOf(file.getAbsolutePath()) + ", " + fileName + ", " + ioe.getMessage() + ")");
            } finally
            {
                try
                {
                    if (out != null)
                        out.close();
                    out = null;
                } catch (IOException e)
                {

                }
            }
        }
        else
        {
            Mango.log("MangoCache", "Could not write " + fileName + " to cache because external media is not available for writing.");
        }
    }

    public static String readDataFromCache(String fileName)
    {
        fileName = Integer.toHexString(fileName.hashCode());

        String state = Environment.getExternalStorageState();
        if (state.startsWith(Environment.MEDIA_MOUNTED))
        {
            File file;
            BufferedReader br = null;
            try
            {
                file = new File(Mango.getDataDirectory() + "/Mango/cache/" + fileName);
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
            } catch (IOException ioe)
            {
                Mango.log("MangoCache", "IOException when reading from cache! (" + fileName + ", " + ioe.getMessage() + ")");
            } catch (OutOfMemoryError oom)
            {
                Mango.log("Mango ran out of memory while trying to load cache data into StringBuilder!");
                System.gc();
                return "Mango ran out of memory.  Try going to Settings and Help >> Advanced Options >> Restart App.";
            } finally
            {
                try
                {
                    if (br != null)
                        br.close();
                    br = null;
                } catch (IOException e)
                {

                }
            }
        }
        else
        {
            Mango.log("MangoCache", "Could not read " + fileName + " from cache because external media is not available for reading.");
        }
        return null;
    }

    public static Bitmap readBitmapFromCache(String filepath, String fileName, int sampleSize)
    {
        String hashedFilename = Integer.toHexString(fileName.hashCode());

        String state = Environment.getExternalStorageState();
        if (state.startsWith(Environment.MEDIA_MOUNTED))
        {
            File file;
            try
            {
                file = new File(Mango.getDataDirectory() + "/Mango/cache/img/" + filepath + hashedFilename);
                if (!file.exists())
                    throw new Exception("File does not exist (" + file.getAbsolutePath() + ")");
                // long time = System.currentTimeMillis();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;

                Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                if (bm == null)
                    throw new Exception("(" + file.getAbsolutePath() + " is not a valid bitmap or file couldn't be accessed)");
                return bm;
            } catch (Exception ioe)
            {
                Mango.log("MangoCache", "Exception when reading bitmap from cache! " + ioe.getMessage() + ")");
            } catch (OutOfMemoryError oom)
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
                    Mango.log("Downsampling image because there is not enough memory. (" + hashedFilename + ")");
                    return MangoCache.readBitmapFromCache(filepath, fileName, sampleSize + 1);
                }
            }
        }
        else
        {
            Mango.log("MangoCache", "Could not read " + hashedFilename + " from cache because external media is not available for reading.");
        }
        return BitmapFactory.decodeResource(Mango.CONTEXT.getResources(), R.drawable.img_decodefailure);
    }

    public static Bitmap readCustomCoverArt(String fileName, int sampleSize)
    {
        String state = Environment.getExternalStorageState();
        fileName = fileName.substring(fileName.indexOf("@", 6) + 1);
        if (state.startsWith(Environment.MEDIA_MOUNTED))
        {
            File file;
            try
            {
                file = new File(fileName);
                if (!file.exists())
                    return BitmapFactory.decodeResource(Mango.CONTEXT.getResources(), R.drawable.placeholder_error);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;

                Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                if (bm == null)
                    return BitmapFactory.decodeResource(Mango.CONTEXT.getResources(), R.drawable.placeholder_error);
                return bm;
            } catch (Exception ioe)
            {
                Mango.log("MangoCache", "Exception when reading custom cover art! " + ioe.getMessage() + ")");
            } catch (OutOfMemoryError oom)
            {
                if (sampleSize > 3)
                {
                    Mango.log("Mango went /oom while decoding bitmap. Blame the tank :'(");
                    Mango.log("Device heap size is " + (java.lang.Runtime.getRuntime().maxMemory() / 1024 / 1024) + "MB");
                    Mango.log("Returning null.");
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
                }
                else
                {
                    Mango.log("Downsampling image because there is not enough memory. (" + fileName + ")");
                    return MangoCache.readCustomCoverArt(fileName, sampleSize + 1);
                }
            }
        }
        else
        {
            Mango.log("MangoCache", "Could not read " + fileName + " because media is not available for reading.");
        }
        return BitmapFactory.decodeResource(Mango.CONTEXT.getResources(), R.drawable.img_decodefailure);
    }

    public static boolean checkCacheForData(String fileName)
    {
        fileName = Integer.toHexString(fileName.hashCode());

        String state = Environment.getExternalStorageState();
        if (state.startsWith(Environment.MEDIA_MOUNTED))
        {
            File file;
            file = new File(Mango.getDataDirectory() + "/Mango/cache/" + fileName);
            if (!file.exists())
            {
                return false;
            }
            else
            {
                return true;
            }
        }
        else
        {
            Mango.log("MangoCache", "Couldn't access cache because SD Card is not accessible. (" + fileName + ")");
            return false;
        }
    }
}
