'use strict';

app.directive('jointDiagram', function () {
        //explicitly creating a directive definition variable
        //this may look verbose but is good for clarification purposes
        //in real life you'd want to simply return the object {...}
        var directiveDefinitionObject = {
             //We restrict its use to an element
             //as usually  <bars-chart> is semantically
             //more understandable
             restrict: 'E',
             //this is important,
             //we don't want to overwrite our directive declaration
             //in the HTML mark-up
             replace: false,
             //our data source would be an array
             //passed thru chart-data attribute
             scope: {data: '=jointData'},
             link: function (scope, element, attrs) {
                 var graph = new joint.dia.Graph;

                 var paper = new joint.dia.Paper({
                     el: element,
                     width: 650, height: 200, gridSize: 1,
                     model: graph,
                     defaultLink: new joint.dia.Link({
                         router: { name: 'manhattan' },
                         attrs: {
                             '.connection': { stroke: '#428bca', 'stroke-width': 2 },
                             '.marker-target': { fill: '#428bca', d: 'M 10 0 L 0 5 L 10 10 z' }
                         }
                     }),
                     validateConnection: function(cellViewS, magnetS, cellViewT, magnetT, end, linkView) {
                         // Prevent linking from input ports.
                         if (magnetS && magnetS.getAttribute('type') === 'input') return false;
                         // Prevent linking from output ports to input ports within one element.
                         if (cellViewS === cellViewT) return false;
                         // Prevent linking to input ports.
                         return magnetT && magnetT.getAttribute('type') === 'input';
                     },
                     validateMagnet: function(cellView, magnet) {
                         // Note that this is the default behaviour. Just showing it here for reference.
                         // Disable linking interaction for magnets marked as passive (see below `.inPorts circle`).
                         return magnet.getAttribute('magnet') !== 'passive';
                     },
                     // Enable link snapping within 75px lookup radius
                     snapLinks: { radius: 75 }
                 });

                 var connect = function(source, sourcePort, target, targetPort) {
                     var link = new joint.shapes.devs.Link({
                         source: { id: source.id, selector: source.getPortSelector(sourcePort) },
                         target: { id: target.id, selector: target.getPortSelector(targetPort) },
                         router: { name: 'manhattan' },
                         attrs: {
                             '.connection': { stroke: '#428bca', 'stroke-width': 2 },
                             '.marker-target': { fill: '#428bca', d: 'M 10 0 L 0 5 L 10 10 z' }
                         }
                     });

                     link.addTo(graph);
                 };

                 var a1 = new joint.shapes.devs.Model({
                     position: { x: 50, y: 50 },
                     size: { width: 90, height: 90 },
                     inPorts: ['in'],
                     outPorts: ['out'],
                     attrs: {
                         '.label': { text: 'Rest Server', 'ref-x': .4, 'ref-y': .2 },
                         rect: { fill: '#ff6600' },
                         '.inPorts circle': { fill: '#16A085', magnet: 'passive', type: 'input' },
                         '.outPorts circle': { fill: '#E74C3C', type: 'output' }
                     }
                 });

                 var a2 = new joint.shapes.devs.Model({
                     position: { x: 250, y: 50 },
                     size: { width: 90, height: 90 },
                     inPorts: ['in', 'state'],
                     outPorts: [],
                     attrs: {
                         '.label': { text: 'Histogram', 'ref-x': .4, 'ref-y': .2 },
                         rect: { fill: '#ff6600' },
                         '.inPorts circle': { fill: '#16A085', magnet: 'passive', type: 'input' },
                         '.outPorts circle': { fill: '#E74C3C', type: 'output' }
                     }
                 });

                 var a3 = new joint.shapes.devs.Model({
                     position: { x: 450, y: 50 },
                     size: { width: 90, height: 90 },
                     inPorts: ['in','collect'],
                     outPorts: ['out'],
                     attrs: {
                         '.label': { text: 'Model', 'ref-x': .4, 'ref-y': .2 },
                         rect: { fill: '#ff6600' },
                         '.inPorts circle': { fill: '#16A085', magnet: 'passive', type: 'input' },
                         '.outPorts circle': { fill: '#E74C3C', type: 'output' }
                     }
                 });

                 graph.addCell(a1);
                 graph.addCell(a2);
                 graph.addCell(a3);

                 connect(a1,'out',  a2, 'in');
                 connect(a1,'out',  a3, 'in');
                 connect(a3,'collect', a2, 'state');
             }
        };
        return directiveDefinitionObject;
});
