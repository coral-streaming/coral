'use strict';

app.controller('todoCtrl', function ($scope, $modal, $http, $log) {

  $scope.list = function() {
    $http.get('/api/tasks').success(function(data) {
      $scope.items  = data.tasks;
    });
  }

  function create(item) {
    $http({
      url:"/api/tasks",
      method: "POST",
      data: item,
      headers: {'Content-Type': 'application/json'}
    }).success(function (data, status, headers, config) {
      $scope.items.push(data);
    }).error(function (data, status, headers, config) {
      $scope.status = status;
    });
  };

  function edit(id, item) {
    $http({
      url:"/api/tasks/"+id,
      method: "PUT",
      data: item,
      headers: {'Content-Type': 'application/json'}
    }).success(function (data, status, headers, config) {
      $scope.items.push(data);
    }).error(function (data, status, headers, config) {
      $scope.status = status;
    });
  };

  $scope.list()

  $scope.openNew = function () {

    var modalInstance = $modal.open({
      templateUrl: 'newTask.html',
      controller: 'ModalTaskCtrl',
      resolve: {
        item: function () {
          return "{\"desc\":\"...\"}";
        },
        id:0
      }
    });

    modalInstance.result.then(function (item) {
      create(item)
    }, function () {
      $log.info('Modal dismissed at: ' + new Date());
    });
  };

  $scope.openEdit = function (id) {

    var modalInstance = $modal.open({
      templateUrl: 'editTask.html',
      controller: 'ModalTaskCtrl',
      resolve: {
        item: function () {
          return "{\"desc\":\"...\"}";
        },
        id: id
      }
    });

    modalInstance.result.then(function (item) {
      edit(item)
    }, function () {
      $log.info('Modal dismissed at: ' + new Date());
    });
  };

});

app.controller('ModalTaskCtrl', function ($scope, $log, $modalInstance, item, id) {
  $scope.item = item

  // todo: act before closing the modal, move resource crud in the modal controller
  $scope.create = function () {
    $modalInstance.close($scope.item);
  };

  $scope.update = function () {
    $modalInstance.close($scope.item);
  };

  $scope.delete = function () {
    $modalInstance.close($scope.item);
  };

  $scope.cancel = function () {
    $modalInstance.dismiss('cancel');
  };
});