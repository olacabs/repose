/*
 * _=_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=
 * Repose
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Copyright (C) 2010 - 2015 Rackspace US, Inc.
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=_
 */
package org.openrepose.commons.utils.logging.apache;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.openrepose.commons.utils.http.CommonHttpHeader;
import org.openrepose.commons.utils.logging.apache.format.FormatterLogic;
import org.openrepose.commons.utils.logging.apache.format.LogArgumentFormatter;
import org.openrepose.commons.utils.logging.apache.format.stock.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Enclosed.class)
public class HttpLogFormatterTest {

    public static HttpLogFormatter newFormatter(String format) {
        return new HttpLogFormatter(format);
    }

    public static class WhenParsingSimpleArguments {

        public HttpServletResponse response;
        public HttpServletRequest request;

        @Before
        public void setup() {
            request = mock(HttpServletRequest.class);
            when(request.getRequestURL()).thenReturn(new StringBuffer("http://some.place.net/u/r/l"));
            response = mock(HttpServletResponse.class);
        }

        @Test
        public void shouldCorrectlyDetectEscapeSequences() {
            final HttpLogFormatter formatter = newFormatter("%h %% %u %U");

            assertTrue("Should have parsed seven handlers. Only found: " + formatter.getHandlerList().size(), formatter.getHandlerList().size() == 7);
        }

        @Test
        public void shouldCorrectlyParseEmptySpace() {
            final HttpLogFormatter formatter = newFormatter("%h%%%u%U");

            assertTrue("Should have parsed four handlers. Only found: " + formatter.getHandlerList().size(), formatter.getHandlerList().size() == 4);
        }

        @Test
        public void shouldPreserveStringFormatting() {
            final HttpLogFormatter formatter = newFormatter("%%log output%% %U");
            final String expected = "%log output% http://some.place.net/u/r/l";

            assertEquals(expected, formatter.format(request, response));
        }

        @Test
        public void shouldCorrectlyConstructRequestLine() {
            when(request.getProtocol()).thenReturn("HTTP/1.1");
            when(request.getRequestURI()).thenReturn("/index.html");
            when(request.getMethod()).thenReturn("GET");

            final HttpLogFormatter formatter = newFormatter("%r");
            final String expected = "GET /index.html HTTP/1.1";

            assertEquals(expected, formatter.format(request, response));
        }

        @Test
        public void shouldReplaceTokenWithRequestGuid() {
            final HttpLogFormatter formatter = newFormatter("%" + LogFormatArgument.TRACE_GUID.toString());
            final String expected = "test-guid";

            Vector<String> reqGuidValues = new Vector<>();
            reqGuidValues.add("test-guid");

            when(request.getHeaders(CommonHttpHeader.TRACE_GUID.toString()))
                    .thenReturn(reqGuidValues.elements());

            assertEquals(expected, formatter.format(request, response));
        }
    }

    public static class WhenParsingComplexArguments {

        public HttpServletResponse response;
        public HttpServletRequest request;

        @Before
        public void setup() {
            request = mock(HttpServletRequest.class);
            when(request.getRequestURL()).thenReturn(new StringBuffer("http://some.place.net/u/r/l"));
            response = mock(HttpServletResponse.class);
        }

        @Test
        public void shouldParseInclusiveStatusCodeRestrictions() {
            final HttpLogFormatter formatter = newFormatter("%200,201U");
            final String expected = "http://some.place.net/u/r/l";

            when(response.getStatus()).thenReturn(200);
            assertEquals(expected, formatter.format(request, response));
            when(response.getStatus()).thenReturn(401);
            assertEquals("-", formatter.format(request, response));
        }

        @Test
        public void shouldParseExclusiveStatusCodeRestrictions() {
            final HttpLogFormatter formatter = newFormatter("%!401,403U");
            final String expected = "http://some.place.net/u/r/l";

            assertEquals(expected, formatter.format(request, response));
            when(response.getStatus()).thenReturn(401);
            assertEquals("-", formatter.format(request, response));
        }
    }

    public static class WhenSettingLogic {

        private LogArgumentFormatter formatter;
        private FormatterLogic logic;

        @Before
        public void setup() {
            formatter = new LogArgumentFormatter();
        }

        @Test
        public void shouldNotHaveSetLogicByDefault() {
            assertNull(formatter.getLogic());
        }

        @Test
        public void CanonicalPortHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.CANONICAL_PORT.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof CanonicalPortHandler);
        }

        @Test
        public void LocalAddressHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.LOCAL_ADDRESS.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof LocalAddressHandler);
        }

        @Test
        public void StatusCodeHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.STATUS_CODE.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof StatusCodeHandler);
        }

        @Test
        public void PercentHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.PERCENT.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof StringHandler);
        }

        @Test
        public void RequestHeaderHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.REQUEST_HEADER.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof RequestHeaderHandler);
        }

        @Test
        public void ResponseHeaderHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.RESPONSE_HEADER.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof ResponseHeaderHandler);
        }

        @Test
        public void QueryStringHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.QUERY_STRING.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof QueryStringHandler);
        }

        @Test
        public void RemoteAddressHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.REMOTE_ADDRESS.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof RemoteAddressHandler);
        }

        @Test
        public void RemoteHostHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.REMOTE_HOST.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof RemoteHostHandler);
        }

        @Test
        public void RemoteUserHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.REMOTE_USER.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof RemoteUserHandler);
        }

        @Test
        public void RequestMethodHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.REQUEST_METHOD.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof RequestMethodHandler);
        }

        @Test
        public void ResponseBytesHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.RESPONSE_BYTES.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof ResponseBytesHandler);
        }

        @Test
        public void TimeReceivedHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.TIME_RECEIVED.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof TimeReceivedHandler);
        }

        @Test
        public void UrlRequestedHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.URL_REQUESTED.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof UrlRequestedHandler);
        }

        @Test
        public void RequestGuidHandler() {
            LogArgumentGroupExtractor extractor = LogArgumentGroupExtractor.instance("", "", "", "", LogFormatArgument.TRACE_GUID.toString());

            HttpLogFormatter.setLogic(extractor, formatter);

            assertTrue(formatter.getLogic() instanceof RequestHeaderHandler);
        }
    }
}
