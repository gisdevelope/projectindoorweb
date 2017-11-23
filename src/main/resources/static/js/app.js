/**
 * @file Angular App for Project Indoor
 */

/**
 * ----------------------------------------------
 * Angular specific entries, like controllers etc.
 * ----------------------------------------------
 */

// definition of the angular app
var app = angular.module('IndoorApp', ['ngMaterial', 'ngRoute']);

// ------------- Page routing
app.config(['$routeProvider',

    function ($routeProvider) {
        $routeProvider
            .when('/map', {
                title: 'Map View',
                templateUrl: 'pages/map.html',
                controller: 'MapCtrl'
            })
            .when('/edit', {
                title: 'Edit',
                templateUrl: 'pages/edit.html',
                controller: 'LogImportCtrl'
            })
            .when('/import', {
                title: 'Import',
                templateUrl: 'pages/import.html',
                controller: 'LogImportCtrl'
            })
            .otherwise({
                redirectTo: '/map'
            });
    }]);


// ------------- Color definitions
app.config(function ($mdThemingProvider) {
    // palette for our purple color tone
    $mdThemingProvider.definePalette('inpurple', {
        '50': 'f4e3eb',
        '100': 'e3b9cd',
        '200': 'd08bab',
        '300': 'bd5c89',
        '400': 'af3970',
        '500': 'a11657',
        '600': '99134f',
        '700': '8f1046',
        '800': '850c3c',
        '900': '74062c',
        'A100': 'ffa3bd',
        'A200': 'ff7098',
        'A400': 'ff3d73',
        'A700': 'ff2461',
        'contrastDefaultColor': 'light',
        'contrastDarkColors': [
            '50',
            '100',
            '200',
            'A100',
            'A200'
        ],
        'contrastLightColors': [
            '300',
            '400',
            '500',
            '600',
            '700',
            '800',
            '900',
            'A400',
            'A700'
        ]
    });
    // palette for our blue color tone
    $mdThemingProvider.definePalette('inblue', {
        '50': 'e1e8eb',
        '100': 'b5c6ce',
        '200': '83a0ad',
        '300': '51798c',
        '400': '2c5d74',
        '500': '07405b',
        '600': '063a53',
        '700': '053249',
        '800': '042a40',
        '900': '021c2f',
        'A100': '68b3ff',
        'A200': '359aff',
        'A400': '0280ff',
        'A700': '0074e7',
        'contrastDefaultColor': 'light',
        'contrastDarkColors': [
            '50',
            '100',
            '200',
            'A100',
            'A200'
        ],
        'contrastLightColors': [
            '300',
            '400',
            '500',
            '600',
            '700',
            '800',
            '900',
            'A400',
            'A700'
        ]
    });
    $mdThemingProvider.theme('default')
        .primaryPalette('inpurple')
        .accentPalette('inblue');
    // add a palette variation for the toolbar
    var whiteMap = $mdThemingProvider.extendPalette('inpurple', {'500': '#ffffff', 'contrastDefaultColor': 'dark'});
    $mdThemingProvider.definePalette('inwhite', whiteMap);
});

// ------------- Application run
app.run(['$rootScope', '$route', function ($rootScope, $route) {
    $rootScope.$on('$routeChangeSuccess', function () {
        document.title = $route.current.title;
    });
}]);

// ------------- Controllers
// controller which handels page navigation
app.controller('NavToolbarCtrl', function ($scope, $timeout) {
    $scope.currentTitle = 'Map View'

    // change Toolbar title when route changes
    $scope.$on('$routeChangeSuccess', function (event, current) {
        $scope.currentTitle = current.title;
    });

})

// controller which handles map configuration
app.controller('MapSettingsCtrl', function ($scope, $timeout, $mdSidenav) {
    $scope.toggleLeft = buildToggler('left');
    $scope.toggleRight = buildToggler('right');

    function buildToggler(componentId) {
        return function () {
            $mdSidenav(componentId).toggle();
        };
    }
});

