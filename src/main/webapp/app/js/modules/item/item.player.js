angular.module('ps.item.player', [
    'ngSanitize',
    'ngRoute',
    'device-detection',
    'com.2fdevs.videogular',
    'com.2fdevs.videogular.plugins.poster',
    'com.2fdevs.videogular.plugins.controls',
    'com.2fdevs.videogular.plugins.overlayplay',
    'com.2fdevs.videogular.plugins.buffering'
])
    .config(function($routeProvider) {
        $routeProvider.
            when('/podcast/:podcastId/item/:itemId/play', {
                templateUrl: 'html/item-player.html',
                controller: 'ItemPlayerController',
                controllerAs: 'ipc',
                resolve : {
                    item : function (itemService, $route) {
                        return itemService.findById($route.current.params.podcastId, $route.current.params.itemId);
                    },
                    podcast : function (podcastService, $route) {
                        return podcastService.findById($route.current.params.podcastId);
                    }
                }
            });
    })
    .controller('ItemPlayerController', function (podcast, item, $timeout, deviceDetectorService) {
        var vm = this;
        
        vm.item = item;
        vm.item.podcast = podcast;
        
        vm.config = {
            preload: true,
            sources: [
                { src : item.proxyURL, type : item.mimeType }
            ],
            theme: {
                url: "http://www.videogular.com/styles/themes/default/videogular.css"
            },
            plugins: {
                controls: {
                    autoHide: !deviceDetectorService.isTouchedDevice(),
                    autoHideTime: 2000
                },
                poster: item.cover.url
            }
        }

        vm.onPlayerReady = function(API) {
            if (vm.config.preload) {
                $timeout(function () {
                    API.play();
                })
            }
        };
    });