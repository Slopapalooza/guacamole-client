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
 * A directive which provides the persistent left navigation sidebar: product
 * identity, the current user, the main navigation pages, and logout. Nav
 * pages are sourced from userPageService, so extension-provided pages and the
 * user's actual permissions are reflected automatically.
 */
angular.module('navigation').directive('guacSidebar', [function guacSidebar() {

    return {
        restrict: 'E',
        replace: true,
        scope: {},

        templateUrl: 'app/navigation/templates/guacSidebar.html',
        controller: ['$scope', '$injector',
            function guacSidebarController($scope, $injector) {

            // Required types
            var User = $injector.get('User');

            // Required services
            var $location             = $injector.get('$location');
            var authenticationService = $injector.get('authenticationService');
            var requestService        = $injector.get('requestService');
            var userService           = $injector.get('userService');
            var userPageService       = $injector.get('userPageService');

            /**
             * The username of the current user.
             *
             * @type String
             */
            $scope.username = authenticationService.getCurrentUsername();

            /**
             * The current user's full name, if defined.
             *
             * @type String
             */
            $scope.fullName = null;

            /**
             * The role the current user has, if defined.
             *
             * @type String
             */
            $scope.role = null;

            /**
             * The main navigation pages available to the current user.
             *
             * @type Page[]
             */
            $scope.pages = null;

            /**
             * The URL of the current user's preferences page, which the
             * identity block links to.
             *
             * @type String
             */
            $scope.preferencesUrl = '#/settings/'
                    + encodeURIComponent(authenticationService.getDataSource())
                    + '/preferences';

            // Pull profile details for the identity block
            userService.getUser(authenticationService.getDataSource(), $scope.username)
                    .then(function userRetrieved(user) {
                $scope.fullName = user.attributes[User.Attributes.FULL_NAME];
                $scope.role = user.attributes[User.Attributes.ORGANIZATIONAL_ROLE];
            }, requestService.IGNORE);

            // Pull the navigable main pages
            userPageService.getMainPages()
            .then(function retrievedMainPages(pages) {
                $scope.pages = pages;
            });

            /**
             * Returns whether the given page is the one currently being
             * viewed, so it can be marked active in the navigation.
             *
             * @param {Page} page
             *     The page to test.
             *
             * @returns {Boolean}
             *     true if the given page is the current page, false otherwise.
             */
            $scope.isActive = function isActive(page) {

                var path = $location.path() || '/';

                // The home page ("/") is active only on an exact match; other
                // pages are active when the current path is within them
                if (page.url === '/')
                    return path === '/';

                return path === page.url || path.indexOf(page.url + '/') === 0;

            };

            /**
             * Returns the sidebar icon name to use for the given page, derived
             * from its className so extension pages fall back gracefully.
             *
             * @param {Page} page
             *     The page whose icon is wanted.
             *
             * @returns {String}
             *     A short icon key consumed by the template's icon set.
             */
            $scope.getIcon = function getIcon(page) {
                var name = (page.className || '') + ' ' + (page.name || '');
                if (/settings/i.test(name))    return 'settings';
                if (/session|history|record/i.test(name)) return 'history';
                return 'connections';
            };

            /**
             * Returns the label to display for the given navigation page. The
             * home page is presented as "Connections", since that is what it
             * lists; all other pages use their own name.
             *
             * @param {Page} page
             *     The page whose label is wanted.
             *
             * @returns {String}
             *     The translation key of the label to display.
             */
            $scope.getPageLabel = function getPageLabel(page) {
                return page.url === '/' ? 'SIDEBAR.NAV_CONNECTIONS' : page.name;
            };

            /**
             * Logs the current user out, returning to the login screen.
             */
            $scope.logout = function logout() {
                authenticationService.logout()
                ['catch'](requestService.IGNORE);
            };

            /**
             * Whether the current user authenticated anonymously (the sidebar
             * identity block is suppressed for anonymous users).
             *
             * @returns {Boolean}
             *     true if anonymous, false otherwise.
             */
            $scope.isAnonymous = function isAnonymous() {
                return authenticationService.isAnonymous();
            };

        }] // end controller

    };
}]);
