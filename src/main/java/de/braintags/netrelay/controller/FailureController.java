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

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import de.braintags.io.vertx.util.ExceptionUtil;
import de.braintags.io.vertx.util.exception.InitException;
import de.braintags.netrelay.RequestUtil;
import de.braintags.netrelay.routing.RouterDefinition;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.ext.web.RoutingContext;

/**
 * A Controller for failing calls. The Controller can be configured to produce output depending on an error code or an
 * exception. For each of them can be defined a redirect address. If no definition was found, then an internal default
 * output is generated. If no definition was found, then an internal default output is generated.
 * 
 * When this controller is added to a RouterDefinition, the {@link RouterDefinition#setFailureDefinition(boolean)}
 * should be set to true.
 * 
 * <br>
 * <br>
 * Config-Parameter:<br/>
 * With the configuration you are able to define the redirect addresses per Exception and / or error code.
 * <UL>
 * <LI>Exception handling parameters<br>
 * All parameters, which are starting with {@value #EXCEPTION_START_PARAMETER} are interpreted as definition for
 * occuring exceptions. For example:
 * 
 * <pre>
 * EX:java.lang.IndexOutOfBoundsException=/error/exception.html
 * </pre>
 * 
 * The system tries to instantiate the defined Exception. If during runtime then an exception occures and the occured
 * Exception is the same or a subclass of the defined IndexOutOfBoundsException, then the redirect is processed to
 * the defined URI
 * <br>
 * 
 * <LI>Errorcode handling parameters<br>
 * All parameters, which are starting with {@value #ERRORCODE_START_PARAMETER} are interpreted for those situations,
 * where not an exception occured, but an errorcode was raised.
 * 
 * <LI>{@value #DEFAULT_PROPERTY}<br>
 * by using this propertyname, the default redirect address can be defined, which is used for the cases, where an
 * exception or an errorcode occured, where no definition could be found
 * </UL>
 * 
 * <br>
 * Request-Parameter:<br/>
 * <br/>
 * Result-Parameter:<br/>
 * <br/>
 * 
 * @author Michael Remme
 */
public class FailureController extends AbstractController {
  private static final io.vertx.core.logging.Logger LOGGER = io.vertx.core.logging.LoggerFactory
      .getLogger(FailureController.class);
  /**
   * All properties, which are starting whith this text are interpreted to define reactions on an exception
   */
  public static final String EXCEPTION_START_PARAMETER = "EX:";

  /**
   * All properties, which are starting whith this text are interpreted to define reactions on an exception
   */
  public static final String ERRORCODE_START_PARAMETER = "ERR:";

  /**
   * By using this parameter for a property, the default redirect page can be defined
   */
  public static final String DEFAULT_PROPERTY = "DEFAULT";

  private Map<Class, String> exceptionDefinitions = new HashMap<Class, String>();
  private Map<Integer, String> codeDefinitions = new HashMap<Integer, String>();
  private String defaultRedirect;

  @Override
  public void initProperties(Properties properties) {
    LOGGER.info("init " + getClass().getName());
    defaultRedirect = readProperty(DEFAULT_PROPERTY, null, false);
    Enumeration<Object> keys = properties.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      if (key.startsWith(EXCEPTION_START_PARAMETER)) {
        addException(key, properties.getProperty(key));
      } else if (key.startsWith(ERRORCODE_START_PARAMETER)) {
        addErrorCode(key, properties.getProperty(key));
      }
    }
  }

  @SuppressWarnings("rawtypes")
  private void addException(String key, String value) {
    try {
      Class defClass = Class.forName(key.substring(EXCEPTION_START_PARAMETER.length()));
      exceptionDefinitions.put(defClass, value);
    } catch (ClassNotFoundException e) {
      throw new InitException(e);
    }
  }

  private void addErrorCode(String key, String value) {
    Integer code = Integer.parseInt(key.substring(ERRORCODE_START_PARAMETER.length()));
    codeDefinitions.put(code, value);
  }

  /*
   * (non-Javadoc)
   * 
   * @see io.vertx.core.Handler#handle(java.lang.Object)
   */
  @Override
  public void handle(RoutingContext context) {
    HttpResponseStatus status = context.statusCode() > 0 ? HttpResponseStatus.valueOf(context.statusCode()) : null;

    String reply = String.format("Statuscode %d %s for request %s", context.statusCode(),
        status == null ? "" : "( " + status.reasonPhrase() + " )", context.request().path());
    LOGGER.info(reply);
    if (context.failure() != null) {
      reactByException(context);
    } else {
      reactByStatusCode(context);
    }
  }

  private void reactByException(RoutingContext context) {
    Throwable error = context.failure();
    String redirect = getRedirectByException(error);
    LOGGER.error("", error);
    if (redirect != null && !context.request().path().equalsIgnoreCase(redirect)) {
      RequestUtil.sendRedirect(context.response(), redirect);
    } else {
      String reply = String.format("Statuscode %d for request %s", context.statusCode(), context.request().path());
      reply += "\n" + error.toString();
      reply += "\n" + ExceptionUtil.getStackTrace(error);
      if (!context.response().ended()) {
        context.response().end(reply);
      }
    }
  }

  @SuppressWarnings({ "rawtypes" })
  private String getRedirectByException(Throwable error) {
    Iterator<Entry<Class, String>> keys = exceptionDefinitions.entrySet().iterator();
    while (keys.hasNext()) {
      Entry<Class, String> entry = keys.next();
      if (entry.getKey().isAssignableFrom(error.getClass())) {
        return entry.getValue();
      }
    }
    if (defaultRedirect != null && defaultRedirect.hashCode() != 0) {
      return defaultRedirect;
    }
    return null;
  }

  private void reactByStatusCode(RoutingContext context) {
    String redirect = getRedirectByStatusCode(context.statusCode());
    if (redirect != null && !context.request().path().equalsIgnoreCase(redirect)) {
      RequestUtil.sendRedirect(context.response(), redirect);
    } else {
      handleDefaultStatus(context);
    }

  }

  private String getRedirectByStatusCode(int statusCode) {
    if (codeDefinitions.containsKey(statusCode)) {
      return codeDefinitions.get(statusCode);
    }
    if (defaultRedirect != null && defaultRedirect.hashCode() != 0) {
      return defaultRedirect;
    }
    return null;
  }

  private void handleDefaultStatus(RoutingContext context) {
    if (!context.response().ended()) {
      context.response().setStatusCode(context.statusCode());
      context.response().end();
    }
  }

  /**
   * Creates a default definition for the current instance
   * 
   * @return
   */
  public static RouterDefinition createDefaultRouterDefinition() {
    RouterDefinition def = new RouterDefinition();
    def.setName(FailureController.class.getSimpleName());
    def.setBlocking(false);
    def.setFailureDefinition(true);
    def.setController(FailureController.class);
    return def;
  }

}