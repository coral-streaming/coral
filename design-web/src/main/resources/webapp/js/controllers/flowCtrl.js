'use strict';

app.controller('flowCtrl', function($scope, $modal, $location, $routeParams, filterFilter) {

    $scope.myData = 0

    //joint.layout.DirectedGraph.layout(graph, {'rankdir':'LR'});
    $scope.drawDiagram = function() {
        //graph.resetCells()
        $scope.myData += 1
    }
})