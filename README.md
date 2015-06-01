## Feed aggregator using Vert.x 3 and Apex.

This project is designed to show what a real-life application can look like with Vert.x.

It's a simple feed aggregator.

Every user can subscribe to some feeds (RSS, ...), then Vert.x will periodically read these feeds and store news (and notify connected clients). 

Users and their subscriptions user are store in MongoDB. Feed entries (RSS news article for example) are stored in Redis.

## How it works :

Simple use-case :

* Users register using a simple login / password. An User is a simple document in MongoDB database. (there's no need for an email address in this demo)
* Once they're registered, they can subscribe to feeds by prividing the feed's URL (and a color, for UI display). A Feed is another document in MongoDB.
* Subscriptions are stored in user infos (`user.subscriptions`) but also in a plain mongo collection listing feeds and the number of people who subscribed to this feed.
* Periodically, a verticle lists the entries in the feeds collection and for each of the feeds that have a subscriber count > 0, will read the RSS feed and fetch new entries
* New feed entries are store into JSON objects in a Redis set. The key is the feed's URL hash, the value is the JSON equivalent of the RSS entry and the score is the timestamp of the entry's publication date
* When a user asks for his news feed entries are aggregated from Redis for each of his subscriptions
* When the new feed entries are stored into Redis, they're also pushed as a List of entries on Vertx's event bus. This allows end users who are connected from client side to receive real-time updates by subscribing directly to event bus notifications.


## Feed API :

* `GET /api/feeds` : lists user's subscriptions
* `POST /api/feeds` : subscribe to a new feed
* `GET /api/feeds/:feedId` : fetches information for a specific feed
* `PUT /api/feeds/:feedId` : update infos for a specific feed (color especially)
* `DELETE /api/feeds/:feedId` : unsubscribe to a feed
* `GET /api/feeds/:feedId/entries` : fetches the feed entries

## Schema : 

```
                 +--------------------------+                                                   
                 |                          |                                                   
                 |  Verticle (= broker)     |                                                   
                 |                          |                                                   
                 |                          |                                                   
                 |                          |                                                   
                 |fetch items / subscription|                                                   
                 | +-------------------+    |                                                   
                 | |                   |    |                                                   
                 +--------------------------+                                                   
list subscriptions |                   | populate items in Redis                                
 +-----------------+----------+ +------+--------------------+                                   
 |                            | |                           |                                   
 |          MongoDB           | |           Redis           |                                   
 |                            | |                           |                                   
 |                            | |                           |                                   
 | Stores :                   | | Stores :                  |                                   
 | - Users                    | | - Items per feed          |                                   
 | - Their subscriptions      | |                           |                                   
 | - A collection of feeds    | |                           |                                   
 |                            | |                           |                                   
 +-----------------^----------+ +----^----------------------+                                   
 login / create user / subscribe     | poll periodically for each connected user -> push updates
                +---------------------------------+                                             
                |  Web verticle (API + Sockets)   |                                             
                |                                 |                                             
                | - serves pages                  |                                             
                | | login users                   |                                             
                | | serves items through API      |                                             
                | - real-time updates (sockjs)    |                                             
                |                                 |                                             
                |                                 |                                             
                |                                 |                                             
                +---------------------------------+                                             

```


## What it illustrates

The goal of this project is to be informative for every user who wants to see what Vert.x is able to do.

You'll find : 

* how to build dynamic server-side rendered pages thanks to `Handlebars` and it's implementation in Vert.x (`HandlebarsTemplateEngine`)
* how to build a JSON REST API using `vertx-web`'s Router
* how to read data from a JSON REST API using AngularJS on client-side
* how to read / write from / to MongoDB in an asynchronous way thanks to `vertx-mongo-client`
* how to use `vertx-redis-client` to store items in a redis set asynchronously
* a very simple implementation of the publish / subscribe pattern on Vert.x's event bus, one of its key features.
* how to expose Vert.x's event bus messages on client-side thanks to Vert.x `EventBusBridge` using `sockjs`

For now, the project is here for you to read its code, not **at all** for production use.

If you want to run it on your local machine, you need to have both a Redis store and a Mongo database running on default host / port. I'm currently looking at the embedded versions of these two projects to make stuff even more easy to run locally. 