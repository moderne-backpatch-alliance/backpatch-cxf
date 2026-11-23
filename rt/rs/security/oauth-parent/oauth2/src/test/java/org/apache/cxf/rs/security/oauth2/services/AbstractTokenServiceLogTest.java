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
package org.apache.cxf.rs.security.oauth2.services;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.rs.security.oauth2.common.Client;
import org.apache.cxf.rs.security.oauth2.common.OAuthError;
import org.apache.cxf.rs.security.oauth2.provider.OAuthServiceException;
import org.apache.cxf.rs.security.oauth2.utils.OAuthConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AbstractTokenServiceLogTest {

    private final List<String> messages = new ArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private Logger logger;

    @Before
    public void addHandler() {
        logger = AbstractOAuthService.LOG;
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
    }

    @After
    public void removeHandler() {
        logger.removeHandler(handler);
    }

    @Test
    public void testUnknownClientIdLoggedWithoutLineBreaks() {
        callGetClient(new LogTestTokenService(null));
        assertEquals(1, messages.size());
        assertEquals("No valid client found for clientId: bad__client", messages.get(0));
    }

    @Test
    public void testRejectedClientIdLoggedWithoutLineBreaks() {
        LogTestTokenService service = new LogTestTokenService(
            new OAuthServiceException(new OAuthError(OAuthConstants.UNAUTHORIZED_CLIENT)));
        callGetClient(service);
        assertEquals(1, messages.size());
        assertEquals("No valid client found for clientId: bad__client", messages.get(0));
    }

    private static void callGetClient(LogTestTokenService service) {
        try {
            service.getClient("bad\r\nclient", "secret", new MetadataMap<String, String>());
            fail("RejectedClientException expected");
        } catch (RejectedClientException ex) {
            // expected
        }
    }

    private static final class LogTestTokenService extends AbstractTokenService {
        private final OAuthServiceException exception;

        LogTestTokenService(OAuthServiceException exception) {
            this.exception = exception;
        }

        @Override
        protected Client getValidClient(String clientId, String clientSecret,
                                        MultivaluedMap<String, String> params) {
            if (exception != null) {
                throw exception;
            }
            return null;
        }

        @Override
        protected void reportInvalidClient(OAuthError error) {
            throw new RejectedClientException();
        }
    }

    private static final class RejectedClientException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
