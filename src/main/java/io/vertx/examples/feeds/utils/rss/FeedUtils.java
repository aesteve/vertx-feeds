package io.vertx.examples.feeds.utils.rss;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndImage;

public class FeedUtils {

    private final static Logger log = LoggerFactory.getLogger(FeedUtils.class);

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
        return json;

    }

    public static List<JsonObject> toJson(List<SyndEntry> entries, Date maxDate) {
        List<JsonObject> result = new ArrayList<JsonObject>(entries.size());
        entries.forEach(entry -> {
            Date published = entry.getPublishedDate();
            if (published == null) {
                published = entry.getUpdatedDate();
            }
            if (maxDate == null || (published != null && published.compareTo(maxDate) > 0)) {
                log.info("maxDate = " + maxDate);
                result.add(toJson(entry));
            }
        });
        return result;
    }

    public static JsonObject toJson(SyndEntry entry) {
        JsonObject json = new JsonObject();
        json.put("title", entry.getTitle());
        Date published = entry.getPublishedDate();
        if (published == null) {
            // TODO : log warning ? use another date ?
            log.warn("!!!!!! The RSS has no published date : this will lead to duplicates entry");
            published = new Date(); // FIXME : absolutely wrong...
        }
        json.put("published", toJson(published));
        json.put("score", Long.valueOf(published.getTime()).doubleValue());
        json.put("link", entry.getLink());
        SyndContent description = entry.getDescription();
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
