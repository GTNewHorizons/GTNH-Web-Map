# About GTNH-Web-Map
GTNH-Web-Map is a fork of Dynmap (a server side addon which adds a browser accessible shared map for your players) with the explicit goal of improving support for 1.7.10 mods and the contents of the GTNH mod pack. GWM is only available as a Forge mod at this time.

# How do I install it?
Latest releases are available on the GWM GitHub page: https://github.com/GTNewHorizons/GTNH-Web-Map/releases

* **NOTE!** This is an actively developed mod currently in alpha state. You will experience bugs (please report them!) and possibly crashes so make sure you have backups etc!
* If you've used Dynmap on this server before, make sure to delete the DynmapForge-xxx.jar and **remove all renderdata/custom files** as they will override any improvements from this fork.
* Other than that, basic installation only requires you to add the gtnh-web-map-xxx.jar to your server mods folder and (re)start the server.
* Once the server is up and running, it will listen on port 8123 by default (so http://<server-address>:8123/). You may need to open ports in your firewall and/or router.

# Support and reporting issues
* All issues should be reported to the GWM issue tracker in GitHub: https://github.com/GTNewHorizons/GTNH-Web-Map/issues
* Discussions, development and support are handled on the GTNH Discord server: https://discord.gg/gtnh (see channel #special-projects-dev)

# Some useful commands to get started
* `/dynmap fullrender <dim>` can be used to force a re-render of the maps of a dimension. Otherwise it'll only render new or modified chunks.
    * `<dim>` can be `world` or whatever you call it in your server.properties for overworld
    * `DIM-1` for Nether, `DIM1` for the End, `DIM28` for the Moon etc. To find the number, consider using `/cofh tps`.
    * `DIM180` is typically the name of the first personal dimension.
    * `/dynmap fullrender <dim>:<mapname>` can be used to only render a particular map instead of all maps of that dimension. By default you probably have `flat`, `surface` and `cave`.
* To add, disable or reorder maps, you must first pause rendering with `/dynmap pause all`. **Remember to resume with `/dynmap pause none` when you are done!**
    * `/dmap worldset <dim> enabled:false` hides all maps from <dim>
    * `/dmap worldset <dim> order:1` moves <dim> to the top of the menu
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
    * This is highly recommended to make your base(s) pretty without taking forever to render the whole map, and without sacrifising too much disk space.

# Other tips
* You can change the port the webserver listens on in dynmap/configuration.txt. Look for `webserver-port: 8123` on approximately line 311. The commented out port in the beginning of the file is something completely different, don't touch unless you know what you're doing.
* You may wish to take a look at some of the performance settings in dynmap/configuration.txt. The defaults were made to have minimal impact on nearly 10 year old hardware and are extremely conservative. You can tweak these pretty aggressively without impacting TPS assuming you have a multicore server.
