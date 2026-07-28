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
 * A service which curates which connection parameters are "essential" (shown
 * by default when a guac-form uses the "essentials"/"advanced" layout modes)
 * and which advanced fields warrant extra attention when set.
 */
angular.module('form').factory('formLayoutService', ['$injector',
        function formLayoutService($injector) {

    // Required services
    var translationStringService = $injector.get('translationStringService');

    var service = {};

    /**
     * The names of the parameters curated as essential for each protocol,
     * keyed by the translation namespace of the protocol's forms. Fields
     * listed here are lifted out of their sections into the "essentials"
     * layout, in the order given; all remaining fields render as collapsed
     * sections in the "advanced" layout. Protocols absent from this map have
     * no essentials and render all sections in the "advanced" layout, with
     * common sections expanded by default.
     *
     * @type Object.<String, String[]>
     */
    var ESSENTIAL_FIELDS = {

        'PROTOCOL_RDP' : [
            'hostname',
            'port',
            'username',
            'password',
            'domain',
            'security',
            'ignore-cert',
            'resize-method',
            'secondary-monitors'
        ],

        'PROTOCOL_VNC' : [
            'hostname',
            'port',
            'username',
            'password'
        ],

        'PROTOCOL_SSH' : [
            'hostname',
            'port',
            'username',
            'password',
            'private-key',
            'passphrase'
        ]

    };

    /**
     * The canonicalized names of form sections which should be expanded by
     * default when no curated essential fields exist for the namespace being
     * rendered (the graceful-degradation path for uncurated protocols).
     *
     * @type Object.<String, Boolean>
     */
    var DEFAULT_EXPANDED_SECTIONS = {
        'NETWORK'        : true,
        'AUTHENTICATION' : true
    };

    /**
     * Pattern matching the names of fields whose values are sensitive enough
     * that a collapsed section containing a set value should be visually
     * flagged rather than reduced to a bare count.
     *
     * @type RegExp
     */
    var SENSITIVE_FIELD_PATTERN = /password|private-key|passphrase|disable-auth/;

    /**
     * Returns the ordered array of essential field names curated for the
     * given translation namespace, or null if no curation exists for that
     * namespace.
     *
     * @param {String} namespace
     *     The translation namespace of the forms being rendered, such as
     *     "PROTOCOL_RDP".
     *
     * @returns {String[]}
     *     The ordered array of essential field names, or null if the
     *     namespace has no curated essentials.
     */
    service.getEssentialFields = function getEssentialFields(namespace) {
        return ESSENTIAL_FIELDS[namespace] || null;
    };

    /**
     * Returns whether any essential fields are curated for the given
     * translation namespace.
     *
     * @param {String} namespace
     *     The translation namespace of the forms being rendered.
     *
     * @returns {Boolean}
     *     true if essential fields are curated for the given namespace,
     *     false otherwise.
     */
    service.hasEssentialFields = function hasEssentialFields(namespace) {
        return !!ESSENTIAL_FIELDS[namespace];
    };

    /**
     * Returns whether the given form section should be expanded by default
     * within the "advanced" layout. Sections are only expanded by default
     * when the namespace has no curated essentials (an uncurated protocol
     * would otherwise be entirely hidden behind collapsed sections).
     *
     * @param {String} namespace
     *     The translation namespace of the forms being rendered.
     *
     * @param {String} formName
     *     The name of the form section, as declared by the protocol or
     *     attribute schema.
     *
     * @returns {Boolean}
     *     true if the section should be expanded by default, false
     *     otherwise.
     */
    service.isExpandedByDefault = function isExpandedByDefault(namespace, formName) {

        if (service.hasEssentialFields(namespace) || !formName)
            return false;

        return !!DEFAULT_EXPANDED_SECTIONS[
            translationStringService.canonicalize(formName)];

    };

    /**
     * Returns whether the given field value is considered set (present and
     * non-empty) for the purpose of the collapsed-section badges.
     *
     * @param {String} value
     *     The field value to test.
     *
     * @returns {Boolean}
     *     true if the value is set, false otherwise.
     */
    service.isSet = function isSet(value) {
        return value !== undefined && value !== null && value !== '';
    };

    /**
     * Returns whether the field having the given name is sensitive, such
     * that a collapsed section containing a set value for it should be
     * flagged with a warning-styled badge.
     *
     * @param {String} name
     *     The name of the field to test.
     *
     * @returns {Boolean}
     *     true if the field is sensitive, false otherwise.
     */
    service.isSensitive = function isSensitive(name) {
        return SENSITIVE_FIELD_PATTERN.test(name || '');
    };

    return service;

}]);
