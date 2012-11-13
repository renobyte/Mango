package net.leetsoft.mangareader;

import java.io.Serializable;
import java.util.ArrayList;

public class SyncQueue implements Serializable
{
	public ArrayList<SyncChange> queueAdd;
	public ArrayList<SyncChange> queueRemove;
	public ArrayList<SyncChange> queueRead;
	public ArrayList<SyncChange> queueUpdate;

}
