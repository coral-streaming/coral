from httpMethods import *

lookupTable = {
    "Amsterdam": {"geo":"aaa"},
    "Rotterdam": {"geo":"bbb"},
    "DenHaag":   {"geo":"ccc"},
    "Utrecht":   {"geo":"ddd"},
    "Eindhoven": {"geo":"eee"},
    "Arnhem":    {"geo":"fff"}
}

# Enrich data while streaming
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"httpbroadcast"}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"lookup", "params":{"key": "city", "function":"enrich", "lookup": lookupTable }}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"stats", "params":{"field": "amount"}, "group":{"by":"geo"}}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"zscore", "params":{"by":"geo", "field": "amount","score" : 6.0}}}})

patch('/api/actors/1',  {"data": {"type": "actors", "id": "1", "attributes": {"input":{"trigger":{"in":{"type":"external"}}}}}})
patch('/api/actors/2',  {"data": {"type": "actors", "id": "2", "attributes": {"input":{"trigger":{"in":{"type":"actor", "source":1}}}}}})
patch('/api/actors/3',  {"data": {"type": "actors", "id": "3", "attributes": {"input":{"trigger":{"in":{"type":"actor", "source":2}}}}}})
patch('/api/actors/4',  {"data": {"type": "actors", "id": "4", "attributes": {"input":{"trigger":{"in":{"type":"actor", "source":2}},"collect":{"stats":{"type":"actor", "source":3}}}}}})
