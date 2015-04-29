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
