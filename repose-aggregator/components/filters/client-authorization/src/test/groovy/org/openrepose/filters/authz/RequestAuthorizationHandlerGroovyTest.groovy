package org.openrepose.filters.authz
import com.rackspace.httpdelegation.HttpDelegationHeaders
import com.rackspace.httpdelegation.JavaDelegationManagerProxy
import org.openrepose.common.auth.openstack.AuthenticationService
import org.openrepose.commons.utils.http.CommonHttpHeader
import org.openrepose.commons.utils.http.HttpStatusCode
import org.openrepose.commons.utils.http.OpenStackServiceHeader
import org.openrepose.commons.utils.http.header.HeaderName
import org.openrepose.components.authz.rackspace.config.DelegatingType
import org.openrepose.components.authz.rackspace.config.IgnoreTenantRoles
import org.openrepose.components.authz.rackspace.config.ServiceEndpoint
import org.openrepose.core.filter.logic.FilterAction
import org.openrepose.core.filter.logic.FilterDirector
import org.openrepose.filters.authz.cache.CachedEndpoint
import org.openrepose.filters.authz.cache.EndpointListCache
import org.openstack.docs.identity.api.v2.AuthenticateResponse
import org.openstack.docs.identity.api.v2.Endpoint
import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification
import spock.lang.Unroll

import javax.servlet.http.HttpServletRequest

import static org.junit.Assert.assertEquals
import static org.mockito.Matchers.any
import static org.mockito.Matchers.eq
import static org.mockito.Mockito.*

class RequestAuthorizationHandlerGroovyTest extends Specification {

    AuthenticationService authenticationService
    AuthenticateResponse authenticateResponse
    EndpointListCache endpointListCache
    ServiceEndpoint serviceEndpoint
    IgnoreTenantRoles ignoreTenantRoles
    HttpServletRequest httpServletRequest
    RequestAuthorizationHandler requestAuthorizationHandler

    private static final String UNAUTHORIZED_TOKEN = "abcdef-abcdef-abcdef-abcdef", AUTHORIZED_TOKEN = "authorized", CACHED_TOKEN = "cached";
    private static final String PUBLIC_URL = "http://service.api.f.com/v1.1", REGION = "ORD", NAME = "Nova", TYPE = "compute";

    protected AuthenticationService mockedAuthService;
    protected RequestAuthorizationHandler handler;
    protected EndpointListCache mockedCache;
    protected MockHttpServletRequest mockedRequest;

    def setup() {
        mockedRequest = new MockHttpServletRequest();
        authenticationService = Mock()
        authenticateResponse = Mock()
        endpointListCache = Mock()
        serviceEndpoint = Mock()
        ignoreTenantRoles = new IgnoreTenantRoles()
        requestAuthorizationHandler = Mock()

        // Caching mocks
        mockedCache = mock(EndpointListCache.class);

        final List<CachedEndpoint> cachedEndpointList = new LinkedList<CachedEndpoint>();
        cachedEndpointList.add(new CachedEndpoint(PUBLIC_URL, REGION, NAME, TYPE));

        when(mockedCache.getCachedEndpointsForToken(AUTHORIZED_TOKEN)).thenReturn(null);
        when(mockedCache.getCachedEndpointsForToken(CACHED_TOKEN)).thenReturn(cachedEndpointList);

        // Auth service mocks
        final List<Endpoint> endpointList = new LinkedList<Endpoint>();

        Endpoint endpoint = new Endpoint();
        endpoint.setPublicURL(PUBLIC_URL);
        endpoint.setRegion(REGION);
        endpoint.setName(NAME);
        endpoint.setType(TYPE);

        // Added to test case where endpoint values are null
        Endpoint endpointb = new Endpoint();

        endpointList.add(endpointb);
        endpointList.add(endpoint);

        mockedAuthService = mock(AuthenticationService.class);
        when(mockedAuthService.getEndpointsForToken(UNAUTHORIZED_TOKEN)).thenReturn(Collections.EMPTY_LIST);
        when(mockedAuthService.getEndpointsForToken(AUTHORIZED_TOKEN)).thenReturn(endpointList);
        when(mockedAuthService.getEndpointsForToken(CACHED_TOKEN)).thenReturn(endpointList);

        final ServiceEndpoint myServiceEndpoint = new ServiceEndpoint();
        myServiceEndpoint.setHref(PUBLIC_URL);
        myServiceEndpoint.setRegion(REGION);
        myServiceEndpoint.setName(NAME);
        myServiceEndpoint.setType(TYPE);

        handler = new RequestAuthorizationHandler(mockedAuthService, mockedCache, myServiceEndpoint, null, null);
    }

