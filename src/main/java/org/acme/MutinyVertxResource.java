package org.acme;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.redis.client.Command;
import io.vertx.mutiny.redis.client.Redis;
import io.vertx.mutiny.redis.client.Request;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.RedisSlaves;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.core.MediaType;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
@Path("/mutiny-vertx")
public class MutinyVertxResource {

    @Inject
    Vertx vertx;

    private final AtomicReference<Redis> client = new AtomicReference<>();

    void start(@Observes StartupEvent startupEvent) {
        this.client.set(Redis
                .createClient(
                        vertx,
                        new RedisOptions()
                                .setType(RedisClientType.CLUSTER)
                                .addConnectionString("redis://localhost:6379")
                                .setUseSlave(RedisSlaves.ALWAYS)

                ));

    }

    private Redis getClient() {
        Redis c = this.client.getPlain();
        if (c != null) {
            return c;
        }
        return this.client.get();
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> get(@QueryParam("key") String key) {
        return this.getClient()
                .send(Request.cmd(Command.GET).arg(key))
                .onFailure()
                .apply(r -> new ServiceUnavailableException())
                .onItem()
                .produceUni(r -> Uni.createFrom().item(r.toString()));
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<Void> set(@QueryParam("key") String key, @QueryParam("value") String value) {
        return this.getClient()
                .send(Request.cmd(Command.SET).arg(key).arg(value))
                .onFailure()
                .apply(r -> new ServiceUnavailableException())
                .onItem()
                .produceUni(r -> Uni.createFrom().nullItem());
    }
}