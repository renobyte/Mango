package net.leetsoft.mangareader;

import java.io.Serializable;
import java.util.HashMap;

public class SyncChange implements Serializable
{
	String                  syncQueryString;

	Favorite                syncFavorite;
	long                    syncIndex;
	int                     syncChangeType;
	HashMap<String, String> syncModifiedData;
}
