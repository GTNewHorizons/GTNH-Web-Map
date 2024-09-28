# About GTNH-Web-Map
GTNH-Web-Map is a fork of Dynmap (a server side addon which adds a browser accessible shared map for your players) with the explicit goal of improving support for 1.7.10 mods and the contents of the GTNH mod pack. GWM is only available as a Forge mod at this time.

# How do I install it?
Latest releases are available on the GWM GitHub page: https://github.com/GTNewHorizons/GTNH-Web-Map/releases

* If you've used Dynmap on this server before, make sure to delete the DynmapForge-xxx.jar and **remove all renderdata/custom files** as they will override any improvements from this fork.
* Other than that, basic installation only requires you to add the gtnh-web-map-xxx.jar to your server mods folder and (re)start the server.
* Once the server is up and running, it will listen on port 8123 by default (so http://<server-address>:8123/). You may need to open ports in your firewall and/or router.
* You may wish to tweak your `dynmap/configuration.txt` as well as your `config/GTNH-Web-Map.cfg` files. The former contains most performance and web page settings, the latter mostly contains various settings related to cross mod interactions and similar.

# Support and reporting issues
* All issues should be reported to the GWM issue tracker in GitHub: https://github.com/GTNewHorizons/GTNH-Web-Map/issues
* Discussions, development and support are handled on the GTNH Discord server: https://discord.gg/gtnh (see channel #special-projects-dev)

# Some useful commands to get started
* `/dynmap fullrender <dim>` can be used to force a re-render of the maps of a dimension. Otherwise, it'll only render new or modified chunks.
    * `<dim>` can be `world` or whatever you call it in your server.properties for overworld
    * `DIM-1` for Nether, `DIM1` for the End, `DIM28` for the Moon etc. To find the number, consider using `/cofh tps`.
    * `DIM180` is typically the name of the first personal dimension.
    * `/dynmap fullrender <dim>:<mapname>` can be used to only render a particular map instead of all maps of that dimension. By default, you probably have `flat`, `surface` and `cave`.
* To add, disable or reorder maps, you must first pause rendering with `/dynmap pause all`. **Remember to resume with `/dynmap pause none` when you are done!**
    * `/dmap exploremode` hides all dimensions except overworld, but will re-enable them on visit, and automatically order them by the order they are visited.
    * `/dmap setorder <dim0> <dim1> ...` move listed dimensions to the top of the list, in listed order.
    * `/dmap enableonly <dim0> <dim1> ...` move listed dimensions to the top of the list, in listed order **and disable all others**.
    * `/dmap disableworlds <dim0> <dim1> ...` disable listed dimensions.
    * `/dmap worldset <dim> enabled:false` hides all maps from <dim>.
    * `/dmap worldset <dim> order:1` moves <dim> to the top of the menu.
    * `/dmap mapadd <dim>:<mapname> title:<"map-title"> perspective:<perspective-id> shader:<shader-id> lighting:<lighting-id>` can be used to add a map. See example below.
    * See here for more complete guide: https://github.com/webbukkit/dynmap/wiki/Configuring-Maps-and-Worlds-using-dmap
* Example - To add the other isometric directions to your overworld:
```
/dynmap pause all
/dmap mapadd world:surfaceNE title:"Surface NE" perspective:iso_NE_30_hires shader:stdtexture lighting:shadows mapzoomin:1
/dmap mapadd world:surfaceNW title:"Surface NW" perspective:iso_NW_30_hires shader:stdtexture lighting:shadows mapzoomin:1
/dmap mapadd world:surfaceSW title:"Surface SW" perspective:iso_SW_30_hires shader:stdtexture lighting:shadows mapzoomin:1
/dynmap pause none
```
* **NOTE!** _hires_ maps use quite a bit of disk space. You may wish to use _lores_ instead and boost rendering in your base(s).
    * To boost (improve resolution of) certain areas of your map, see this guide: https://github.com/webbukkit/dynmap/wiki/Boosting-areas-on-Dynmap
    * This is highly recommended to make your base(s) pretty without taking forever to render the whole map, and without sacrificing too much disk space.

# Performance settings
You may wish to take a look at some of the performance settings in dynmap/configuration.txt. The defaults were made to have minimal impact on nearly 10 years old hardware and are extremely conservative. You can tweak these pretty aggressively without impacting TPS assuming you have a multicore server. It can be especially useful when doing initial fullrender passes or when pre-generating a map. If found to be too performance hungry, they can be tuned back after initial renders. In particular, look at these:
* `renderinterval` For a snappy map, set to maybe 0.2 or less.  
* `renderaccelerateinterval` This is how fast it'll render when the backlog gets too big. Set to maybe 0.05 or even 0.01.
* `tiles-rendered-at-once` Set this to something like 75-85% of your number of cores.
* `parallelrendercnt` Uncomment and set to the same as `tiles-rendered-at-once`.
* `maxchunkspertick` You can probably set this to 500.
* `tileupdatedelay` This controls how quickly a tile will be redrawn after its contents are updated. Setting this to a low value like 5 or even 1 will make the map update a lot faster as you're exploring or building, but it can also cause a lot of unnecessary re-renders of automated farms or areas where machines are turning on and off frequently. 
* `zoomoutperiod` This will affect how quickly the zoomed out versions of tiles are generated/updated, which is especially useful when you're pre-generating a map or similar. I use 1.
* `fullrender-min-tps` Setting this to a lower value like say 5 will let it render even when the server is on its knees, which can be useful when pre-generating maps as you typically don't care. Also, rendering typically doesn't affect TPS particularly much if you have a multicore server as rendering is done in background threads, so lowering this and the related ones probably won't hurt performance measurably.
* `progressloginterval` This is not a performance setting, but increasing this to maybe 1000 or 5000 will greatly reduce chat spam when doing fullrender passes. You can also run `/dynmap quiet` to disable the chat spam completely, but then it won't tell you when it's done or such either.

# Chat settings
By default, a chat system is included which allows users to see chat from the game, and send messages from the map page to the game. 
* `allowwebchat` set to false to disable the ability to send messages from the web page. Note that this is sometimes abused by bots etc to send spam, especially if you run on the default port (8123).
* `allowchat` set to false to disable showing chat from the game on the web page. If the above is true and this is false, web page visitors can chat with each other and send messages to players, but they won't see any replies from players.
* `messagettl` controls how long messages appear in the log. Default is 5 seconds, which might not be enough to even read the messages.
* `scrollback` can be used to instead of showing messages for N seconds, keep N messages until they are replaced.
* To completely disable the chat integration, set `allowwebchat` to false, then comment out these sections:
  ``` 
    - class: org.dynmap.SimpleWebChatComponent
      allowchat: false
      # If true, web UI users can supply name for chat using 'playername' URL parameter.  'trustclientname' must also be set true.
      allowurlname: false
  ```
  ```
     - class: org.dynmap.ClientComponent
      type: chat
      allowurlname: false
    - class: org.dynmap.ClientComponent
      type: chatballoon
      focuschatballoons: false
    - class: org.dynmap.ClientComponent
      type: chatbox
      showplayerfaces: true
      messagettl: 5
      # Optional: set number of lines in scrollable message history: if set, messagettl is not used to age out messages
      #scrollback: 100
      # Optiona; set maximum number of lines visible for chatbox
      #visiblelines: 10
      # Optional: send push button
      sendbutton: false
  ```
# Other tips
* You can change the port the webserver listens on in dynmap/configuration.txt. Look for `webserver-port: 8123`. The commented out `port` setting in the beginning of the file is for using a database engine like MySQL/MariaDB to store map data.
* You can uncomment and set `#webpage-title` to set the web page title (what appears on the browser tab). The default is `"$world - $map - GTNH-Web-Map"`, where `$world` will be replaced with the current dimension name (like Overworld) and `$map` with the map name (like Flat or Surface).
