/*
 * #%L
 * vertx-pojongo
 * %%
 * Copyright (C) 2015 Braintags GmbH
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */
package de.braintags.netrelay.controller.impl;

import java.net.URI;
import java.util.Properties;

import de.braintags.netrelay.controller.impl.persistence.PersistenceController;
import de.braintags.netrelay.routing.RouterDefinition;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * A Controller, which creates and uses a {@link BodyHandler}.
 * The BodyHandler creates som variables and stores them inside the context either:<br/>
 * 
 * <UL>
 * <LI>REQUEST_HOST<br/>
 * the name of the host of the current request
 * <LI>REQUEST_PORT<br/>
 * the port of the host of the current request
 * <LI>REQUEST_SCHEME<br/>
 * the scheme of the host of the current request
 * 
 * </UL>
 * 
 * <br/>
 * <br/>
 * possible paramters are:
 * <br/>
 * {@value #BODY_LIMIT_PROP}<br/>
 * {@value #UPLOAD_DIRECTORY_PROP}
 * 
 * 
 * @author Michael Remme
 * 
 */
public class BodyController extends AbstractController {
  private static final io.vertx.core.logging.Logger LOGGER = io.vertx.core.logging.LoggerFactory
      .getLogger(BodyController.class);

  private BodyHandler bodyHandler;

  /**
   * Defines the maximum size of a body of a request, including all field information and uploaded files.
   * The default is -1
   */
  public static final String BODY_LIMIT_PROP = "bodyLimit";

  /**
   * Defines the directory, where uploaded files are stored into. The upload directory defaults to
   * {@link BodyHandler#DEFAULT_UPLOADS_DIRECTORY}
   * NOTE: the directory defined here should be the temporary directory for incoming files. Other controller, like
   * the {@link PersistenceController} will define an own directory, where those files, which shall be referenced into
   * a record, will be moved.
   */
  public static final String UPLOAD_DIRECTORY_PROP = "uploadDirectory";

  /*
   * (non-Javadoc)
   * 
   * @see io.vertx.core.Handler#handle(java.lang.Object)
   */
  @Override
  public void handle(RoutingContext event) {
    try {
      URI uri = URI.create(event.request().absoluteURI());
      event.put("REQUEST_HOST", uri.getHost());
      event.put("REQUEST_PORT", uri.getPort());
      event.put("REQUEST_SCHEME", uri.getScheme());
    } catch (Exception e) {
      LOGGER.warn(e.toString());
    }
    bodyHandler.handle(event);
  }

  /*
   * (non-Javadoc)
   * 
   * @see de.braintags.netrelay.controller.impl.AbstractController#initProperties(java.util.Properties)
   */
  @Override
  public void initProperties(Properties properties) {
    int upSize = Integer.parseInt(readProperty(BODY_LIMIT_PROP, "-1", false));
    String upDir = readProperty(UPLOAD_DIRECTORY_PROP, null, false);
    bodyHandler = BodyHandler.create().setBodyLimit(upSize);
    if (upDir != null) {
      bodyHandler.setUploadsDirectory(upDir);
    }
  }

  /**
   * Creates a default definition for the current instance
   * 
   * @return
   */
  public static RouterDefinition createDefaultRouterDefinition() {
    RouterDefinition def = new RouterDefinition();
    def.setName(BodyController.class.getSimpleName());
    def.setBlocking(false);
    def.setController(BodyController.class);
    def.setHandlerProperties(getDefaultProperties());
    return def;
  }

  /**
   * Get the default properties for an implementation of StaticController
   * 
   * @return
   */
  public static Properties getDefaultProperties() {
    Properties json = new Properties();
    return json;
  }

}
