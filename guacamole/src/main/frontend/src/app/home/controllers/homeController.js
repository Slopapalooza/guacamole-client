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
 * The controller for the home page.
 */
angular.module('home').controller('homeController', ['$scope', '$injector', 
        function homeController($scope, $injector) {

    // Get required types
    var ConnectionGroup  = $injector.get('ConnectionGroup');
    var GroupListItem    = $injector.get('GroupListItem');
            
    // Get required services
    var authenticationService  = $injector.get('authenticationService');
    var connectionGroupService = $injector.get('connectionGroupService');
    var dataSourceService      = $injector.get('dataSourceService');
    var preferenceService      = $injector.get('preferenceService');
    var requestService         = $injector.get('requestService');

    /**
     * Map of data source identifier to the root connection group of that data
     * source, or null if the connection group hierarchy has not yet been
     * loaded.
     *
     * @type Object.<String, ConnectionGroup>
     */
    $scope.rootConnectionGroups = null;

    /**
     * The current user preferences, used to control whether connection
     * groups within the list render expanded by default.
     *
     * @type Object.<String, Object>
     */
    $scope.preferences = preferenceService.preferences;

    /**
     * The protocol which connections must use to be listed, or null to list
     * connections of every protocol.
     *
     * @type String
     */
    $scope.protocolFilter = null;

    /**
     * The protocols used by at least one connection visible to the current
     * user, in alphabetical order, as offered by the protocol filter.
     *
     * @type String[]
     */
    $scope.availableProtocols = [];

    /**
     * Whether connections are listed in descending rather than ascending
     * order by name.
     *
     * @type Boolean
     */
    $scope.sortDescending = false;

    /**
     * How connections are presented: "list" for the tabular tree, or "grid"
     * for cards.
     *
     * @type String
     */
    $scope.viewMode = 'list';

    /**
     * The root connection groups with connections of other protocols removed,
     * as the source for the name filter. Identical to rootConnectionGroups
     * when no protocol filter is active.
     *
     * @type Object.<String, ConnectionGroup>
     */
    $scope.protocolFilteredGroups = null;

    /**
     * Recursively removes connections which do not use the given protocol
     * from the given connection group, along with any group left containing
     * nothing.
     *
     * @param {ConnectionGroup} group
     *     The connection group to prune. Modified in place.
     *
     * @param {String} protocol
     *     The protocol to retain.
     */
    var pruneToProtocol = function pruneToProtocol(group, protocol) {

        group.childConnections = (group.childConnections || []).filter(
            function isDesiredProtocol(connection) {
                return connection.protocol === protocol;
            });

        (group.childConnectionGroups || []).forEach(function pruneChild(child) {
            pruneToProtocol(child, protocol);
        });

        group.childConnectionGroups = (group.childConnectionGroups || []).filter(
            function isNotEmpty(child) {
                return child.childConnections.length
                    || child.childConnectionGroups.length;
            });

    };

    /**
     * Collects the distinct protocols used by connections within the given
     * connection group and its descendants.
     *
     * @param {ConnectionGroup} group
     *     The connection group to inspect.
     *
     * @param {Object.<String, Boolean>} protocols
     *     The set of protocols found so far, updated in place.
     */
    var collectProtocols = function collectProtocols(group, protocols) {

        (group.childConnections || []).forEach(function addProtocol(connection) {
            if (connection.protocol)
                protocols[connection.protocol] = true;
        });

        (group.childConnectionGroups || []).forEach(function inspectChild(child) {
            collectProtocols(child, protocols);
        });

    };

    /**
     * Applies the current protocol filter to the loaded connection groups,
     * producing the source for the name filter.
     */
    var applyProtocolFilter = function applyProtocolFilter() {

        if (!$scope.rootConnectionGroups)
            return;

        // Without a protocol filter the groups pass through untouched
        if (!$scope.protocolFilter) {
            $scope.protocolFilteredGroups = $scope.rootConnectionGroups;
            return;
        }

        var filtered = angular.copy($scope.rootConnectionGroups);
        angular.forEach(filtered, function pruneRoot(group) {
            pruneToProtocol(group, $scope.protocolFilter);
        });

        $scope.protocolFilteredGroups = filtered;

    };

    // Re-filter whenever the chosen protocol changes
    $scope.$watch('protocolFilter', applyProtocolFilter);

    /**
     * Selects the given protocol as the filter, or clears the filter if that
     * protocol is already selected.
     *
     * @param {String} protocol
     *     The protocol to filter by, or null to list all protocols.
     */
    $scope.setProtocolFilter = function setProtocolFilter(protocol) {
        $scope.protocolFilter = ($scope.protocolFilter === protocol) ? null : protocol;
    };

    /**
     * Reverses the order in which connections are listed.
     */
    $scope.toggleSortOrder = function toggleSortOrder() {
        $scope.sortDescending = !$scope.sortDescending;
    };

    /**
     * Switches between the tabular list and the card grid.
     */
    $scope.toggleViewMode = function toggleViewMode() {
        $scope.viewMode = ($scope.viewMode === 'grid') ? 'list' : 'grid';
    };

    /**
     * Array of all connection properties that are filterable.
     *
     * @type String[]
     */
    $scope.filteredConnectionProperties = [
        'name'
    ];

    /**
     * Array of all connection group properties that are filterable.
     *
     * @type String[]
     */
    $scope.filteredConnectionGroupProperties = [
        'name'
    ];
    
    /**
     * Returns whether the "Recent Connections" section should be displayed on
     * the home screen.
     * 
     * @returns {!boolean}
     *     true if recent connections should be displayed on the home screen,
     *     false otherwise.
     */
    $scope.isRecentConnectionsVisible = function isRecentConnectionsVisible() {
        return preferenceService.preferences.showRecentConnections;
    };

    /**
     * Returns whether critical data has completed being loaded.
     *
     * @returns {Boolean}
     *     true if enough data has been loaded for the user interface to be
     *     useful, false otherwise.
     */
    $scope.isLoaded = function isLoaded() {

        return $scope.rootConnectionGroups !== null;

    };

    // Retrieve root groups and all descendants
    dataSourceService.apply(
        connectionGroupService.getConnectionGroupTree,
        authenticationService.getAvailableDataSources(),
        ConnectionGroup.ROOT_IDENTIFIER
    )
    .then(function rootGroupsRetrieved(rootConnectionGroups) {
        $scope.rootConnectionGroups = rootConnectionGroups;

        // Offer a filter entry for each protocol actually in use
        var protocols = {};
        angular.forEach(rootConnectionGroups, function inspectRoot(group) {
            collectProtocols(group, protocols);
        });
        $scope.availableProtocols = Object.keys(protocols).sort();

        applyProtocolFilter();

    }, requestService.DIE);

}]);
