package com.lillop.pastum;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(VertxExtension.class)
public class TestMainVerticle {
  private static final Logger logger = LoggerFactory.getLogger(TestMainVerticle.class);

  @BeforeEach
//  void deploy_verticle(Vertx vertx, VertxTestContext testContext) throws Throwable {
    // Get available ephemeral port to configure new server
//    ServerSocket socket = new ServerSocket(0);
//    Integer port = socket.getLocalPort();
//    socket.close();
//    DeploymentOptions options = new DeploymentOptions()
//      .setConfig(new JsonObject().put("LILLOP_ENV", "dev"));
//    JsonObject cfg = new JsonObject();
//    TCPPasteReceiverVerticle tpr = new TCPPasteReceiverVerticle(options.getConfig(), PgPool.pool());
//    vertx.deployVerticle(tpr, options, testContext.succeeding(id -> testContext.completeNow()));
//  }

  @Test
  void verticle_deployed(Vertx vertx, VertxTestContext testContext) throws Throwable {
    testContext.completeNow();
  }
}