    @Unroll
    def "#desc auth should be bypassed if an x-roles header role matches within a configured list of service admin roles"() {
        given:
        mockedRequest.addHeader(CommonHttpHeader.AUTH_TOKEN.toString(), "abc")
        mockedRequest.addHeader(OpenStackServiceHeader.ROLES.toString(), ["role0", "role1", "role2"])
        ignoreTenantRoles.getIgnoreTenantRole() >> new ArrayList<String>()
        ignoreTenantRoles.getIgnoreTenantRole().add("role1")

        requestAuthorizationHandler = new RequestAuthorizationHandler(authenticationService, endpointListCache,
                serviceEndpoint, ignoreTenantRoles, delegable)

        when:
        def filterDirector = requestAuthorizationHandler.handleRequest(mockedRequest, null)

        then:
        filterDirector.getFilterAction() == FilterAction.PASS
        filterDirector.requestHeaderManager().headersToAdd().get(HeaderName.wrap(HttpDelegationHeaders.Delegated())) == null

        where:
        desc                   | delegable
        "With delegable on,"   | new DelegatingType().with { it.quality = 0.3; it }
        "With delegable off, " | null

    }

    @Unroll
    def "#desc bypassed if the x-roles header role does not match within a configured list of service admin roles"() {
        given:
        mockedRequest.addHeader(CommonHttpHeader.AUTH_TOKEN.toString(), "abc")
        mockedRequest.addHeader(OpenStackServiceHeader.ROLES.toString(), ["role0", "role2"])
        ignoreTenantRoles.getIgnoreTenantRole() >> new ArrayList<String>()
        ignoreTenantRoles.getIgnoreTenantRole().add("role1")

        requestAuthorizationHandler = new RequestAuthorizationHandler(authenticationService, endpointListCache,
                serviceEndpoint, ignoreTenantRoles, delegable)

        when:
        def filterDirector = requestAuthorizationHandler.handleRequest(mockedRequest, null)

        then:
        filterDirector.getFilterAction() == filterAction
        filterDirector.getResponseStatus() == responseStatus
        isDelegableHeaderAccurateFor delegable, filterDirector, serviceCatalogFailureMessage()

        where:
        desc                                         | filterAction        | responseStatus           | delegable
        "with delegable on, auth failures should be" | FilterAction.PASS   | HttpStatusCode.FORBIDDEN | new DelegatingType().with { it.quality = 0.3; it }
        "with delegable off, auth should not be"     | FilterAction.RETURN | HttpStatusCode.FORBIDDEN | null
    }

    @Unroll
    def "#desc reject requests without auth tokens"() {
        given:
        def requestAuthorizationHandler = new RequestAuthorizationHandler(mockedAuthService, mockedCache, serviceEndpoint, null, delegable);

        when:
        def director = requestAuthorizationHandler.handleRequest(mockedRequest, null);

        then:
        director.getFilterAction() == filterAction
        director.getResponseStatus() == responseStatus
        isDelegableHeaderAccurateFor delegable, director, authTokenNotFoundMessage()

        where:
        desc                                    | filterAction        | responseStatus              | delegable
        "When delegating is not set, it should" | FilterAction.RETURN | HttpStatusCode.UNAUTHORIZED | null
        "When delegating is set, it should not" | FilterAction.PASS   | HttpStatusCode.UNAUTHORIZED | new DelegatingType()
    }

