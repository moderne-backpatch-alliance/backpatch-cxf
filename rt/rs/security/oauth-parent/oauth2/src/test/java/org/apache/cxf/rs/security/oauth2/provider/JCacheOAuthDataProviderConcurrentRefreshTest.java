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
package org.apache.cxf.rs.security.oauth2.provider;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.cxf.rs.security.oauth2.common.AccessTokenRegistration;
import org.apache.cxf.rs.security.oauth2.common.Client;
import org.apache.cxf.rs.security.oauth2.common.ServerAccessToken;
import org.apache.cxf.rs.security.oauth2.common.UserSubject;
import org.apache.cxf.rs.security.oauth2.tokens.refresh.RefreshToken;
import org.apache.cxf.rs.security.oauth2.utils.OAuthConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Concurrent refreshes with one refresh token when refresh tokens are not recycled:
 * every access token issued must stay linked to the refresh token, so revoking it revokes them all.
 */
public class JCacheOAuthDataProviderConcurrentRefreshTest {

    private PausingJCacheOAuthDataProvider provider;

    @Before
    public void setUp() throws Exception {
        provider = new PausingJCacheOAuthDataProvider();
        provider.setSupportedScopes(Collections.singletonMap("refreshToken", "RefreshToken"));
        provider.setRecycleRefreshTokens(false);
    }

    @After
    public void tearDown() throws Exception {
        provider.removeClient("concurrent");
        provider.close();
    }

    @Test
    public void testConcurrentRefreshKeepsEveryAccessTokenLinked() throws Exception {
        final Client c = new Client();
        c.setRedirectUris(Collections.singletonList("http://client/redirect"));
        c.setClientId("concurrent");
        c.setClientSecret("123");
        c.setResourceOwnerSubject(new UserSubject("alice"));
        provider.setClient(c);

        AccessTokenRegistration atr = new AccessTokenRegistration();
        atr.setClient(c);
        atr.setApprovedScope(Arrays.asList("refreshToken"));
        atr.setSubject(c.getResourceOwnerSubject());
        ServerAccessToken at0 = provider.createAccessToken(atr);
        final String refreshTokenKey = at0.getRefreshToken();
        assertNotNull(refreshTokenKey);

        Callable<ServerAccessToken> refresh = new Callable<ServerAccessToken>() {
            @Override
            public ServerAccessToken call() {
                return provider.refreshAccessToken(c, refreshTokenKey, Collections.<String>emptyList());
            }
        };
        provider.pause = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ServerAccessToken> f1 = executor.submit(refresh);
            Future<ServerAccessToken> f2 = executor.submit(refresh);
            ServerAccessToken at1 = f1.get(30, TimeUnit.SECONDS);
            ServerAccessToken at2 = f2.get(30, TimeUnit.SECONDS);
            provider.pause = null;

            RefreshToken rt = provider.getRefreshToken(refreshTokenKey);
            assertNotNull(rt);
            List<String> linked = rt.getAccessTokens();
            assertEquals(3, linked.size());
            assertTrue(linked.contains(at0.getTokenKey()));
            assertTrue(linked.contains(at1.getTokenKey()));
            assertTrue(linked.contains(at2.getTokenKey()));

            provider.revokeToken(c, refreshTokenKey, OAuthConstants.REFRESH_TOKEN);
            assertNull(provider.getAccessToken(at0.getTokenKey()));
            assertNull(provider.getAccessToken(at1.getTokenKey()));
            assertNull(provider.getAccessToken(at2.getTokenKey()));
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class PausingJCacheOAuthDataProvider extends JCacheOAuthDataProvider {
        private volatile CountDownLatch pause;

        @Override
        protected RefreshToken getRefreshToken(String refreshTokenKey) {
            RefreshToken rt = super.getRefreshToken(refreshTokenKey);
            CountDownLatch latch = pause;
            if (latch != null) {
                // let a second refresh read the same token, unless the lock keeps it out
                latch.countDown();
                try {
                    latch.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return rt;
        }
    }
}
