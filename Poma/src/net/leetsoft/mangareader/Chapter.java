package net.leetsoft.mangareader;

import java.io.Serializable;

@SuppressWarnings("serial")
public class Chapter implements Serializable
{
	public String title;
	public String id;
	public String url;
	public Page[] pages;
	public String scanlator;
	public String date;
}
