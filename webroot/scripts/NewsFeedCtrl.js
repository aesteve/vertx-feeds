vertxFeeds.controller('NewsFeedCtrl', ['$scope', '$http', function($scope, $http) {
	var addFeedEntries = function (entries, subscription) {
		for (var i = 0; i < entries.length; i++) {
			entries[i].feed = subscription;
		}
		$scope.entries = entries.concat($scope.entries);
		console.log("Nb entries : " + $scope.entries.length);
	};
	var connectToEventBus = function () {
		var eb = new vertx.EventBus("http://localhost:9000/eventbus");
		eb.onopen = function(){
			for (var i = 0; i < $scope.subscriptions.length; i++) {
				var subscription = $scope.subscriptions[i];
				console.log("register reader for : " + subscription.hash + " on the event bus");
				eb.registerHandler(subscription.hash, function(entries){
					console.log(entries);
					addFeedEntries(entries, subscription);
				});
			}
		};
	};
	var getFeedEntries = function(feed, callback) {
		$http.get("/api/feeds/"+feed.hash+"/entries?accessToken="+userToken).then(function(data){
		data = data.data;
			addFeedEntries(data, feed);
			if (callback) {
				callback();
			}
		});
	};
	var fetchFeeds = function() {
		$http.get("/api/feeds?accessToken="+userToken).then(function(data){
			var data = data.data
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
	$scope.entries = [];
	fetchFeeds();
}]);