from httpMethods import *

# Enrich data while streaming
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"httpbroadcast"}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"lookup", "params":{"key": "city", "function":"enrich", "lookup": { "amsterdam": {"geo":"aaa", "zip":"1010 AA"}, "rotterdam": {"geo":"bbb", "zip":"1010 AA"}} }}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"stats", "params":{"field": "amount"}, "group":{"by":"tag"}}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"zscore", "params":{"by":"tag", "field": "amount","score" : 6.0}}}})

patch('/api/actors/2',  {"data": {"type": "actors", "id": "2", "attributes": {"input":{"trigger":"1"}}}})
patch('/api/actors/3',  {"data": {"type": "actors", "id": "3", "attributes": {"input":{"trigger":"2"}}}})
patch('/api/actors/4',  {"data": {"type": "actors", "id": "4", "attributes": {"input":{"trigger":"2", "collect":{"stats":"3"}}}}})

