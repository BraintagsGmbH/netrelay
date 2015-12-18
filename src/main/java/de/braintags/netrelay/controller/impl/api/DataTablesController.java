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
package de.braintags.netrelay.controller.impl.api;

import java.util.List;
import java.util.Properties;

import de.braintags.io.vertx.pojomapper.dataaccess.query.IQuery;
import de.braintags.io.vertx.pojomapper.exception.ParameterRequiredException;
import de.braintags.io.vertx.pojomapper.mapping.IField;
import de.braintags.io.vertx.pojomapper.mapping.IMapper;
import de.braintags.io.vertx.pojomapper.mapping.IStoreObject;
import de.braintags.netrelay.controller.impl.AbstractController;
import de.braintags.netrelay.controller.impl.api.DataTableLinkDescriptor.ColDef;
import de.braintags.netrelay.mapping.NetRelayMapperFactory;
import de.braintags.netrelay.routing.RouterDefinition;
import edu.emory.mathcs.backport.java.util.Arrays;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A controller, which
 * 
 * @author Michael Remme
 * 
 */
public class DataTablesController extends AbstractController {
  /**
   * The name of a the property in the request, which specifies the mapper
   */
  public static final String MAPPER_KEY = "mapper";

  private NetRelayMapperFactory mapperFactory;

  /*
   * (non-Javadoc)
   * 
   * @see io.vertx.core.Handler#handle(java.lang.Object)
   */
  @Override
  public void handle(RoutingContext context) {
    String mapperName = context.request().getParam(MAPPER_KEY);
    if (mapperName == null) {
      context.fail(new ParameterRequiredException(MAPPER_KEY));
    } else {
      Class mapperClass = getNetRelay().getSettings().getMappingDefinitions().getMapperClass(mapperName);
      DataTableLinkDescriptor descr = new DataTableLinkDescriptor(mapperClass, context);
      IQuery<?> query = descr.toQuery(getNetRelay().getDatastore());
      execute(query, descr, result -> {
        if (result.failed()) {
          context.fail(result.cause());
        } else {
          HttpServerResponse response = context.response();
          response.putHeader("content-type", "application/json; charset=utf-8").end(result.result().encodePrettily());
        }
      });
    }
  }

  private void execute(IQuery<?> query, DataTableLinkDescriptor descr, Handler<AsyncResult<JsonObject>> handler) {
    query.execute(qr -> {
      if (qr.failed()) {
        handler.handle(Future.failedFuture(qr.cause()));
      } else {
        qr.result().toArray(result -> {
          if (result.failed()) {
            handler.handle(Future.failedFuture(result.cause()));
          } else {
            Object[] selection = result.result();
            mapperFactory.getStoreObjectFactory().createStoreObjects(query.getMapper(), Arrays.asList(selection),
                str -> {
              if (str.failed()) {
                handler.handle(Future.failedFuture(result.cause()));
              } else {
                handler.handle(Future.succeededFuture(createJsonObject(query.getMapper(), str.result(), descr)));
              }
            });
          }
        });
      }
    });
  }

  private JsonObject createJsonObject(IMapper mapper, List<IStoreObject<?>> selection, DataTableLinkDescriptor descr) {
    JsonObject json = new JsonObject();
    json.put("iTotalRecords", selection.size());
    json.put("iTotalDisplayRecords", selection.size());
    JsonArray resArray = new JsonArray();
    json.put("aaData", resArray);
    for (IStoreObject<?> ob : selection) {
      resArray.add(handleObject(mapper, ob, descr));
    }
    return json;
  }

  private JsonArray handleObject(IMapper mapper, IStoreObject<?> sto, DataTableLinkDescriptor descr) {
    JsonArray json = new JsonArray();
    for (ColDef colDef : descr.getColumns()) {
      IField field = mapper.getField(colDef.name);
      json.add(sto.get(field));
    }
    return json;
  }

  /*
   * (non-Javadoc)
   * 
   * @see de.braintags.netrelay.controller.impl.AbstractController#initProperties(java.util.Properties)
   */
  @Override
  public void initProperties(Properties properties) {
    mapperFactory = new NetRelayMapperFactory(getNetRelay());
  }

  /**
   * Creates a default definition for the current instance
   * 
   * @return
   */
  public static RouterDefinition createDefaultRouterDefinition() {
    RouterDefinition def = new RouterDefinition();
    def.setActive(false);
    def.setName(DataTablesController.class.getSimpleName());
    def.setBlocking(false);
    def.setController(DataTablesController.class);
    def.setHandlerProperties(getDefaultProperties());
    def.setRoutes(new String[] {});
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