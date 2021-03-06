/*
 * #%L
 * netrelay
 * %%
 * Copyright (C) 2015 Braintags GmbH
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */
package de.braintags.netrelay.unit;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.braintags.vertx.jomnigate.testdatastore.TestHelper;
import de.braintags.vertx.util.exception.InitException;
import de.braintags.netrelay.impl.NetRelayExt_FileBasedSettings;
import de.braintags.netrelay.init.Settings;
import de.braintags.netrelay.mapper.SimpleNetRelayMapper;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * 
 * 
 * @author Michael Remme
 * 
 */
@RunWith(VertxUnitRunner.class)
public class TSettings {
  private static final io.vertx.core.logging.Logger LOGGER = io.vertx.core.logging.LoggerFactory
      .getLogger(TSettings.class);

  private static String localSettingsFileNameUserDir = Settings.LOCAL_USER_DIRECTORY + "/"
      + NetRelayExt_FileBasedSettings.class.getName() + ".settings.json";
  private static String localSettingsFileNameTmpDir = System.getProperty("java.io.tmpdir") + "/"
      + NetRelayExt_FileBasedSettings.class.getName() + ".settings.json";
  private static Vertx vertx = Vertx.vertx(TestHelper.getOptions());

  /**
   * creates new Settings and stores them inside the local user directory
   */
  @Test
  public void testInitSettingsNewInUserDir(TestContext context) {
    context.remove(Settings.SETTINGS_LOCATION_PROPERTY);
    context.assertNotNull(vertx);
    FileSystem fs = vertx.fileSystem();
    deleteFileInDir(context, fs, localSettingsFileNameUserDir);

    Async async = context.async();
    vertx.deployVerticle(NetRelayExt_FileBasedSettings.class.getName(), result -> {
      try {
        if (result.failed()) {
          context.assertTrue(fs.existsBlocking(localSettingsFileNameUserDir),
              "does not exist: " + localSettingsFileNameUserDir);
        } else {
          context.fail("This test expects to fail, cause the settings were created new on disc");
        }
      } finally {
        async.complete();
      }
    });

    async.await();

    final Async async2 = context.async();
    vertx.deployVerticle(NetRelayExt_FileBasedSettings.class.getName(), result -> {
      if (result.failed()) {
        context.assertEquals(InitException.class, result.cause().getClass());
        context.assertTrue(result.cause().getMessage().contains("The settings are not yet edited."),
            "Expected errormessage which contains 'The settings are not yet edited.'");
        async2.complete();
      } else {
        context.fail("This test expects to fail, cause the settings were not edited yet");
        async2.complete();
      }
    });
    async2.await();

    final Async async3 = context.async();
    NetRelayExt_FileBasedSettings netRelay = new NetRelayExt_FileBasedSettings(true);
    vertx.deployVerticle(netRelay, result -> {
      try {
        if (result.failed()) {
          context.fail(result.cause());
        } else {
          context.assertTrue(fs.existsBlocking(localSettingsFileNameUserDir));
          context.assertNotNull(netRelay.getSettings(), "Settings are null");
          context.assertNotNull(netRelay.getSettings().getDatastoreSettings(), "Settings.datastoreSettings are null");
          context.assertNotNull(netRelay.getSettings().getRouterDefinitions(),
              "Settings.getRouterDefinitions are null");
          context.assertNotNull(netRelay.getSettings().getMappingDefinitions(),
              "Settings.getMappingDefinitions are null");
          Class definedMapperClass = netRelay.getSettings().getMappingDefinitions()
              .getMapperClass(NetRelayExt_FileBasedSettings.SIMPLEMAPPER_NAME);
          context.assertNotNull(definedMapperClass,
              "mapperclass is null for key " + NetRelayExt_FileBasedSettings.SIMPLEMAPPER_NAME);
          context.assertEquals(SimpleNetRelayMapper.class, definedMapperClass);
        }
      } finally {
        async3.complete();
      }
    });
    async3.await();
  }

  /**
   * Check for a file inside another location.
   * 
   */
  @Test
  public void testInitSettingsFromOtherDir(TestContext context) {
    context.assertNotNull(vertx);
    FileSystem fs = vertx.fileSystem();
    Settings setting = new NetRelayExt_FileBasedSettings().createDefaultSettings();
    LOGGER.info("handling in " + localSettingsFileNameTmpDir);

    deleteFileInDir(context, fs, localSettingsFileNameTmpDir);
    Async async = context.async();

    DeploymentOptions options = new DeploymentOptions();
    options.setConfig(new JsonObject().put(Settings.SETTINGS_LOCATION_PROPERTY, localSettingsFileNameTmpDir));
    vertx.deployVerticle(NetRelayExt_FileBasedSettings.class.getName(), options, result -> {
      try {
        if (result.failed()) {
          context.assertTrue(fs.existsBlocking(localSettingsFileNameUserDir),
              "does not exist: " + localSettingsFileNameUserDir);
        } else {
          context.fail("This test expects to fail, cause the settings were created new on disc");
        }
      } finally {
        async.complete();
      }
    });
    async.await();

    final Async async2 = context.async();
    vertx.deployVerticle(NetRelayExt_FileBasedSettings.class.getName(), options, result -> {
      try {
        if (result.failed()) {
          context.assertEquals(InitException.class, result.cause().getClass());
          context.assertTrue(result.cause().getMessage().contains("The settings are not yet edited."),
              "Expected errormessage which contains 'The settings are not yet edited.'");
          context.assertTrue(fs.existsBlocking(localSettingsFileNameUserDir));
        } else {
          context.fail("This test expects to fail, cause the settings were not edited yet");
        }
      } finally {
        async2.complete();
      }
    });
    async2.await();
  }

  private void deleteFileInDir(TestContext context, FileSystem fs, String path) {
    if (fs.existsBlocking(path)) {
      fs.deleteBlocking(path);
    }
    context.assertFalse(fs.existsBlocking(path));
  }

  @AfterClass
  public static void shutdown(TestContext context) throws Exception {
    LOGGER.debug("performing shutdown");
    if (vertx != null) {
      Async async = context.async();
      vertx.close(ar -> {
        vertx = null;
        async.complete();
      });
      async.awaitSuccess();
      LOGGER.debug("finished shutdown");
    }
  }

}
