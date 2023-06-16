package com.lillop.pastum;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.pgclient.PgPool;
import io.vertx.pgclient.data.Inet;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

/**
 * TCPPasteReceiverVerticle starts a socket listener that stores pastum from any connection
 */
public class TCPPasteReceiverVerticle extends MainVerticle {
  private static final Logger logger = LoggerFactory.getLogger(TCPPasteReceiverVerticle.class);

  public TCPPasteReceiverVerticle(PgPool pool) {
    super(pool);
  }

  @Override
  public void start(Promise<Void> startPromise) {
    JsonObject cfg = vertx.getOrCreateContext().config();

    // Setup TCP options
    NetServerOptions netServerOptions = new NetServerOptions()
      .setPort(cfg.getInteger("tcpPort"))
      .setHost(cfg.getString("listenAddress"))
      .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)
      .setReadIdleTimeout(cfg.getInteger("maxiTimeout"))
      .setLogActivity(cfg.getBoolean("logActivity"));

    // Create TCP server
    vertx.createNetServer(netServerOptions).connectHandler(sock -> {
        Buffer b = Buffer.buffer();
        ArrayDeque<Long> timers = new ArrayDeque<>(1);
        Inet remoteAddr;

        // Capture remote IP address
        try {
          remoteAddr = new Inet().setAddress(InetAddress.getByName(sock.remoteAddress().hostAddress()));
        } catch (UnknownHostException e) {
          // We should never get here
          throw new RuntimeException(e);
        }

        // Create socket data handler
        sock.handler(buf -> {
          // Cancel last packet timer on receipt of data
          Long timer = timers.pollLast();
          if (timer != null) {
            getVertx().cancelTimer(timer);
          }

          // Capture new data
          b.appendBuffer(buf);

          // Restart short timer on receipt of data
          if (cfg.getBoolean("useMiniTimeout"))
            timers.push(getVertx().setTimer(cfg.getInteger("miniTimeout"),
              getPastumSaveHandler(sock, b, remoteAddr, false)));//input timer handler
        });//socket handler

        // Setup long timeout handler
        if (!cfg.getBoolean("useMiniTimeout")) {
          sock.closeHandler(v -> {
            getPastumSaveHandler(sock, b, remoteAddr, true).handle(0L);//connection closed handler
          });//close handler
        }
      })
      .exceptionHandler(t -> {
        if (logger.isDebugEnabled())
          logger.debug(t.getMessage());
      })
      .listen()
      .compose(ns -> {
        logger.info("TCP server started on port {}:{}", cfg.getString("listenAddress"), cfg.getInteger("tcpPort"));
        startPromise.complete();
        return Future.succeededFuture();
      })
      .onFailure(startPromise::fail);
  }

  private Handler<Long> getPastumSaveHandler(NetSocket sock, Buffer b, Inet remoteAddr, Boolean timeoutMode) {
    return l -> {
      if (logger.isDebugEnabled()) {
        logger.debug(String.format("INSERT %s (%d) bytes)", vertx.getOrCreateContext().config().getString("pasteColumn"), b.length()));
      }

      Future<RowSet<Row>> f = pool.preparedQuery(vertx.getOrCreateContext().config().getString("saveQuery"))
        .execute(Tuple.of(remoteAddr, b.toString()));
      if (timeoutMode) {
        // Handle connection close: store b
        f.onComplete(asyncRows -> {
          if (asyncRows.succeeded()) {
            String webkey = asyncRows.result().iterator().next().getString("webkey");
            if (logger.isDebugEnabled())
              logger.debug(String.format("Someone from %s posted %s but ran off without a receipt", sock.remoteAddress(), getURL(webkey)));
          } else {
            if (logger.isDebugEnabled())
              logger.debug(asyncRows.cause().getMessage());
          }
        });
      } else {
        // Handle short timeout: save buffer, print URL to client
        f.onComplete(asyncRows -> {
          if (asyncRows.succeeded()) {
            String webkey = asyncRows.result().iterator().next().getString("webkey");
            sock.write(getURL(webkey));
            sock.write("\n");
          } else {
            sock.write("500\n");
            if (logger.isDebugEnabled())
              logger.debug(asyncRows.cause().getMessage());
          }
          sock.close();
        });
      }
    };
  }
}