    def "should Reject Unauthorized Requests"() {
        given:
        mockedRequest.addHeader(CommonHttpHeader.AUTH_TOKEN.toString(), UNAUTHORIZED_TOKEN)

        when:
        final FilterDirector director = handler.handleRequest(mockedRequest, null);

        then:
        assertEquals("Authorization component must return unauthorized requests", FilterAction.RETURN, director.getFilterAction());
        assertEquals("Authorization component must reject unauthorized requests with a 403", HttpStatusCode.FORBIDDEN, director.getResponseStatus());
    }

    def "should Pass Authorized Requests"() {
        given:
        mockedRequest.addHeader(CommonHttpHeader.AUTH_TOKEN.toString(), AUTHORIZED_TOKEN)

        when:
        final FilterDirector director = handler.handleRequest(mockedRequest, null);

        then:
        assertEquals("Authorization component must pass authorized requests", FilterAction.PASS, director.getFilterAction());
    }

    def "should Return 500"() {
        given:
        mockedRequest.addHeader(CommonHttpHeader.AUTH_TOKEN.toString(), AUTHORIZED_TOKEN)
        when(mockedAuthService.getEndpointsForToken(AUTHORIZED_TOKEN)).thenThrow(new RuntimeException("Service Exception"));

        when:
        final FilterDirector director = handler.handleRequest(mockedRequest, null);

        then:
        assertEquals("Authorization component must retrun 500 on service exception", HttpStatusCode.INTERNAL_SERVER_ERROR.intValue(), director.getResponseStatus().intValue());
    }

    def "should Cache Fresh Endpoint Lists"() {
        given:
        mockedRequest.addHeader(CommonHttpHeader.AUTH_TOKEN.toString(), AUTHORIZED_TOKEN)

        when:
        handler.handleRequest(mockedRequest, null);

        then:
        verify(mockedCache, times(1)).getCachedEndpointsForToken(AUTHORIZED_TOKEN) == null
        verify(mockedAuthService, times(1)).getEndpointsForToken(AUTHORIZED_TOKEN) == null
        verify(mockedCache, times(1)).cacheEndpointsForToken(eq(AUTHORIZED_TOKEN), any(List.class)) == null
    }

    def "should Use Cache For Cached Endpoint Lists"() {
        given:
        mockedRequest.addHeader(CommonHttpHeader.AUTH_TOKEN.toString(), CACHED_TOKEN)

        when:
        handler.handleRequest(mockedRequest, null);

        then:
        verify(mockedCache, times(1)).getCachedEndpointsForToken(CACHED_TOKEN) == null
        verify(mockedAuthService, never()).getEndpointsForToken(CACHED_TOKEN) == null
        verify(mockedCache, never()).cacheEndpointsForToken(eq(AUTHORIZED_TOKEN), any(List.class)) == null
    }

    void isDelegableHeaderAccurateFor(DelegatingType delegable, FilterDirector filterDirector, String message) {
        assert filterDirector.requestHeaderManager().headersToAdd().get(HeaderName.wrap(HttpDelegationHeaders.Delegated()))?.getAt(0) ==
                (delegable ? JavaDelegationManagerProxy.buildDelegationHeaders(filterDirector.getResponseStatusCode(),
                        "client-authorization", message, delegable.getQuality()).get(HttpDelegationHeaders.Delegated()).get(0) : null)
    }

    def String serviceCatalogFailureMessage() {
        "User token: abc: The user's service catalog does not contain an endpoint that matches the endpoint configured " +
                "in openstack-authorization.cfg.xml: \"null\".  User not authorized to access service."
    }

    def String authTokenNotFoundMessage() {
        "Authentication token not found in X-Auth-Token header. Rejecting request."
    }
}
