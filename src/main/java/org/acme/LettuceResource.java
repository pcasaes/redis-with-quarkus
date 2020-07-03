package org.acme;

import io.lettuce.core.ReadFrom;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.codec.StringCodec;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.converters.uni.UniReactorConverters;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.core.MediaType;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
@Path("/lettuce")
public class LettuceResource {

    private final AtomicReference<StatefulRedisClusterConnection<String, String>> client = new AtomicReference<>();

    void start(@Observes StartupEvent event) {
        this.client.set(createConnection());
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> set(@QueryParam("key") String key) {
        return Uni.createFrom().converter(UniReactorConverters.fromMono(), this.getClient().reactive().get(key))
                .onFailure()
                .apply(r -> new ServiceUnavailableException());
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<Void> set(@QueryParam("key") String key, @QueryParam("value") String value) {
        return Uni.createFrom().converter(UniReactorConverters.fromMono(), this.getClient().reactive().set(key, value))
                .onFailure()
                .apply(r -> new ServiceUnavailableException())
                .onItem()
                .produceUni(r -> Uni.createFrom().nullItem());
    }

    private StatefulRedisClusterConnection<String, String> getClient() {
        StatefulRedisClusterConnection<String, String> c = this.client.getPlain();
        if (c != null) {
            return c;
        }
        return this.client.get();
    }

    private StatefulRedisClusterConnection<String, String> createConnection() {
        TimeoutOptions timeoutOptions = TimeoutOptions.builder()
                .fixedTimeout(Duration.ofMillis(200))
                .build();

        SocketOptions soOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofMillis(1000))
                .build();

        ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(true)
                .dynamicRefreshSources(false)
                .build();

        ClusterClientOptions clientOptions = ClusterClientOptions.builder()
                .topologyRefreshOptions(topologyRefreshOptions)
                .timeoutOptions(timeoutOptions)
                .socketOptions(soOptions)
                .build();

        RedisClusterClient localClient = RedisClusterClient.create(RedisURI.create("localhost", 6379));
        localClient.setOptions(clientOptions);

        StatefulRedisClusterConnection<String, String> localConnection = localClient.connect(new StringCodec());
        localConnection.setReadFrom(ReadFrom.REPLICA_PREFERRED);
        return localConnection;
    }
}
