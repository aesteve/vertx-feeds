vertxFeeds.controller('LoginCtrl', ['$scope', '$http', function($scope, $http) {
    $scope.token = window.userToken;
	$scope.logout = function() {
		$http.post("/api/logout/?accessToken="+userToken)
		.success(function() {
            $scope.token = null;
		}).error(function(error){
			console.error(error);
		});
	};
}]);