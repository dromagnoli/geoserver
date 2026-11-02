# GWC Hazelcast tile cache plugin

This plugin adds two [Hazelcast](https://hazelcast.com/) backends to the [in-memory tile cache](../../geowebcache/webadmin/defaults.md#in-memory-tile-cache), so that the cached tiles are shared by every node of a GeoServer cluster. A tile rendered by one node is then served by every other node until it expires, and a truncate on one node removes the tile for all of them.

## Installing the plugin

1.  Download the extension from the [nightly GeoServer community module builds](https://build.geoserver.org/geoserver/main/community-latest/).

    !!! warning
        Make sure to match the version of the extension to the version of the GeoServer instance!

2.  Extract the contents of the archive into the `WEB-INF/lib` directory of the GeoServer installation, on every node.

## Choosing a backend

Once installed, the **Backend** list of the in-memory tile cache, in the [Caching Defaults](../../geowebcache/webadmin/defaults.md) page, offers two more entries:

- **Hazelcast client (shared by all nodes)**: GeoServer connects to a separate Hazelcast cluster, which holds the tiles. GeoServer nodes stay small, and the cluster is sized and restarted on its own.
- **Hazelcast embedded (shared by all nodes)**: each GeoServer node becomes a member of the cluster and holds a share of the tiles in its own memory. There is no separate service to run, at the cost of more memory on each node.

**Hazelcast open source has no authentication nor encryption**. The cluster must only be reachable from the GeoServer nodes, for example on a private network or with Kubernetes network policies, as anyone reaching it can read and change the cached tiles.

Both backends read their Hazelcast configuration file, if any, from the same directory as `geowebcache.xml`: by default the `gwc` directory of the data directory, or the one set by `GEOWEBCACHE_CONFIG_DIR`. Changes to these files apply on restart.

The plugin is independent from the [Hazelcast Clustering Plugin](../hz-cluster/index.md). When both are installed, give the two clusters different names and ports.

## Hazelcast client

- **Cluster name**: the name of the Hazelcast cluster, `dev` by default.
- **Cluster members**: comma separated `host:port` list of cluster members, `localhost:5701` by default. One reachable member is enough, the others are found from it.
- **Map name**: the map holding the tiles, `gwc-tiles` by default. It must be declared on the cluster, see [Declaring the map](#declaring-the-map).

GeoServer starts even when the cluster is down. Tiles are then served without the cache, and GeoServer keeps trying to connect in the background, waiting up to 30 seconds between attempts. A request waits at most 5 seconds for an answer from the cluster.

For settings not available in the page, such as discovery or timeouts, place a `hazelcast-client.xml` file next to `geowebcache.xml`, following the [Hazelcast client configuration](https://docs.hazelcast.com/hazelcast/5.7/clients/java#configuring-java-client). When present, it **replaces** the cluster name, the members and every connection default. It is to be noted that GeoServer always connects in the background and retries forever, whatever the file says, so that an unreachable cluster never stops GeoServer: a finite `cluster-connect-timeout-millis` is ignored, with a warning in the log. The map name is still taken from the page.

## Hazelcast embedded

- **Map name**: the map holding the tiles, `gwc-tiles` by default.

The member is configured by a `hazelcast.xml` file, **mandatory**, next to `geowebcache.xml`, following the [Hazelcast member configuration](https://docs.hazelcast.com/hazelcast/5.7/configuration/configuring-declaratively).

The node joins the cluster when the cache is first used, and the request doing so waits for it. A missing file, or a file not declaring the map, leaves the cache unusable, with the reason in the log, while tiles keep being served without it.

The following example fits a Kubernetes deployment, where the GeoServer pods find each other through a service. The pods need permission to read endpoints and pods in their namespace:

```xml
<hazelcast xmlns="http://www.hazelcast.com/schema/config">
  <cluster-name>gwc</cluster-name>
  <network>
    <join>
      <multicast enabled="false"/>
      <kubernetes enabled="true">
        <namespace>geoserver</namespace>
        <service-name>geoserver-hazelcast</service-name>
      </kubernetes>
    </join>
  </network>
  <map name="gwc-tiles">
    <backup-count>1</backup-count>
    <eviction eviction-policy="LRU" max-size-policy="USED_HEAP_PERCENTAGE" size="25"/>
  </map>
</hazelcast>
```

## Declaring the map

With both backends, the map must be declared in the cluster configuration with an eviction policy and a size bound. **A map without them grows until the members run out of memory**, as Hazelcast keeps every entry by default. The embedded backend refuses to start on an undeclared map. The client backend cannot check it, so review the cluster configuration by hand.

The time to live does not need to be declared, as each tile is stored with its own.

`backup-count` sets how many other members keep a copy of each tile. With `1`, losing a member loses no tiles, at the cost of twice the memory. With `0`, the tiles of a lost member are rendered again.

## Truncation

Truncating by bounding box reads the keys of the affected zoom level from the cluster into the memory of the node doing it, so very large caches need enough memory for that on the GeoServer nodes.
