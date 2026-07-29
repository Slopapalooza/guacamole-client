/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * Service for operating on the inheritable default parameters of connection
 * groups via the extension REST API of the data source which stores them.
 */
angular.module('rest').factory('groupDefaultsService', ['$injector',
        function groupDefaultsService($injector) {

    // Required services
    var authenticationService = $injector.get('authenticationService');
    var cacheService          = $injector.get('cacheService');

    var service = {};

    /**
     * Returns the base URL of the extension resource exposing connection
     * group defaults within the given data source.
     *
     * @param {String} dataSource
     *     The identifier of the data source containing the connection group.
     *
     * @returns {String}
     *     The base URL of the group defaults resource.
     */
    var getBaseURL = function getBaseURL(dataSource) {
        return 'api/session/ext/' + encodeURIComponent(dataSource);
    };

    /**
     * Makes a request to the REST API to retrieve the default parameters
     * stored directly on the given connection group, returning a promise
     * which provides a map of parameter name to value.
     *
     * @param {String} dataSource
     *     The identifier of the data source containing the connection group.
     *
     * @param {String} groupIdentifier
     *     The identifier of the connection group.
     *
     * @returns {Promise.<Object.<String, String>>}
     *     A promise which resolves with the map of default parameter names
     *     to values stored on the given connection group.
     */
    service.getGroupDefaults = function getGroupDefaults(dataSource, groupIdentifier) {
        return authenticationService.request({
            cache  : cacheService.connections,
            method : 'GET',
            url    : getBaseURL(dataSource) + '/groupDefaults/'
                        + encodeURIComponent(groupIdentifier)
        });
    };

    /**
     * Makes a request to the REST API to replace the default parameters
     * stored on the given connection group. Parameters having empty values
     * are removed.
     *
     * @param {String} dataSource
     *     The identifier of the data source containing the connection group.
     *
     * @param {String} groupIdentifier
     *     The identifier of the connection group.
     *
     * @param {Object.<String, String>} defaults
     *     The default parameters to store, as a map of parameter name to
     *     value.
     *
     * @returns {Promise}
     *     A promise which resolves if the defaults were successfully stored.
     */
    service.setGroupDefaults = function setGroupDefaults(dataSource, groupIdentifier, defaults) {
        return authenticationService.request({
            method : 'PUT',
            url    : getBaseURL(dataSource) + '/groupDefaults/'
                        + encodeURIComponent(groupIdentifier),
            data   : defaults || {}
        })

        // Clear the cache, as effective values elsewhere may have changed
        .then(function defaultsStored() {
            cacheService.connections.removeAll();
        });
    };

    /**
     * Makes a request to the REST API to retrieve the defaults which the
     * given connection group inherits from its ancestors, returning a
     * promise which provides a map of parameter name to an object having
     * "value", "source", and "sourceName" properties.
     *
     * @param {String} dataSource
     *     The identifier of the data source containing the connection group.
     *
     * @param {String} groupIdentifier
     *     The identifier of the connection group.
     *
     * @returns {Promise.<Object.<String, Object>>}
     *     A promise which resolves with the map of inherited parameter names
     *     to values and their source groups.
     */
    service.getGroupInheritedDefaults = function getGroupInheritedDefaults(dataSource, groupIdentifier) {
        return authenticationService.request({
            cache  : cacheService.connections,
            method : 'GET',
            url    : getBaseURL(dataSource) + '/groupDefaults/'
                        + encodeURIComponent(groupIdentifier) + '/inherited'
        });
    };

    /**
     * Makes a request to the REST API to retrieve the default parameters
     * which the given connection inherits from its ancestor groups,
     * returning a promise which provides a map of parameter name to an
     * object having "value", "source", and "sourceName" properties. Values
     * stored on the connection itself are never included.
     *
     * @param {String} dataSource
     *     The identifier of the data source containing the connection.
     *
     * @param {String} connectionIdentifier
     *     The identifier of the connection.
     *
     * @returns {Promise.<Object.<String, Object>>}
     *     A promise which resolves with the map of inherited parameter names
     *     to values and their source groups.
     */
    service.getConnectionDefaults = function getConnectionDefaults(dataSource, connectionIdentifier) {
        return authenticationService.request({
            cache  : cacheService.connections,
            method : 'GET',
            url    : getBaseURL(dataSource) + '/connectionDefaults/'
                        + encodeURIComponent(connectionIdentifier)
        });
    };

    return service;

}]);
