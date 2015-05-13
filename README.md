## Feed aggregator using Vert.x 3 and Apex.

This project is designed to show what a real-life application can look like with Vert.x.

Every user can subscribe to some feeds (RSS, Twitter feed, ...). 

Users and subscriptions by user are store in MongoDB.

## How it works :

Two models : 
* User
* Feed (for now there is only one type of Feed : RSSFeed, could evolve in the future)

* Users register using a simple login / password
* Once they're registered, they can subscribe to feeds by prividing the feed's URL (and a color)
* Subscriptions are stored in user infos (`user.subscriptions`) but also in a plain mongo collection listing feeds and the number of people who subscribed to this feed
* Periodically, a verticle lists the entries in the feeds collection and for each of the feeds that have a subscriber count > 0, will read the RSS feed and fetch new entries
* New feed entries are store into JSON objects in a Redis set. The key is the feed's URL hash, the value is the JSON equivalent of the RSS entry and the score is the timestamp of the entry's publication date
* When a user asks for his news feed entries are aggregated from Redis for each of his subscriptions


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
                 |Worker verticle (= broker)|                                                   
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
 | - Users                    | | - Items per feed  |                                   
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
