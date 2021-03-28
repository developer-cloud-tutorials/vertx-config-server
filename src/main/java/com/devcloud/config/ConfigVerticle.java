package com.devcloud.config;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class ConfigVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    initEnvConfig().getConfig()
      .compose(entries -> initGitConfig(entries).getConfig())
      .onSuccess(entries -> log.info("Configuration loaded"))
      .onFailure(throwable -> log.error("Config error", throwable));

    initRouting(startPromise);
  }

  private void initRouting(Promise<Void> startPromise) {
    Router router = Router.router(vertx);

    router.get("/configs/:application/:label").handler(routingContext -> {
      String application = routingContext.pathParam("application");
      String label = routingContext.pathParam("label");
      ConfigStoreOptions git = new ConfigStoreOptions()
        .setType("directory")
        .setConfig(new JsonObject()
          .put("path", "config-git")
          .put("filesets",
            new JsonArray()
              .add(new JsonObject()
                .put("pattern", "*global.yaml")
                .put("format", "yaml"))
              .add(new JsonObject()
                .put("pattern", String.format("*global-%s.yaml", label))
                .put("format", "yaml"))
              .add(new JsonObject()
                .put("pattern", String.format("*%s-%s.yaml", application, label))
                .put("format", "yaml"))
              .add(new JsonObject()
                .put("pattern", String.format("*%s.yaml", application))
                .put("format", "yaml"))
          ));

      ConfigRetriever.create(vertx, new ConfigRetrieverOptions().addStore(git).addStore(git))
        .getConfig()
        .onSuccess(entries -> routingContext.end(Json.encode(entries)))
        .onFailure(throwable -> {
          log.error("Error getting config", throwable);
          routingContext.response().setStatusCode(500).end();
        });
    });

    router.errorHandler(500, routingContext -> log.error("Request error", routingContext.failure()));

    vertx.createHttpServer().requestHandler(router)
      .listen(8080, http -> {
        if (http.succeeded()) {
          startPromise.complete();
          log.info("HTTP server started on port 8888");
        } else {
          startPromise.fail(http.cause());
        }
      });
  }

  private ConfigRetriever initEnvConfig() {
    ConfigStoreOptions env = new ConfigStoreOptions().setType("env");

    return ConfigRetriever.create(vertx, new ConfigRetrieverOptions().addStore(env));
  }

  private ConfigRetriever initGitConfig(JsonObject envConfig) {

    Optional<String> url = Optional.ofNullable(envConfig.getString("GIT_URL"));
    Optional<String> branch = Optional.ofNullable(envConfig.getString("GIT_BRANCH"));
    Optional<String> user = Optional.ofNullable(envConfig.getString("GIT_USER"));
    Optional<String> password = Optional.ofNullable(envConfig.getString("GIT_PASSWORD"));

    JsonObject config = new JsonObject()
      .put("url", url.orElse("https://github.com/developer-cloud-tutorials/vertx-config.git"))
      .put("branch", branch.orElse("main"))
      .put("path", "config-git")
      .put("filesets",
        new JsonArray().add(new JsonObject().put("pattern", "*.yaml").put("format", "yaml")));

    user.ifPresent(u -> config.put("user", u));
    password.ifPresent(p -> config.put("password", p));

    ConfigStoreOptions git = new ConfigStoreOptions()
      .setType("git")
      .setConfig(config);

    return ConfigRetriever.create(vertx, new ConfigRetrieverOptions().addStore(git));
  }
}
