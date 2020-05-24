package io.vertx.examples.feeds.utils.rss;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FeedConverters {

    public static final SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
	private static final Logger LOG = LoggerFactory.getLogger(FeedConverters.class);

	public static JsonObject toJson(SyndFeed feed) {
		var json = new JsonObject()
                .put("title", feed.getTitle())
                .put("description", feed.getDescription());
		var image = feed.getImage();
		if (image != null) {
			json.put("image", feed.getImage().getUrl());
			// or getLink() ?
		}
		return json.put("published", toJson(feed.getPublishedDate()));

	}

    public static List<JsonObject> toJson(List<SyndEntry> entries, Date maxDate) {
	    return entries
                .stream()
                .flatMap(entry -> {
                    var published = entry.getPublishedDate();
                    if (published == null) {
                        published = entry.getUpdatedDate();
                    }
                    if (maxDate == null || (published != null && published.compareTo(maxDate) > 0)) {
                        LOG.info("maxDate = {}", maxDate);
                        return Stream.of(toJson(entry));
                    } else {
                        return Stream.empty();
                    }
        		})
                .collect(Collectors.toList());
	}

	static JsonObject toJson(SyndEntry entry) {
		var json = new JsonObject().put("title", entry.getTitle());
		var published = entry.getPublishedDate();
		if (published == null) {
			LOG.warn("!!!!!! The RSS has no published date : this will lead to duplicates entry");
			published = new Date(); // Can't do anything better here, but next time the RSS is fetched, new entries will be added
		}
		json.put("published", toJson(published))
                .put("score", (double)published.getTime())
                .put("link", entry.getLink());
		var description = entry.getDescription();
		if (description != null) {
			json.put("description", description.getValue());
		}
		return json;
	}

    public static Date getDate(String isoDate) throws ParseException {
		return isoDateFormat.parse(isoDate);
	}

    public static String toJson(Date date) {
		if (date == null) {
			return null;
		}
		return isoDateFormat.format(date);
	}
}
