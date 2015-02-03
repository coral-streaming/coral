'use strict';

app.factory("Tasks", function($resource) {
  return $resource("/api/tasks/:id", {}, {
    query: { method: "GET", isArray: false }
  });
});