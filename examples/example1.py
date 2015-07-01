from httpMethods import *

# Create the graph (profiling cities)
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"httpbroadcast"}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"stats", "params":{"field": "amount"}, "group":{"by":"city"}}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"zscore", "params":{"by":"city", "field": "amount","score" : 1.0}}}})

patch('/api/actors/2',  {"data": {"type": "actors", "id": "2", "attributes": {"input":{"trigger":"1"}}}})
patch('/api/actors/3',  {"data": {"type": "actors", "id": "3", "attributes": {"input":{"trigger":"1"}},"collect":{"stats":"2"}}})