// controller which handles the map
function MapController($scope) {

    // map styles
    var styles = {
        'refPoint': new ol.style.Style({
            image: new ol.style.Circle({
                radius: 5,
                fill: new ol.style.Fill({color: '#a11657'}),
                stroke: new ol.style.Stroke({color: '#d3d3d3', width: 1})
            })
        }),
        'calcPoint': new ol.style.Style({
            image: new ol.style.Circle({
                radius: 10,
                fill: new ol.style.Fill({color: '#07405b'}),
                stroke: new ol.style.Stroke({color: '#d3d3d3', width: 1})
            })
        })
    };

    // map objects
    var map;


    //function to initialize the map
    $scope.initMap = function () {


        // map DOM element
        mapDiv = document.getElementById("map")

        var extent = [0, 0, 2560, 1536];
        var projection = new ol.proj.Projection({
            code: 'hft-image',
            units: 'pixels',
            extent: extent
        });

        map = new ol.Map({
            target: 'map',
            layers: [
                new ol.layer.Image({
                    source: new ol.source.ImageStatic({
                        url: '/maps/building_2_floor_3.png',
                        projection: projection,
                        imageExtent: extent
                    })
                })
            ],
            view: new ol.View({
                projection: projection,
                center: ol.extent.getCenter(extent),
                zoom: 2,
                maxZoom: 8
            })
        });

        // Test marker
        $scope.placeWaypoint(220, 350);

    };

    $scope.placeWaypoint = function (x, y) {
        var marker = new ol.Overlay({
            position: [x, y],
            positioning: 'center-center',
            element: document.getElementById('marker'),
            stopEvent: false
        });
        map.addOverlay(marker);
    }

    this.$afterViewInit = function () {
        console.log('Hi')
        //map.invalidateSize();
    };
}

app.controller('MapCtrl', MapController);

// controller which handles the building import view
function BuildingImportController($scope) {
    $scope.upload = function () {
        angular.element(document.querySelector('#inputFile')).click();
    };
}

app.controller('BuildingImportCtrl', BuildingImportController);

//Controller to fetch the building and floor data using GET method
function BuildingController($scope, $http) {
    $http({
        method : "GET",
        url : "config/config.json"
    }).then(function success(response) {
        $scope.buildings = response.data.buildings;
        $scope.floors = response.data.floors;
    }, function error(response) {
        $scope.buildings = response.statusText;
        $scope.floors = response.statusText;
    });
}

app.controller('BuildingCtrl', BuildingController);

/**
 * POST the uploaded log file
 * Custom directive to define ng-files attribute
 */
app.directive('ngFiles', ['$parse', function ($parse) {
    function filelink(scope, element, attrs) {
        var onChange = $parse(attrs.ngFiles);
        element.on('change', function (event) {
            onChange(scope, {$files: event.target.files});
        });
    };

    return {
        link: filelink
    }
}]);

// controller which handles the log import view
function LogImportController($scope, $http) {
    $scope.upload = function () {
        angular.element(document.querySelector('#inputFile')).click();
    };
    var formData = new FormData();

    $scope.getTheFiles = function ($files) {
        formData.append('fileName', $scope.fileName);
        formData.append('isTrainData', $scope.trainData ? $scope.trainData : false);
        formData.append('logFile', $files[0]);
        //console.log($files[0].name);
        $scope.fileUploaded = $files[0].name;
    };

    //Post the file and parameters
    $scope.uploadFiles = function () {
        var request = $http({
            method: 'POST',
            url: '/fileupload',
            data: formData,
            transformRequest: angular.identity,
            headers: {
                'Content-Type': undefined
            }
        }).success(function (data, status, headers, config) {
            //alert("success!");
        }).error(function (data, status, headers, config) {
            //alert("failed!");
        });
}

app.controller('LogImportCtrl', LogImportController);

/**
 * ----------------------------------------------
 * Non angular specific entries, like map initializing
 * ----------------------------------------------
 */