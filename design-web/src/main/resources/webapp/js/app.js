'use strict';

// Declare app level module which depends on filters, and services
var app= angular.module('coralApp', ['ngRoute', 'ui.bootstrap']);

app.config(['$routeProvider', function($routeProvider) {
  $routeProvider.when('/flows', {templateUrl: 'html/views/flow.html', controller: 'flowCtrl'});
  $routeProvider.when('/todo', {templateUrl: 'html/views/todo.html', controller: 'todoCtrl'});
  $routeProvider.otherwise({redirectTo: '/flows'});
}]);
