vertxFeeds.controller('ManageFeedsCtrl', ['$scope', '$http', function($scope, $http) {
	var fetchFeeds = function() {
		$http.get("/api/feeds?accessToken="+userToken).success(function(data){
			$scope.subscriptions = data;
		});
	};
	$scope.addSubscription = function() {
		$scope.pendingSubscription = {
			url:'http://your.feed.here',
			color:'#000000'
		};
	};
	$scope.cancelPending = function() {
		$scope.pendingSubscription = undefined;
	};
	$scope.savePending = function() {
		$http.post("/api/feeds?accessToken="+userToken, $scope.pendingSubscription)
		.success(function(data){
			fetchFeeds();
			$scope.pendingSubscription = undefined;
		});
		
	};
	$scope.unsubscribe = function(feed) {
		$http.delete("/api/feeds/"+feed.hash+"?accessToken="+userToken)
		.success(function() {
			fetchFeeds();
		}).error(function(error){
			console.error(error);
		});
	};
	fetchFeeds();
}]);