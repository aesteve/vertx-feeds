window.vertxFeeds = angular.module('vertxFeeds', []);

vertxFeeds.config(function($interpolateProvider){
    $interpolateProvider.startSymbol('{[{').endSymbol('}]}');
});