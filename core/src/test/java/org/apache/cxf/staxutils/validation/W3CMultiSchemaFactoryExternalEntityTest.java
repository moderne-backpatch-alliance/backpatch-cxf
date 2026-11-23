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

package org.apache.cxf.staxutils.validation;

import java.io.File;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.stream.StreamSource;

import com.sun.msv.reader.xmlschema.MultiSchemaReader;

import org.objenesis.ObjenesisStd;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class W3CMultiSchemaFactoryExternalEntityTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    @SuppressWarnings("deprecation")
    public void testSchemaWithExternalEntityIsRefused() throws Exception {
        File fragment = tmp.newFile("fragment.xml");
        Files.write(fragment.toPath(),
            "<xs:element name=\"fromEntity\" type=\"xs:string\"/>".getBytes(StandardCharsets.UTF_8));
        String schema = "<?xml version=\"1.0\"?>\n"
            + "<!DOCTYPE xs:schema [<!ENTITY ext SYSTEM \"" + fragment.toURI() + "\">]>\n"
            + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
            + " targetNamespace=\"urn:test\">&ext;</xs:schema>";
        String systemId = tmp.getRoot().toURI() + "test.xsd";

        // The constructor needs the Woodstox 5 W3CSchema; skip it to reach createSchema with Woodstox 6
        W3CMultiSchemaFactory factory = new ObjenesisStd().newInstance(W3CMultiSchemaFactory.class);
        Exception thrown = null;
        try {
            factory.createSchema(systemId,
                Collections.singletonMap("urn:test", new StreamSource(new StringReader(schema), systemId)));
        } catch (Exception e) {
            thrown = e;
        }

        Field f = W3CMultiSchemaFactory.class.getDeclaredField("multiSchemaReader");
        f.setAccessible(true);
        assertNull("A schema with an external entity must not load",
            ((MultiSchemaReader)f.get(factory)).getResult());
        assertTrue("Expected XMLStreamException, got " + thrown, thrown instanceof XMLStreamException);
    }
}
