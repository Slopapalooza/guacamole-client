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
 * Service for retrieving and applying the deployment's branding: the site
 * name and logo. Branding is readable without authentication, so it applies
 * to the login screen as well as the rest of the application.
 */
angular.module('rest').factory('brandingService', ['$injector',
        function brandingService($injector) {

    // Required services
    var $document = $injector.get('$document');
    var $http     = $injector.get('$http');
    var $q        = $injector.get('$q');

    var service = {};

    /**
     * The site name configured for this deployment, or null to use the
     * application's own name.
     *
     * @type String
     */
    service.siteName = null;

    /**
     * Whether a logo has been uploaded for this deployment.
     *
     * @type Boolean
     */
    service.hasLogo = false;

    /**
     * Applies the current branding to the document: the configured logo is
     * exposed as a custom property which the logo styles fall back from, and
     * the page title follows the site name.
     */
    var applyBranding = function applyBranding() {

        var root = $document[0].documentElement;

        if (service.hasLogo)
            // Cache-bust on each load so a replaced logo appears immediately
            root.style.setProperty('--pdg-logo',
                    'url("api/branding/logo?v=' + Date.now() + '")');
        else
            root.style.removeProperty('--pdg-logo');

    };

    /**
     * Retrieves the current branding, applying it once received.
     *
     * @returns {Promise}
     *     A promise which resolves once branding has been retrieved.
     */
    service.load = function load() {
        return $http({
            method : 'GET',
            url    : 'api/branding'
        })
        .then(function brandingRetrieved(response) {
            service.siteName = response.data.siteName || null;
            service.hasLogo  = !!response.data.hasLogo;
            applyBranding();
            return service;
        }, function brandingUnavailable() {
            // Branding is cosmetic; its absence must never block the interface
            return service;
        });
    };

    /**
     * Stores the given site name.
     *
     * @param {String} siteName
     *     The site name to store, or null to clear it.
     *
     * @returns {Promise}
     *     A promise which resolves once the name has been stored.
     */
    service.setSiteName = function setSiteName(siteName) {
        return $http({
            method : 'PUT',
            url    : 'api/branding',
            data   : { siteName : siteName }
        })
        .then(function stored() {
            service.siteName = siteName || null;
        });
    };

    /**
     * Uploads the given file as the logo.
     *
     * @param {File} file
     *     The image to upload.
     *
     * @returns {Promise}
     *     A promise which resolves once the logo has been stored.
     */
    service.setLogo = function setLogo(file) {

        var deferred = $q.defer();
        var reader = new FileReader();

        reader.onload = function fileRead() {
            $http({
                method  : 'POST',
                url     : 'api/branding/logo',
                data    : reader.result,
                headers : { 'Content-Type' : 'application/octet-stream' },
                transformRequest : angular.identity
            })
            .then(function stored() {
                service.hasLogo = true;
                applyBranding();
                deferred.resolve();
            }, deferred.reject);
        };

        reader.onerror = function fileFailed() {
            deferred.reject(reader.error);
        };

        reader.readAsArrayBuffer(file);
        return deferred.promise;

    };

    /**
     * Removes the stored logo, restoring the default.
     *
     * @returns {Promise}
     *     A promise which resolves once the logo has been removed.
     */
    service.removeLogo = function removeLogo() {
        return $http({
            method : 'DELETE',
            url    : 'api/branding/logo'
        })
        .then(function removed() {
            service.hasLogo = false;
            applyBranding();
        });
    };

    return service;

}]);
