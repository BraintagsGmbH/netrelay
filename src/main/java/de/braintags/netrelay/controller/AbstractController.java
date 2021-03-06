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
package de.braintags.netrelay.controller;

import java.util.Properties;

import de.braintags.netrelay.NetRelay;
import de.braintags.netrelay.routing.CaptureCollection;
import de.braintags.netrelay.routing.RouterDefinition;
import de.braintags.vertx.util.HttpContentType;
import de.braintags.vertx.util.exception.ParameterRequiredException;
import de.braintags.vertx.util.request.RequestUtil;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;

/**
 * An abstract implementation of {@link IController}
 *
 * <br/>
 * Documentation Template for Controllers:<br/>
 * Config-Parameter:<br/>
 * possible parameters, which are read from the configuration
 * <UL>
 * <LI>parameter1 - describe the sense of the parameter
 * <LI>parameter2 - describe the sense of the parameter
 * </UL>
 * <br>
 *
 * Request-Parameter:<br/>
 * possible parameters, which are read from a request
 * <UL>
 * <LI>parameter1 - describe the sense of the parameter
 * <LI>parameter2 - describe the sense of the parameter
 * </UL>
 * <br/>
 *
 * Result-Parameter:<br/>
 * possible paramters, which will be placed into the context
 * <UL>
 * <LI>parameter1 - describe the content, which is stored under the given parameter name
 * </UL>
 * <br/>
 *
 * Example configuration:<br/>
 *
 * <pre>
 * {
      "name" : "ExampleController",
      "routes" : null,
      "blocking" : false,
      "failureDefinition" : false,
      "controller" : "de.braintags.netrelay.controller.ExampleController",
      "httpMethod" : null,
      "handlerProperties" : {
        "prop1" : "127.0.0.1",
        "prop2" : "http://localhost",
        "prop3" : "true"
       },
      "captureCollection" : null
    }
 * </pre>
 *
 *
 *
 * @author Michael Remme
 *
 */
public abstract class AbstractController implements IController {
  private static final io.vertx.core.logging.Logger LOGGER = io.vertx.core.logging.LoggerFactory
      .getLogger(AbstractController.class);

  private Vertx vertx;
  private CaptureCollection[] captureCollection;
  private NetRelay netRelay;
  private Properties properties;
  private String name;

  /**
   * The instance of {@link Vertx} which is used to initialize NetRelay
   *
   * @return the vertx
   */
  public final Vertx getVertx() {
    return vertx;
  }

  /*
   * (non-Javadoc)
   *
   * @see io.vertx.core.Handler#handle(java.lang.Object)
   */
  @Override
  public final void handle(final RoutingContext context) {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("handling controller " + getClass().getName());
    try {
      handleController(context);
    } catch (Exception e) {
      handleError(context, e);
    }
  }

  /**
   * The default implementation lets the context fail with the error
   *
   * @param context
   * @param e
   */
  protected void handleError(final RoutingContext context, final Throwable e) {
    context.fail(e);
  }

  /**
   * internal method to handle the request
   *
   * @param context
   */
  protected abstract void handleController(RoutingContext context);

  /*
   * (non-Javadoc)
   *
   * @see de.braintags.netrelay.controller.IController#init(io.vertx.core.Vertx, java.util.Properties)
   */
  @Override
  public final void init(final Vertx vertx, final NetRelay netRelay, final Properties properties,
      final CaptureCollection[] captureCollection, final String name) {
    this.vertx = vertx;
    this.netRelay = netRelay;
    this.properties = properties;
    this.name = name;
    initProperties(properties);
    initCaptureCollection(captureCollection);
    // just add some async init, ignore for now
    init().otherwise(t -> {
      LOGGER.error("error during init of controller: " + this.name, t);
      return null;
    });
  }

  protected Future<Void> init() {
    return Future.succeededFuture();
  }

  /**
   * Initialize the given {@link CaptureCollection}. The default implementation just stores it inside the current
   * instance
   *
   * @param captureCollection
   */
  public void initCaptureCollection(final CaptureCollection[] captureCollection) {
    this.captureCollection = captureCollection;
  }

  /**
   * Initialize the controller by using the definitions inside the {@link Properties}
   *
   * @param properties
   *          the properties to be used for init
   */
  public abstract void initProperties(Properties properties);

  /**
   * The {@link CaptureCollection} which was defined inside the {@link RouterDefinition}
   *
   * @return the captureCollection
   */
  public final CaptureCollection[] getCaptureCollections() {
    return captureCollection;
  }

  /**
   * Get the parent instance of {@link NetRelay}
   *
   * @return the netRelay
   */
  public final NetRelay getNetRelay() {
    return netRelay;
  }

