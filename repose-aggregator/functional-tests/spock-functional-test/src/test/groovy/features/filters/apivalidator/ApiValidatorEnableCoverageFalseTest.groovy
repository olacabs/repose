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
package features.filters.apivalidator

import framework.ReposeValveTest
import framework.category.Slow
import org.junit.experimental.categories.Category
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.MessageChain
import spock.lang.Unroll

/**
 * Created by jennyvo on 6/12/14.
 */
@Category(Slow.class)
class ApiValidatorEnableCoverageFalseTest extends ReposeValveTest {
    String intrumentedHandler = '\"com.rackspace.com.papi.components.checker.handler\":*'

    def setupSpec() {
        deproxy = new Deproxy()
        deproxy.addEndpoint(properties.targetPort)

        def params = properties.getDefaultTemplateParams()
        repose.configurationProvider.applyConfigs("common", params)
        repose.configurationProvider.applyConfigs("features/filters/apivalidator/common", params)
        repose.configurationProvider.applyConfigs("features/filters/apivalidator/statemachine/enableapicoveragefalse", params)
        repose.start()

        repose.waitForNon500FromUrl(reposeEndpoint)
    }

    def cleanupSpec() {
        if (repose)
            repose.stop()
        if (deproxy)
            deproxy.shutdown()
    }
    /*
        When enable-api-coverage is set to false, enable-rax-role is set to true,
        certain user roles will allow to access certain methods according to config in the wadl.
        i.e. 'GET' method only be available to access by a:observer and a:admin role
    */

    @Unroll("enableapicoverage false:headers=#headers")
    def "when enable-api-coverage is false, validate count at state level"() {
        given:
        File outputfile = new File("output.dot")
        if (outputfile.exists())
            outputfile.delete()
        MessageChain messageChain

        when:
        messageChain = deproxy.makeRequest(url: reposeEndpoint + "/a", method: method, headers: headers)

        def getBeanObj = repose.jmx.quickMBeanNames(intrumentedHandler)

        def validatorBeanDomain = '\"com.rackspace.com.papi.components.checker\":*'
        def checkstrscope = 'scope=\"raxRolesEnabled_'
        def check = false

        def getMBeanObj = repose.jmx.getMBeanNames(validatorBeanDomain)

        def strIt = getMBeanObj[0].toString()
        //println(strIt)
        if (strIt.contains(checkstrscope))
            check = true

        then:
        messageChain.getReceivedResponse().getCode().equals(responseCode)
        getBeanObj.size() == 0      // not using handler
        check == true

        where:
        method | headers                                           | responseCode
        "GET"  | ["x-roles": "raxRolesEnabled, a:observer"]        | "200"
        "GET"  | ["x-roles": "raxRolesEnabled, a:observer, a:bar"] | "200"
        "GET"  | ["x-roles": "raxRolesEnabled, a:bar, a:admin"]    | "200"
        "GET"  | ["x-roles": "raxRolesEnabled, a:admin"]           | "200"
        "GET"  | ["x-roles": "raxRolesEnabled"]                    | "404"
        "GET"  | ["x-roles": "raxRolesEnabled, a:creator"]         | "404"
        "GET"  | null                                              | "403"
    }
}
