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
package org.openrepose.filters.flush;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.openrepose.commons.utils.servlet.http.MutableHttpServletResponse;
import org.openrepose.core.filter.logic.FilterAction;
import org.openrepose.core.filter.logic.FilterDirector;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockito.Mockito.*;
import static org.openrepose.core.filter.logic.FilterDirector.SC_UNSUPPORTED_RESPONSE_CODE;

@RunWith(Enclosed.class)
public class FlushOutputHandlerTest {

    public static class WhenHandlingRequests {
        private MutableHttpServletResponse response;
        private FlushOutputHandler instance;

        @Before
        public void setup() {
            this.response = mock(MutableHttpServletResponse.class);
            this.instance = new FlushOutputHandler();
        }

        @Test
        public void shouldCallCommitOutput() throws IOException {
            instance.handleResponse(mock(HttpServletRequest.class), response);
            verify(response).commitBufferToServletOutputStream();
        }

        @Test
        public void shouldNotModifyResponse() {
            final HttpServletRequest request = mock(HttpServletRequest.class);
            when(response.getStatus()).thenReturn(SC_UNSUPPORTED_RESPONSE_CODE);

            final FilterDirector filterDirector = instance.handleResponse(request, response);

            assertNotSame("Must not return an invalid FilterAction.", FilterAction.NOT_SET, filterDirector.getFilterAction());
            assertEquals("Must return the received response status code", SC_UNSUPPORTED_RESPONSE_CODE, filterDirector.getResponseStatusCode());
        }
    }
}
