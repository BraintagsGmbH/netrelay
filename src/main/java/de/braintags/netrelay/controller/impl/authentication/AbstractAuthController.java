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
package de.braintags.netrelay.controller.impl.authentication;

import java.util.Properties;

import de.braintags.io.vertx.pojomapper.IDataStore;
import de.braintags.io.vertx.pojomapper.mongo.MongoDataStore;
import de.braintags.netrelay.controller.impl.AbstractController;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.mongo.HashSaltStyle;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.RedirectAuthHandler;

/**
 * A controller, which generates and keeps an {@link AuthHandler}
 * 
 * @author Michael Remme
 * 
 */
public abstract class AbstractAuthController extends AbstractController {
  public static final String AUTH_PROVIDER_MONGO = "MongoAuth";

  /**
   * The name of the key, which is used to store the mapper in the {@link User#principal()}
   */
  public static final String MAPPERNAME_IN_PRINCIPAL = "mapper";
  /**
   * Defines the name of the {@link AuthProvider} to be used
   */
  public static final String AUTH_PROVIDER_PROP = "authProvider";
  /**
   * The name of the property which defines the login page to be used
   */
  public static final String LOGIN_PAGE_PROP = "loginPage";

  protected AuthHandler authHandler;
  protected AuthProvider authProvider;
  private String loginPage;

  /*
   * (non-Javadoc)
   * 
   * @see de.braintags.netrelay.controller.impl.AbstractController#initProperties(java.util.Properties)
   */
  @Override
  public void initProperties(Properties properties) {
    loginPage = (String) properties.get(LOGIN_PAGE_PROP);
    this.authProvider = createAuthProvider(properties);
    setupAuthentication(authProvider);
  }

  private void setupAuthentication(AuthProvider authProvider) {
    authHandler = RedirectAuthHandler.create(authProvider, loginPage);
  }

  private AuthProviderProxy createAuthProvider(Properties properties) {
    String authProvider = (String) properties.get(AUTH_PROVIDER_PROP);
    String mapper = readProperty(MongoAuth.PROPERTY_COLLECTION_NAME, null, true);
    if (authProvider.equals(AUTH_PROVIDER_MONGO)) {
      return new AuthProviderProxy(initMongoAuthProvider(mapper), mapper);
    } else {
      throw new UnsupportedOperationException("unsupported authprovider: " + authProvider);
    }
  }

  /**
   * Init the Authentication Service
   */
  private AuthProvider initMongoAuthProvider(String mapper) {
    IDataStore store = getNetRelay().getDatastore();
    if (!(store instanceof MongoDataStore)) {
      throw new IllegalArgumentException("MongoAuthProvider expects a MongoDataStore");
    }
    JsonObject config = new JsonObject();
    String saltStyle = readProperty(MongoAuth.PROPERTY_SALT_STYLE, HashSaltStyle.NO_SALT.toString(), false);
    config.put(MongoAuth.PROPERTY_SALT_STYLE, HashSaltStyle.valueOf(saltStyle));
    MongoAuth auth = MongoAuth.create(((MongoDataStore) store).getMongoClient(), config);

    auth.setPasswordField(readProperty(MongoAuth.PROPERTY_PASSWORD_FIELD, null, true));
    auth.setUsernameField(readProperty(MongoAuth.PROPERTY_USERNAME_FIELD, null, true));
    auth.setCollectionName(mapper);

    String roleField = readProperty(MongoAuth.PROPERTY_ROLE_FIELD, null, false);
    if (roleField != null) {
      auth.setRoleField(roleField);
    }
    String saltField = readProperty(MongoAuth.PROPERTY_SALT_FIELD, null, false);
    if (saltField != null) {
      auth.setSaltField(saltField);
    }

    return auth;
  }

  class AuthProviderProxy implements AuthProvider {
    AuthProvider prov;
    String mapper;

    AuthProviderProxy(AuthProvider prov, String mapper) {
      this.prov = prov;
      this.mapper = mapper;
    }

    /*
     * (non-Javadoc)
     * 
     * @see io.vertx.ext.auth.AuthProvider#authenticate(io.vertx.core.json.JsonObject, io.vertx.core.Handler)
     */
    @Override
    public void authenticate(JsonObject arg0, Handler<AsyncResult<User>> handler) {
      prov.authenticate(arg0, result -> {
        if (result.failed()) {
          handler.handle(result);
        } else {
          User user = result.result();
          user.principal().put(MAPPERNAME_IN_PRINCIPAL, mapper);
          handler.handle(Future.succeededFuture(user));
        }
      });
    }

  }
}