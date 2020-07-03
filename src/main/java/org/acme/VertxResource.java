package org.acme;

import io.quarkus.runtime.StartupEvent;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.redis.client.Command;
import io.vertx.reactivex.redis.client.Redis;
import io.vertx.reactivex.redis.client.RedisConnection;
import io.vertx.reactivex.redis.client.Request;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
@Path("/vertx")
public class VertxResource {

    private static final Logger LOGGER = Logger.getLogger(VertxResource.class.getName());

    @Inject
    Vertx vertx;

    private final AtomicReference<RedisConnection> client = new AtomicReference<>();

    void start(@Observes StartupEvent startupEvent) {
        Redis.createClient(vertx, new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString("redis://localhost:6379")
                .setUseSlave(RedisSlaves.ALWAYS)

        ).connect(onConnect -> {
            if (onConnect.succeeded()) {
                RedisConnection c = onConnect.result();
                this.client.set(c);
                LOGGER.info("Connected to REDIS");
                c.send(Request.cmd(Command.CLUSTER).arg("NODES"), r -> {
                    if (r.succeeded()) {
                        LOGGER.info(r.result().toString());
                    }
                });
            } else {
                LOGGER.log(Level.SEVERE, onConnect.cause().getMessage(), onConnect.cause());
            }
        });
    }

    private RedisConnection getClient() {
        RedisConnection c = this.client.getPlain();
        if (c != null) {
            return c;
        }
        c = this.client.get();
        if (c == null) {
            throw new ServiceUnavailableException();
        }
        return c;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String get(@QueryParam("key") String key) {
        final AtomicReference<String> result = new AtomicReference<>();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        getClient().send(Request.cmd(Command.GET).arg(key), resp -> {
            if (resp.succeeded()) {
                result.set(resp.result().toString());
            } else {
                LOGGER.log(Level.WARNING, "fail on get " + key, resp.cause());
            }
            countDownLatch.countDown();
        });

        try {
            if (countDownLatch.await(200, TimeUnit.MILLISECONDS)) {
                String r = result.get();
                if (r != null) {
                    return r;
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        throw new ServiceUnavailableException();

    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public void set(@QueryParam("key") String key, @QueryParam("value") String value) {
        getClient().send(Request.cmd(Command.SET).arg(key).arg(value), resp -> {
            if (resp.succeeded()) {
                LOGGER.info("success on set " + key + " " + value);
                LOGGER.info(resp.result().toString());
            } else {
                LOGGER.log(Level.WARNING, "fail on set " + key + " " + value, resp.cause());
            }
        });
    }
}