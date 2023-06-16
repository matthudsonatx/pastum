package com.lillop.pastum;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.micrometer.PrometheusScrapingHandler;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTPPasteServerVerticle starts a web server that returns pastum for a requested webkey
 */
public class HTTPPasteServerVerticle extends MainVerticle {
  private static final Logger logger = LoggerFactory.getLogger(HTTPPasteServerVerticle.class);

  public HTTPPasteServerVerticle(PgPool pool) {
    super(pool);
  }

  // Test a database round-trip
  public void databaseStatus(Promise<Status> p) {
    pool.query(vertx.getOrCreateContext().config().getString("dbHealthQuery")).execute().compose(rowSet -> {
      p.complete(rowSet.size() > 0 ? Status.OK() : Status.KO());
      return Future.succeededFuture();
    }).onFailure(p::fail);
  }

  public void databaseCount(Promise<Status> p) {
    pool.query(vertx.getOrCreateContext().config().getString("dbCountQuery")).execute().compose(rowSet -> {
      p.complete(rowSet.size() > 0 ? Status.OK().setData(rowSet.iterator().next().toJson()) : Status.KO());
      return Future.succeededFuture();
    }).onFailure(p::fail);
  }

  @Override
  public void start(Promise<Void> startPromise) {
    JsonObject cfg = vertx.getOrCreateContext().config();

    // Setup pastum retrieval handler
    Router router = Router.router(vertx);
    // Setup health checks handler
    HealthChecks hc = HealthChecks.create(vertx)
      .register("database", this::databaseStatus)
      .register("volume", this::databaseCount);
    router.route("/health*").handler(HealthCheckHandler.createWithHealthChecks(hc));
    router.route("/metrics").handler(PrometheusScrapingHandler.create());

    // Install retrieval handler last
    router.route(cfg.getString("webRoute")).handler(getPastumRetrievalHandler());

    // Start web server
    vertx.createHttpServer()
      .requestHandler(router)
      .listen(cfg.getInteger("httpPort"), cfg.getString("listenAddress"))
      .compose(http -> {
        logger.info(String.format("HTTP server started on port %s:%s", cfg.getString("listenAddress"), cfg.getInteger("httpPort")));
        startPromise.complete();
        return Future.succeededFuture();
      }).onFailure(startPromise::fail);
  }

  private Handler<RoutingContext> getPastumRetrievalHandler() {
    return req -> {
      String webkey = req.pathParam("webkey");
      pool.preparedQuery(vertx.getOrCreateContext().config().getString("readQuery")).execute(Tuple.of(webkey))
        .compose(rowSet -> {
          if (rowSet.size() > 0) {
            req.response().putHeader("content-type", "text/plain");
            req.response().end(rowSet.iterator().next().getString(vertx.getOrCreateContext().config().getString("pasteColumn")));
          } else {
            req.response().putHeader("content-type", "text/plain").end("webkey not found");
          }
          return Future.succeededFuture();
        }).onFailure(t -> {
          if (logger.isDebugEnabled())
            logger.debug(t.toString());
        });
    };
  }
}
