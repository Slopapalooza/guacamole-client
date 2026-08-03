/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  See the License for the specific language
 * governing permissions and limitations under the License.
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * A directive which invokes the given expression when a file is chosen,
 * exposing the chosen file as "file". AngularJS does not bind file inputs
 * natively, so this bridges the change event onto the scope.
 */
angular.module('settings').directive('picoFileSelect', ['$parse',
    function picoFileSelect($parse) {

    return {
        restrict: 'A',
        link: function link(scope, element, attrs) {

            var handler = $parse(attrs.picoFileSelect);

            element.on('change', function fileChosen(event) {
                var file = event.target.files && event.target.files[0];
                scope.$apply(function invoke() {
                    handler(scope, { file : file });
                });
                // Allow the same file to be chosen again
                event.target.value = '';
            });

        }
    };

}]);
