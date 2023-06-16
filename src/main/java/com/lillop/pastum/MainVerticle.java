package com.lillop.pastum;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.pgclient.SslMode;
import io.vertx.pgclient.impl.PgPoolOptions;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.processing.Generated;

/**
 * @author Matt Hudson
 * @version 1.0
 * @since May 17, 2023
 * <p>
 * Simple superclass and deployer for pastum Verticles
 */
public class MainVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
  protected PgPool pool;

  public MainVerticle() {
  }

  public MainVerticle(PgPool pool) {
    this.pool = pool;
  }

  @Override
  public void start(Promise<Void> startPromise) {

    ConfigRetriever retriever = ConfigRetriever.create(vertx);

    // Load config from disk
    retriever.getConfig()
      .compose(fullCfg -> {
        // Merge config into defaults
        JsonObject cfg = getCfg(fullCfg);

        DeploymentOptions options = new DeploymentOptions()
          .setConfig(cfg);

        // DB setup

        // Configure connection
        PgConnectOptions connectOptions = PgConnectOptions.fromUri(cfg.getString("pgUri"))
          .setSslMode(SslMode.REQUIRE)
          .setMetricsName(cfg.getString("pgMetricsName"))
          .setTrustAll(true);// This is required to disable cert chain validation (we don't have the CA root)
        // Configure pool
        PoolOptions poolOptions = new PgPoolOptions(new PoolOptions())
          .setMaxSize(cfg.getInteger("maxPoolSize"))
          .setName(cfg.getString("appName"));
        // Create pool
        pool = PgPool.pool(vertx, connectOptions, poolOptions);

        // Recycling this app pattern? Start chopping here

        // Start TCP server
        return vertx.deployVerticle(new TCPPasteReceiverVerticle(pool), options)
          .compose(ns -> {
            // Start web server
            return vertx.deployVerticle(new HTTPPasteServerVerticle(pool), options);
          })
          .onComplete(ns -> {
            startPromise.complete();
          });
      }).onFailure(t -> {
        logger.error(t.getMessage());
        startPromise.fail(t);
      });
  }

  private static JsonObject getCfg(JsonObject fullCfg) {
    return fullCfg.getJsonObject("default").mergeIn(
      fullCfg.getJsonObject(
        fullCfg.getString(
          fullCfg.getString("appEnv"))));
  }

  protected String getURL(String webkey) {
    return vertx.getOrCreateContext().config().getString("webUrlPrefix") + "/" + webkey;
  }
}
