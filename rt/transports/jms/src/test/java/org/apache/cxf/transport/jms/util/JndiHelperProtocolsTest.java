/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.transport.jms.util;

import java.util.Properties;

import javax.naming.Context;

import org.junit.Assert;
import org.junit.Test;

public class JndiHelperProtocolsTest {

    @Test
    public void testSchemesOutsideAllowListRejected() {
        for (String url : new String[]{"ldaps://localhost", "iiop://localhost", "dns://localhost",
                                       "corbaname:iiop:localhost", "LDAP://localhost", "localhost"}) {
            try {
                new JndiHelper(env(url));
                Assert.fail("Expected IllegalArgumentException for: " + url);
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().contains("Unsafe protocol in JNDI URL"));
            }
        }
    }

    @Test
    public void testAllowListedSchemesAccepted() {
        for (String scheme : new String[]{"vm", "tcp", "nio", "ssl", "http", "https", "ws", "wss"}) {
            new JndiHelper(env(scheme + "://localhost"));
        }
    }

    private static Properties env(String providerUrl) {
        Properties env = new Properties();
        env.put(Context.PROVIDER_URL, providerUrl);
        return env;
    }
}
