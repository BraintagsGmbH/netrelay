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

import org.junit.Test;

import de.braintags.netrelay.RequestUtil;
import io.vertx.ext.unit.TestContext;

/**
 * 
 * 
 * @author Michael Remme
 * 
 */
public class TRequestUtil extends NetRelayBaseTest {
  private static final io.vertx.core.logging.Logger LOGGER = io.vertx.core.logging.LoggerFactory
      .getLogger(TRequestUtil.class);

  @Test
  public void testCleanPath(TestContext context) {
    String path = "first/second/third/fourth";
    String result = RequestUtil.cleanPathElement("second", path);
    context.assertEquals("first/third/fourth", result);
    result = RequestUtil.cleanPathElement("first", path);
    context.assertEquals("second/third/fourth", result);
    result = RequestUtil.cleanPathElement("fourth", path);
    context.assertEquals("first/second/third", result);

    path = "/first/second/third/fourth";
    result = RequestUtil.cleanPathElement("second", path);
    context.assertEquals("/first/third/fourth", result);
    result = RequestUtil.cleanPathElement("first", path);
    context.assertEquals("/second/third/fourth", result);
    result = RequestUtil.cleanPathElement("fourth", path);
    context.assertEquals("/first/second/third", result);

    path = "first/second/third/fourth/";
    result = RequestUtil.cleanPathElement("second", path);
    context.assertEquals("first/third/fourth/", result);
    result = RequestUtil.cleanPathElement("first", path);
    context.assertEquals("second/third/fourth/", result);
    result = RequestUtil.cleanPathElement("fourth", path);
    context.assertEquals("first/second/third/", result);

    path = "first/second/third/firstfourth/";
    result = RequestUtil.cleanPathElement("first", path);
    context.assertEquals("second/third/firstfourth/", result);
  }

}