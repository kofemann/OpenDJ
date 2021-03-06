/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.plugins;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.LDAPAttributeDescriptionListPluginCfgDefn;
import org.opends.server.admin.std.server.LDAPAttributeDescriptionListPluginCfg;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/**
 * This class defines a set of tests for the
 * org.opends.server.plugins.LDAPADListPlugin class.
 */
public class LDAPADListPluginTestCase
       extends PluginTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Retrieves a set of valid configuration entries that may be used to
   * initialize the plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "validConfigs")
  public Object[][] getValidConfigs()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=LDAP Attribute Description List,cn=Plugins,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-plugin",
         "objectClass: ds-cfg-ldap-attribute-description-list-plugin",
         "cn: LDAP Attribute Description List",
         "ds-cfg-java-class: org.opends.server.plugins.LDAPADListPlugin",
         "ds-cfg-enabled: true",
         "ds-cfg-plugin-type: preParseSearch");

    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;
  }



  /**
   * Tests the process of initializing the server with valid configurations.
   *
   * @param  e  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "validConfigs")
  public void testInitializeWithValidConfigs(Entry e)
         throws Exception
  {
    HashSet<PluginType> pluginTypes = TestCaseUtils.getPluginTypes(e);

    LDAPAttributeDescriptionListPluginCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              LDAPAttributeDescriptionListPluginCfgDefn.getInstance(), e);

    LDAPADListPlugin plugin = new LDAPADListPlugin();
    plugin.initializePlugin(pluginTypes, configuration);
    plugin.finalizePlugin();
  }



  /**
   * Retrieves a set of invalid configuration entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs() throws Exception
  {
    ArrayList<Entry> entries = new ArrayList<>();
    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=LDAP Attribute Description List,cn=Plugins,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-plugin",
         "objectClass: ds-cfg-ldap-attribute-description-list-plugin",
         "cn: LDAP Attribute Description List",
         "ds-cfg-java-class: org.opends.server.plugins.LDAPADListPlugin",
         "ds-cfg-enabled: true");
    entries.add(e);

    for (String s : PluginType.getPluginTypeNames())
    {
      if (s.equalsIgnoreCase("preParseSearch"))
      {
        continue;
      }

      e = TestCaseUtils.makeEntry(
           "dn: cn=LDAP Attribute Description List,cn=Plugins,cn=config",
           "objectClass: top",
           "objectClass: ds-cfg-plugin",
           "objectClass: ds-cfg-ldap-attribute-description-list-plugin",
           "cn: LDAP Attribute Description List",
           "ds-cfg-java-class: org.opends.server.plugins.LDAPADListPlugin",
           "ds-cfg-enabled: true",
           "ds-cfg-plugin-type: " + s);
      entries.add(e);
    }

    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;
  }



  /**
   * Tests the process of initializing the server with valid configurations.
   *
   * @param  e  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class })
  public void testInitializeWithInvalidConfigs(Entry e)
         throws Exception
  {
    HashSet<PluginType> pluginTypes = TestCaseUtils.getPluginTypes(e);

    LDAPAttributeDescriptionListPluginCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              LDAPAttributeDescriptionListPluginCfgDefn.getInstance(), e);

    LDAPADListPlugin plugin = new LDAPADListPlugin();
    plugin.initializePlugin(pluginTypes, configuration);
    plugin.finalizePlugin();
  }



  /**
   * Tests the <CODE>doPreParseSearch</CODE> method with an empty attribute
   * list.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoPreParseSearchWithEmptyAttrList()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    final SearchRequest request = newSearchRequest(DN.valueOf("o=test"), SearchScope.BASE_OBJECT);
    assertAttributeOExists(request);
  }



  /**
   * Tests the <CODE>doPreParseSearch</CODE> method with an attribute list that
   * contains a standard attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoPreParseSearchWithRequestedAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    SearchRequest request = newSearchRequest(DN.valueOf("o=test"), SearchScope.BASE_OBJECT)
        .addAttribute("o");
    assertAttributeOExists(request);
  }



  /**
   * Tests the <CODE>doPreParseSearch</CODE> method with an attribute list that
   * contains an objectclass name.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoPreParseSearchWithRequestedObjectClass()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    final SearchRequest request = newSearchRequest(DN.valueOf("o=test"), SearchScope.BASE_OBJECT)
        .addAttribute("@organization");
    assertAttributeOExists(request);
  }



  /**
   * Tests the <CODE>doPreParseSearch</CODE> method with an attribute list that
   * contains an undefined objectclass name.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoPreParseSearchWithRequestedUndefinedObjectClass()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    final SearchRequest request =
        newSearchRequest(DN.valueOf("o=test"), SearchScope.BASE_OBJECT).addAttribute("@undefined");
    assertAttributeOExists(request);
  }

  private void assertAttributeOExists(final SearchRequest request)
  {
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e.getAttribute("o"));
  }
}

