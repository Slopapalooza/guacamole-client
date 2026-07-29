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
 * Registers the additional field types provided by this application beyond
 * those defined by the standard schema.
 */
angular.module('form').config(['formServiceProvider',
    function formConfig(formServiceProvider) {

    /**
     * Field type presenting a boolean setting as three mutually-exclusive
     * buttons - unset, enabled, and disabled - for cases where an absent
     * value carries its own meaning and therefore cannot be conflated with
     * "disabled" as a checkbox would.
     */
    formServiceProvider.registerFieldType('GUAC_TRI_STATE', {
        module      : 'form',
        controller  : 'triStateFieldController',
        templateUrl : 'app/form/templates/triStateField.html'
    });

}]);
