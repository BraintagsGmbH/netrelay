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
package de.braintags.netrelay.processor;

import org.junit.Test;

import de.braintags.netrelay.init.Settings;
import de.braintags.netrelay.unit.NetRelayBaseTest;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

/**
 * Test the TemplateController of NetRelay
 * 
 * @author Michael Remme
 * 
 */
public class TProcessorDefs extends NetRelayBaseTest {
  private static final io.vertx.core.logging.Logger LOGGER = io.vertx.core.logging.LoggerFactory
      .getLogger(TProcessorDefs.class);
  public static boolean eventProcessed = false;
  public static String demoProperty = null;
  public static final String DEMO_PROPERTY = "demoValue";
  public static final int WAITTIME = 120000;
  public static final int FREQUENCE = 1000;

  @Test
  public void testProcessor(TestContext context) throws Exception {
    try {
      Async async = context.async();
      DemoProcessor.async = async;
      context.assertEquals(DEMO_PROPERTY, demoProperty, "init does not seem to be handled");
      async.await(WAITTIME);
      context.assertTrue(eventProcessed, "the event wasn't processed");
    } catch (Exception e) {
      context.fail(e);
    }

  }

  /*
   * (non-Javadoc)
   * 
   * @see de.braintags.netrelay.NetRelayBaseTest#initTest()
   */
  @Override
  public void initTest(TestContext context) {
    super.initTest(context);
  }

  /*
   * (non-Javadoc)
   * 
   * @see de.braintags.netrelay.NetRelayBaseTest#modifySettings(de.braintags.netrelay.init.Settings)
   */
  @Override
  public void modifySettings(TestContext context, Settings settings) {
    super.modifySettings(context, settings);
    ProcessorDefinition pd = new ProcessorDefinition();
    pd.setActive(true);
    pd.setName("DemoProcessor");
    pd.setProcessorClass(DemoProcessor.class);
    pd.setTimeDef(String.valueOf(FREQUENCE));
    pd.getProcessorProperties().put("demoKey", "demoValue");
    settings.getProcessorDefinitons().add(pd);
  }

}
