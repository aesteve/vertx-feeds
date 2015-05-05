package io.vertx.feeds.utils.rss;

import java.text.SimpleDateFormat;
import java.util.Date;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndImage;

public class FeedUtils {
	
	public static SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
	
	public static JsonObject toJson(SyndFeed feed) {
		JsonObject json = new JsonObject();
		json.put("title", feed.getTitle());
		json.put("description", feed.getDescription());
		SyndImage image = feed.getImage();
		if (image != null) {
			json.put("image", feed.getImage().getUrl());
			// or getLink() ?
		}
		json.put("published", toJson(feed.getPublishedDate()));
		JsonArray entries = new JsonArray();
		if (feed.getEntries() != null) {
			feed.getEntries().forEach(entry -> {
				entries.add(toJson(entry));
			});
		}
		json.put("entries", entries);
		return json;

	}
	
	public static JsonObject toJson(SyndEntry entry) {
		JsonObject json = new JsonObject();
		json.put("title", entry.getTitle());
		json.put("published", toJson(entry.getPublishedDate()));
		json.put("link", entry.getLink());
		SyndContent description = entry.getDescription();
		if (description != null) {
			json.put("description", description.getValue());
		}
		return json;
	}
	
	public static String toJson(Date date) {
		if (date == null) {
			return null;
		}
		return isoDateFormat.format(date);
	}
}
