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
import de.braintags.netrelay.RequestUtil;
import de.braintags.netrelay.controller.impl.AbstractController;
import de.braintags.netrelay.routing.RouterDefinition;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.mongo.HashSaltStyle;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.BasicAuthHandler;
import io.vertx.ext.web.handler.FormLoginHandler;
import io.vertx.ext.web.handler.RedirectAuthHandler;
import io.vertx.ext.web.handler.UserSessionHandler;

/**
 * Controller performs the authentication, login and logout of members.
 * If a call to a protected page is performed, a 302-redirect to the defined login page is processed
 * 
 * Logout: performs logout and stores message in context with key LOGOUT_MESSAGE_PROP
 * 
 * 
 * Config-Parameter:<br/>
 * <UL>
 * <LI>{@value #AUTH_PROVIDER_PROP} - defines the name of the {@link AuthProvider} to be used. Possible values are:
 * {@value #AUTH_PROVIDER_MONGO}
 * <LI>{@value #AUTH_HANDLER_PROP} - the name of the property, which defines the {@link AuthHandler} to be used.
 * Possible values are:
 * {@link AuthHandlerEnum#BASIC}, {@link AuthHandlerEnum#REDIRECT}
 * <LI>{@value #LOGIN_PAGE_PROP} - the property name, which defines the path to the login page, which shall be used
 * <LI>{@value #DEFAULT_AUTH_ERROR_PAGE_PROP} the default page which will be called, if authentication failed and no
 * previous called page can be found
 * </UL>
 * <br>
 * Request-Parameter:<br/>
 * <br/>
 * Result-Parameter:<br/>
 * <br/>
 * 
 * 
 * @author mremme
 * 
 */
public class AuthenticationController extends AbstractController {
  private static final io.vertx.core.logging.Logger LOGGER = io.vertx.core.logging.LoggerFactory
      .getLogger(AuthenticationController.class);

  /**
   * With this property the url for the login can be defined. It is the url, which is called by a login form and is a
   * virtual url, where the {@link FormLoginHandler} is processed. Default is "/member/login"
   */
  public static final String LOGIN_ACTION_URL_PROP = "loginAction";

  /**
   * The url where a logout action is performed, if a member is logged in
   */
  public static final String LOGOUT_ACTION_URL_PROP = "logoutAction";

  /**
   * By this property you can define the destination page, which is called after a logout
   */
  public static final String LOGOUT_DESTINATION_PAGE_PROP = "logoutDestinationPage";

  /**
   * This is the name of the parameter, which defines the url to redirect to, if the user logs in directly at the url of
   * the form login handler without being redirected here first. ( parameter used by FormLoginHandler )
   */
  public static final String DIRECT_LOGGED_IN_OK_URL_PROP = "directLoggedInOKURL";

  /**
   * By this property the page can be set, which will be called as default page, when authentication failed and the
   * system cannot read the originally called page
   */
  public static final String DEFAULT_AUTH_ERROR_PAGE_PROP = "defaultAuthenticationError";

  /**
   * The default url, where the login action is performed
   */
  public static final String DEFAULT_LOGIN_ACTION_URL = "/member/login";

  /**
   * The default url, where the login action is performed
   */
  public static final String DEFAULT_LOGOUT_ACTION_URL = "/member/logout";

  /**
   * The default url which is called after a logout
   */
  public static final String DEFAULT_LOGOUT_DESTINATION = "/index.html";

  /**
   * This property name is used as key, to store a logout message into the context
   */
  public static final String LOGOUT_MESSAGE_PROP = "logoutMessage";

  /**
   * Used as possible value for property {@link #AUTH_PROVIDER_PROP} and references to an authentivation provider
   * connected to a mongo db
   */
  public static final String AUTH_PROVIDER_MONGO = "MongoAuth";

  /**
   * The name of the key, which is used, to store the name of the mapper in the {@link User#principal()}
   */
  public static final String MAPPERNAME_IN_PRINCIPAL = "mapper";

  /**
   * Defines the name of the {@link AuthProvider} to be used
   */
  public static final String AUTH_PROVIDER_PROP = "authProvider";

  /**
   * Defines the name of the {@link AuthHandler} to be used
   */
  public static final String AUTH_HANDLER_PROP = "authHandler";

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
   * @see io.vertx.core.Handler#handle(java.lang.Object)
   */
  @Override
  public void handle(RoutingContext event) {
    authHandler.handle(event);

  }

  @Override
  public void initProperties(Properties properties) {
    loginPage = (String) properties.get(LOGIN_PAGE_PROP);
    this.authProvider = createAuthProvider(properties);
    setupAuthentication(properties, authProvider);
    initUserSessionHandler();
    initLoginAction();
    initLogoutAction();
    initLogger();
  }

