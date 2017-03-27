window.vertxFeeds = angular.module('vertxFeeds', ['ngMaterial', 'mdColorPicker']);

vertxFeeds.config(function($interpolateProvider, $mdThemingProvider){
    $mdThemingProvider.theme('default')
        .primaryPalette('blue')
        .accentPalette('orange');
    $interpolateProvider.startSymbol('{[{').endSymbol('}]}');
});