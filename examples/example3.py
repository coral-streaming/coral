from httpMethods import *

# Enrich data while streaming
post('/api/actors', {"type": "actors", "attributes": {"type":"httpbroadcast"}})
post('/api/actors', {"type": "actors", "attributes": {"type":"lookup", "params":{"key": "city", "function":"enrich", "lookup": { "amsterdam": {"geo":"aaa", "zip":"1010 AA"}, "rotterdam": {"geo":"bbb", "zip":"1010 AA"}} }}})
post('/api/actors', {"type": "actors", "attributes": {"type":"stats", "params":{"field": "amount"}, "group":{"by":"tag"}}})
post('/api/actors', {"type": "actors", "attributes": {"type":"zscore", "params":{"by":"tag", "field": "amount","score" : 6.0}}})

put('/api/actors/1',  {"input":{"trigger":{"in":{"type":"external"}}}})
put('/api/actors/2',  {"input":{"trigger":{"in":{"type":"actor", "source":1}}}})
put('/api/actors/3',  {"input":{"trigger":{"in":{"type":"actor", "source":2}}}})
put('/api/actors/4',  {"input":{"trigger":{"in":{"type":"actor", "source":2}},"collect":{"stats":{"type":"actor", "source":3}}}})
