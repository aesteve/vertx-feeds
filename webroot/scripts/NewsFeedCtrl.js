vertxFeeds.controller('NewsFeedCtrl', ['$scope', '$http', function($scope, $http) {
	var getFeedEntries = function(feed) {
		$http.get("/api/feeds/"+feed.hash+"/entries?accessToken="+userToken).success(function(data){
			for (var i=0;i<data.length;i++) {
				data[i].feed = feed;
			}
			console.log("nb entries : "+data.length);
			$scope.entries = $scope.entries.concat(data);
		});
	};
	var fetchFeeds = function() {
		$http.get("/api/feeds?accessToken="+userToken).success(function(data){
			$scope.subscriptions = data;
			for (var i=0; i<data.length;i++) {
				getFeedEntries(data[i]);
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