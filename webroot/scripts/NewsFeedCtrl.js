vertxFeeds.controller('NewsFeedCtrl', ['$scope', '$http', function($scope, $http) {
	var addFeedEntries = function (entries, subscription) {
		for (var i = 0; i < entries.length; i++) {
			entries[i].feed = subscription;
		}
		$scope.entries = entries.concat($scope.entries);
		$scope.$apply();
	};
	var connectToEventBus = function () {
		var eb = new vertx.EventBus("http://localhost:9000/eventbus");
		eb.onopen = function(){
			for (var i = 0; i < $scope.subscriptions.length; i++) {
				var subscription = $scope.subscriptions[i];
				console.log("register reader for : " + subscription.hash + " on the event bus");
				eb.registerHandler(subscription.hash, function(entries){
					addFeedEntries(entries, subscription);
				});
			}
		};
	};
	var getFeedEntries = function(feed, callback) {
		$http.get("/api/feeds/"+feed.hash+"/entries?accessToken="+userToken).success(function(data){
			for (var i=0;i<data.length;i++) {
				data[i].feed = feed;
			}
			console.log("nb entries : "+data.length);
			$scope.entries = $scope.entries.concat(data);
			if (callback) {
				callback();
			}
		});
	};
	var fetchFeeds = function() {
		$http.get("/api/feeds?accessToken="+userToken).success(function(data){
			$scope.subscriptions = data;
			for (var i=0; i<data.length; i++) {
				var callback = undefined;
				if (i == data.length -1) {
					callback = connectToEventBus;
				}
				getFeedEntries(data[i], callback);
			}
		});
	};
	$scope.feedEntryStyle = function(entry) {
		var color = entry.feed.color;
		var style = "border-color:"+color+";";
		return style;
	};
	$scope.entries = [];
	fetchFeeds();
}]);