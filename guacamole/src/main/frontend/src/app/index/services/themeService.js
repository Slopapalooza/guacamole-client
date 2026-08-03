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
 * Service which applies the selected theme to the application by setting the
 * data-theme and data-mode attributes consulted by the bundled theme
 * stylesheets. Selection is stored as a user preference, so it is available
 * from local storage before authentication and therefore applies to the login
 * screen as well.
 */
angular.module('index').factory('themeService', ['$injector',
        function themeService($injector) {

    // Required services
    var $document         = $injector.get('$document');
    var preferenceService = $injector.get('preferenceService');

    var service = {};

    /**
     * The themes available for selection, in display order. Each has an
     * identifier matching the data-theme value used by the stylesheets and a
     * translation key naming it.
     *
     * @type Object[]
     */
    service.themes = [
        { id : 'slate', name : 'SETTINGS_PREFERENCES.NAME_THEME_SLATE' },
        { id : 'blue',  name : 'SETTINGS_PREFERENCES.NAME_THEME_BLUE'  },
        { id : 'green', name : 'SETTINGS_PREFERENCES.NAME_THEME_GREEN' },
        { id : 'red',   name : 'SETTINGS_PREFERENCES.NAME_THEME_RED'   },
        { id : 'grey',  name : 'SETTINGS_PREFERENCES.NAME_THEME_GREY'  }
    ];

    /**
     * The available color modes. "auto" follows the operating system's
     * light/dark setting via the prefers-color-scheme media query.
     *
     * @type Object[]
     */
    service.modes = [
        { id : 'light', name : 'SETTINGS_PREFERENCES.NAME_MODE_LIGHT' },
        { id : 'dark',  name : 'SETTINGS_PREFERENCES.NAME_MODE_DARK'  },
        { id : 'auto',  name : 'SETTINGS_PREFERENCES.NAME_MODE_AUTO'  }
    ];

    /**
     * Applies the currently-selected theme and mode to the document.
     */
    service.apply = function apply() {

        var root = $document[0].documentElement;
        var preferences = preferenceService.preferences;

        root.setAttribute('data-theme', preferences.theme || 'slate');
        root.setAttribute('data-mode', preferences.themeMode || 'light');

    };

    return service;

}]);