  private void initLogger() {
    getNetRelay().getRouter().route().handler(context -> {
      LOGGER.info("USER: " + context.user());
      context.next();
    });
  }

  private void initUserSessionHandler() {
    getNetRelay().getRouter().route().handler(UserSessionHandler.create(authProvider));
  }

  private void initLogoutAction() {
    String logoutUrl = readProperty(LOGOUT_ACTION_URL_PROP, DEFAULT_LOGOUT_ACTION_URL, false);
    String logoutDestinationURL = readProperty(LOGOUT_DESTINATION_PAGE_PROP, DEFAULT_LOGOUT_DESTINATION, false);
    getNetRelay().getRouter().route(logoutUrl).handler(context -> {
      if (context.user() != null) {
        context.clearUser();
        RequestUtil.removeCurrentUser(context);
      }
      String path = logoutDestinationURL + "?" + LOGOUT_MESSAGE_PROP + "=success";
      RequestUtil.sendRedirect(context.response(), path);
    });

  }

  private void initLoginAction() {
    String loginUrl = readProperty(LOGIN_ACTION_URL_PROP, DEFAULT_LOGIN_ACTION_URL, false);
    String directLoginUrl = readProperty(DIRECT_LOGGED_IN_OK_URL_PROP, null, false);
    String defaultAuthError = readProperty(DEFAULT_AUTH_ERROR_PAGE_PROP, null, false);

    FormLoginHandlerBt fl = new FormLoginHandlerBt(authProvider);
    if (directLoginUrl != null) {
      fl.setDirectLoggedInOKURL(directLoginUrl);
    }
    if (defaultAuthError != null) {
      fl.setDefaultAuthenticationErrorPage(defaultAuthError);
    }
    getNetRelay().getRouter().route(loginUrl).handler(fl);
  }

  /**
   * Creates a default definition for the current instance
   * 
   * @return
   */
  public static RouterDefinition createDefaultRouterDefinition() {
    RouterDefinition def = new RouterDefinition();
    def.setName(AuthenticationController.class.getSimpleName());
    def.setBlocking(false);
    def.setController(AuthenticationController.class);
    def.setHandlerProperties(getDefaultProperties());
    def.setRoutes(new String[] { "/member/*" });
    return def;
  }

  /**
   * Get the default properties for an implementation of StaticController
   * 
   * @return
   */
  public static Properties getDefaultProperties() {
    Properties json = new Properties();
    json.put(LOGIN_PAGE_PROP, "/member/login");
    json.put(AUTH_PROVIDER_PROP, AUTH_PROVIDER_MONGO);
    json.put(MongoAuth.PROPERTY_PASSWORD_FIELD, "password");
    json.put(MongoAuth.PROPERTY_USERNAME_FIELD, "username");
    json.put(MongoAuth.PROPERTY_COLLECTION_NAME, "usertable");
    json.put(MongoAuth.PROPERTY_ROLE_FIELD, "roles");
    json.put(LOGIN_ACTION_URL_PROP, DEFAULT_LOGIN_ACTION_URL);
    json.put(LOGOUT_ACTION_URL_PROP, DEFAULT_LOGOUT_ACTION_URL);
    json.put(LOGOUT_DESTINATION_PAGE_PROP, DEFAULT_LOGOUT_DESTINATION);
    return json;
  }

  private void setupAuthentication(Properties properties, AuthProvider authProvider) {
    AuthHandlerEnum ae = AuthHandlerEnum.valueOf(readProperty(AUTH_HANDLER_PROP, "REDIRECT", false));
    switch (ae) {
    case BASIC:
      authHandler = BasicAuthHandler.create(authProvider);
      break;

    case REDIRECT:
      authHandler = RedirectAuthHandler.create(authProvider, loginPage);
      break;

    default:
      throw new UnsupportedOperationException("unsupported definition for authentication handler: " + ae);
    }
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
          LOGGER.info("Authentication failed: " + result.cause());
          handler.handle(result);
        } else {
          User user = result.result();
          user.principal().put(MAPPERNAME_IN_PRINCIPAL, mapper);
          handler.handle(Future.succeededFuture(user));
        }
      });
    }

  }

  public enum AuthHandlerEnum {
    /**
     * Used as possible value for {@link AbstractAuthController#AUTH_HANDLER_PROP} and creates a
     * {@link BasicAuthHandler}
     */
    BASIC(),
    /**
     * Used as possible value for {@link AbstractAuthController#AUTH_HANDLER_PROP} and creates a
     * {@link RedirectAuthHandler}
     */
    REDIRECT();
  }
}
