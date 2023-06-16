package com.lillop;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Custom launcher is required to configure some things before deployment
public class Launcher extends io.vertx.core.Launcher {
  Logger logger = LoggerFactory.getLogger(Launcher.class);

  @Override
  public void beforeStartingVertx(VertxOptions vertxOptions) {

    // Enable Micrometer based metrics with Prometheus
    vertxOptions.setMetricsOptions(
      new MicrometerMetricsOptions()
        .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true))
        .setEnabled(true));
  }

  @Override
  public void afterStartingVertx(Vertx vertx){

  }

  @Override
  public void afterStoppingVertx() {

  }

  @Override
  public void beforeDeployingVerticle(DeploymentOptions deploymentOptions) {

  }

  @Override
  public void handleDeployFailed(Vertx vertx, String mainVerticle, DeploymentOptions deploymentOptions, Throwable cause) {
    logger.debug(deploymentOptions.toJson().encodePrettily());
    logger.error(String.format("Failed to deploy %s: %s", mainVerticle, cause.getMessage()));
  }

  @Override
  public void afterConfigParsed(JsonObject config) {

  }

  public static void main(String[] args) {
    new Launcher().dispatch(args);
  }
}
