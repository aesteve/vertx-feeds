## Feed aggregator using Vert.x 3 and Vertx-web.

This project is designed to show what a real-life application can look like with Vert.x.

It's a simple feed agregator.

Every user can subscribe to some feeds (RSS, ...), then Vert.x will periodically read these feeds and store news (and notify connected clients). 

Users and their subscriptions user are store in MongoDB. Feed entries (RSS news article for example) are stored in Redis.

## How it works :

Simple use-case :

* Users register using a simple login / password. An User is a simple document in MongoDB database. (there's no need for an email address in this demo)
* Once they're registered, they can subscribe to feeds by providing the feed's URL (and a color, for UI display). A Feed is another document in MongoDB.
* Subscriptions are stored in user infos (`user.subscriptions`) but also in a plain mongo collection listing feeds and the number of people who subscribed to this feed.
* Periodically, a verticle lists the entries in the feeds collection and for each of the feeds that have a subscriber count > 0, will read the RSS feed and fetch new entries
* New feed entries are store into JSON objects in a Redis set. The key is the feed's URL hash, the value is the JSON equivalent of the RSS entry and the score is the timestamp of the entry's publication date
* When a user asks for his news feed entries, they are aggregated from Redis for each of his subscriptions
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
* examples of code involving Java 8 lambdas to deal with an asynchronous API
* some (hopefully) useful classes to deal with asynchronous stuff, like the `MultipleFutures` class
* how to package a whole Vert.x application (fatJar) using Gradle, see `build.gradle`

For now, the project is here for you to read its code, not **at all** for production use.

If you want to run it on your local machine, from the root of the project, type in `./gradlew run` from the command-line. If you want to run it from your IDE, you have to create a "Run configuration" involving `io.vertx.core.Starter` as main-class with the following arguments : `run io.vertx.examples.feeds.verticles.MainVerticle`. This way you can easily debug the source code.

Then you can point your browser at : http://localhost:9000/index.hbs and you should see the home page.

Redis and Mongo are embedded in the application so that you don't need to install them locally.


## Contributing

The project is completely free. You can fork the project, use it for your own purpose if you want to.

Feel free to open issues, or ask questions on [Vert.x Google Group](https://groups.google.com/forum/#!topic/vertx/2WDDAJ6KoAw) if you have any.

Feel free to highlight source code that you don't understand or would have written in a different way, I'd be happy to discuss and even more happy if you're right and point me at a more elegant way to write the application's source code.

Obviously, you can submit a Pull Request if you're pretty sure something would be better if written differently, or that a key feature of Vert.x is missing and you'd like it to be illustrated in this example (but keep in mind this project should be kept simple, it's already a bit complicated to dive in).

Thanks anyway for any kind of contribution you could submit, it's greatly appreciated. :) 
