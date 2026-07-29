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
 * Controller for the tri-state toggle field type, which presents a boolean
 * setting as three mutually-exclusive buttons: unset, enabled, and disabled.
 * A plain checkbox cannot express "unset" as distinct from "disabled", which
 * matters wherever an absent value means something specific, such as
 * inheriting a value from elsewhere.
 */
angular.module('form').controller('triStateFieldController', ['$scope', '$injector',
    function triStateFieldController($scope, $injector) {

    // Required services
    var translationStringService = $injector.get('translationStringService');

    /**
     * The value representing the unset state.
     *
     * @type String
     */
    var UNSET = '';

    /**
     * The value representing the enabled state.
     *
     * @type String
     */
    var ENABLED = 'true';

    /**
     * The value representing the disabled state.
     *
     * @type String
     */
    var DISABLED = 'false';

    /**
     * The options presented by this field, in display order, each having a
     * value and the translation string naming it.
     *
     * @type Object[]
     */
    $scope.options = [];

    /**
     * Returns the translation string naming the given value of this field,
     * following the same convention as the options of an enumerated field so
     * that existing translations apply.
     *
     * @param {String} value
     *     The value to name.
     *
     * @returns {String}
     *     The translation string naming the given value.
     */
    var getOptionLabel = function getOptionLabel(value) {

        if (!$scope.field || !$scope.field.name)
            return '';

        return translationStringService.canonicalize($scope.namespace || 'MISSING_NAMESPACE')
                + '.FIELD_OPTION_' + translationStringService.canonicalize($scope.field.name)
                + '_'              + translationStringService.canonicalize(value || 'EMPTY');

    };

    /**
     * Returns whether the given value is the value currently selected.
     *
     * @param {String} value
     *     The value to test.
     *
     * @returns {Boolean}
     *     true if the given value is selected, false otherwise.
     */
    $scope.isSelected = function isSelected(value) {

        // An absent model is the unset state
        if (!$scope.model)
            return value === UNSET;

        return $scope.model === value;

    };

    /**
     * Selects the given value.
     *
     * @param {String} value
     *     The value to select.
     */
    $scope.select = function select(value) {
        $scope.model = value;
    };

    // Build the options once the field is known
    $scope.$watch('field', function fieldChanged() {
        $scope.options = [
            { value : UNSET,    label : getOptionLabel(UNSET)    },
            { value : ENABLED,  label : getOptionLabel(ENABLED)  },
            { value : DISABLED, label : getOptionLabel(DISABLED) }
        ];
    });

}]);
