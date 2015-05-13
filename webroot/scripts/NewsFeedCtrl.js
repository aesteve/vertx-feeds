vertxFeeds.controller('NewsFeedCtrl', ['$scope', '$http', function($scope, $http) {
	var getFeedEntries = function(feed) {
		$http.get("/api/feeds/"+feed.hash+"/entries?accessToken="+userToken).success(function(data){
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
	$scope.entries = [];
	fetchFeeds();
}]);