  /**
   * Read the property with the given name
   *
   * @param propertyName
   *          the name of the property to be read
   * @param defaultValue
   *          the default value to be returned
   * @param required
   *          is it required
   * @return the value of the property or null
   * @throws ParameterRequiredException,
   *           if required parameter wasn't found
   */
  public String readProperty(final String propertyName, final String defaultValue, final boolean required) {
    String value = (String) properties.get(propertyName);
    if (value == null && required) {
      throw new ParameterRequiredException(propertyName);
    }
    return value == null ? defaultValue : value;
  }

  /**
   * Read the property with the given name
   *
   * @param properties
   *          the properties to read from
   * @param propertyName
   *          the name of the property to be read
   * @param defaultValue
   *          the default value to be returned
   * @param required
   *          is it required
   * @return the value of the property or null
   * @throws ParameterRequiredException,
   *           if required parameter wasn't found
   */
  public static String readProperty(final Properties properties, final String propertyName, final String defaultValue,
      final boolean required) {
    String value = (String) properties.get(propertyName);
    if (value == null && required) {
      throw new ParameterRequiredException(propertyName);
    }
    return value == null ? defaultValue : value;
  }

  /**
   * Reads a value either from the request or - if not found there - from the configuration properties
   *
   * @param context
   *          the context from the current request
   * @param key
   *          the key to search for
   * @param defaultValue
   *          the default value
   * @param required
   *          is the value required?
   * @return a found value, the default value or null
   * @throws ParameterRequiredException,
   *           if required parameter wasn't found
   */
  public String readParameterOrProperty(final RoutingContext context, final String key, final String defaultValue,
      final boolean required) {
    String value = readParameter(context, key, false);
    if (value == null) {
      value = readProperty(key, null, false);
    }
    if (value == null && required) {
      throw new ParameterRequiredException(key);
    }
    return value == null ? defaultValue : value;
  }

  /**
   * @deprecated use {@link RequestUtil#readParameterOrContext(RoutingContext, String, String, boolean)} instead
   */
  @Deprecated
  public static String readParameterOrContext(final RoutingContext context, final String key, final String defaultValue,
      final boolean required) {
    return RequestUtil.readParameterOrContext(context, key, defaultValue, required);
  }

  /**
   * Reads a value either from the request or - if not found there - from the configuration properties, or - if not
   * found there neither - from the context
   *
   * @param context
   *          the context from the current request
   * @param key
   *          the key to search for
   * @param defaultValue
   *          the default value
   * @param required
   *          is the value required?
   * @return a found value, the default value or null
   * @throws ParameterRequiredException,
   *           if required parameter wasn't found
   */
  public String readParameterOrPropertyOrContext(final RoutingContext context, final String key,
      final String defaultValue, final boolean required) {
    String value = RequestUtil.readParameter(context, key, false);
    if (value == null) {
      value = readProperty(key, null, false);
    }
    if (value == null) {
      value = context.get(key);
    }

    if (value == null && required) {
      throw new ParameterRequiredException(key);
    }
    return value == null ? defaultValue : value;
  }

  /**
   * This mehtod adds the instance of NetRelay into the context, so that it is accessible by template specific
   * processors for instance
   *
   * @param context
   */
  protected void addNetRelayToContext(final RoutingContext context) {
    context.put(NetRelay.NETRELAY_PROPERTY, getNetRelay());
  }

  /**
   * @deprecated use {@link RequestUtil#readParameter(RoutingContext, String, boolean)} instead
   */
  @Deprecated
  public static String readParameter(final RoutingContext context, final String key, final boolean required) {
    return RequestUtil.readParameter(context, key, null, required);
  }

  /**
   * @deprecated use {@link RequestUtil#readParameter(RoutingContext, String, String, boolean)} instead
   */
  @Deprecated
  public static String readParameter(final RoutingContext context, final String key, final String defaultValue,
      final boolean required) {
    return RequestUtil.readParameter(context, key, defaultValue, required);
  }

  /**
   * Returns true, if a URL parameter or form parameter exists with the given key
   *
   * @param context
   *          the context
   * @param key
   *          the key to lookup
   * @return true, if exists
   */
  public static boolean hasParameter(final RoutingContext context, final String key) {
    return context.request().params().contains(key)
        || (context.request().formAttributes() != null && context.request().formAttributes().contains(key));
  }

  /**
   * Send a reply as Json
   *
   * @param content
   */
  protected void sendJson(final RoutingContext context, final String content) {
    sendJson(context, content, 200);
  }

  /**
   * Send a reply as Json
   *
   * @param content
   */
  protected void sendJson(final RoutingContext context, final String content, final int statusCode) {
    LOGGER.debug("sending result as JSON");
    context.response().putHeader("content-type", HttpContentType.APPLICATION_JSON.toString()).setStatusCode(statusCode)
        .end(content);
  }

  /**
   * Get the name of the definition
   *
   * @return
   */
  public final String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }

  /*
   * (non-Javadoc)
   *
   * @see de.braintags.netrelay.controller.IController#validateRoutingDefinition(de.braintags.netrelay.routing.
   * RouterDefinition)
   */
  @Override
  public void validateRoutingDefinition(final RouterDefinition currentDefinition) {
  }

}
