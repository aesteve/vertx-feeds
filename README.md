## Feed aggregator using Vert.x 3 and Apex.

This project is designed to show what a real-life application can look like with Vert.x.

Every user can subscribe to some feeds (RSS, Twitter feed, ...). 

Users and subscriptions by user are store in MongoDB.

## First approach (without Redis) :

* when a user connects, for each of it's subscriptions, a request is made and data is aggregated and pushed into `/api/myfeed`. Once the API response is received, the user opens a websocket for live updates
* as long as he's connected, a worker verticle polls periodically for each of his subscriptions. If a new item is detected, it's pushed to a websocket

Showcase for : 
* MongoDB
* Apex authentication
* `createClient.getNow()`
* `setPeriodic`
* REST API
* SockJS
* front-end stuff (AngularJS ?)

## Second approach (using Redis) :
* For every users subscriptions, the worker verticle polls periodically and fulfill a Redis database with the updates fetched
* The API simply asks Redis with the right keys
* When a socket is connected, Redis is polled periodically to retrieve live updates

Showcase for : 
* Worker verticles
* Redis
* Intensive polling

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
 | - Users                    | | - Items per subscription  |                                   
 | - Their subscriptions      | |                           |                                   
 |                            | |                           |                                   
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
