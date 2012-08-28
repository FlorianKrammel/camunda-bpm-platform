'use strict';

/* Directives */

angular.module('cycle.directives', [])
.directive('cycleTree', function() {
	return {
		restrict: "A",
		replace: false,
		transclude: false,
		require: '?connector',
		scope: {
			'connector' : "=",
			'id' : "@"	
		},
		link: function(scope, element, attrs, model) {
			
			require(["dojo/ready", 
			         "dojo/_base/window", 
			         "dojo/store/Memory",
			         "dijit/tree/ObjectStoreModel", 
			         "dijit/Tree",
			         "dojo/store/Observable",
			         "dojo/request",
			         "dijit/registry"], function(ready, window, Memory, ObjectStoreModel, Tree, Observable, request, registry) {
				ready(function () {
					
					scope.$watch("connector", function (newValue , oldValue) {
				    	if (newValue != undefined && newValue != oldValue) {
				    		
							request.get(APP_ROOT+"secured/connector/" + newValue.connectorId + "/tree/root", {
					            handleAs: "json"
					        }).then(function(requestData){
								
								var memoryStore = new Memory({
							        data: requestData,
							        getChildren: function(object) {
							        	return request.get(APP_ROOT+"secured/connector/" + newValue.connectorId + "/tree/"+object.id+"/children", {
								            handleAs: "json"
								        }).then(function(childData){
								        	return childData;
								        });
							        }
							    });
								
								var observableStore = new Observable(memoryStore);
								
								// Create the model
							    var treeModel = new ObjectStoreModel({
							        store: observableStore,
							        query: {id: 'root'}
							    });
							    
							    var treeWidget = registry.byId(attrs.id);
							    if (treeWidget != undefined) {
							    	registry.byId(attrs.id).destroy();
	                                registry.remove(attrs.id);
							    }
							    
							    var tree = new Tree({
							      	id :  attrs.id,
							           model: treeModel,
							           openOnClick: true
							       });
							    tree.placeAt(element[0]);
							    tree.startup();
							},
							function(error){
								console.log("An error occurred: " + error);
								alert(error);
							});
				    	}
				    });
				});
			});
		}
	};
})
.directive('typeahead', function($http) {
  return {
    restrict: 'A',
    require: 'ngModel',
    scope: {
      values: '&'
    },
    link:  function(scope, element, attrs, ngModel) {
      var typeahead = element.typeahead({
        source: '[]',
        updater: function(item) {
          scope.$apply(read(item));
          return item;
        }
      });

      // update model with selected value
      function read(item) {
        ngModel.$modelValue = item;
      }

      $http.get('../../resources/diagram/modelerNames').success(function(data) {
        console.log(data);
        typeahead.data('typeahead').source = data;
      });
    }
  };
});