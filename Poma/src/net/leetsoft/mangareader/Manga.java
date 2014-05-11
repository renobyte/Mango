package net.leetsoft.mangareader;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("serial")
public class Manga implements Serializable
{
    public String title;
    public String simpleName;
    public String id;
    public String coverart;
    public Chapter[] chapters;
    public transient long favoriteRowId;
    public transient boolean bookmarked = false;
    public transient MangaDetails details;
    public transient boolean completed = false;
    public transient boolean ecchi = false;

    public void generateSimpleName(Pattern regexPattern)
    {
        if (regexPattern == null)
            simpleName = title.toLowerCase().replaceAll("[^a-z0-9]", "");
        else
        {
            Matcher m = regexPattern.matcher(title.toLowerCase());
            simpleName = m.replaceFirst("");
        }
    }

    public boolean compareTo(Manga m)
    {
        if (m.title.equalsIgnoreCase(this.title))
            return true;

        if (m.id.equalsIgnoreCase(this.id))
            return true;

        if (m.simpleName.equalsIgnoreCase(this.simpleName))
            return true;

        return false;
    }
}
