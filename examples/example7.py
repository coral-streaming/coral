from httpMethods import *

# Filter to exclude Arnhem
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"httpbroadcast"}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"filter", "params": {"filters": [{"type": "startswith", "function": "exclude", "field": "city", "param": "Arnhem"}]}}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"stats", "params":{"field": "amount"}, "group":{"by":"city"}}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"zscore", "params":{"by":"city", "field": "amount","score" : 1.0}}}})

patch('/api/actors/2',  {"data": {"type": "actors", "id": "2", "attributes": {"input":{"trigger":"1"}}}})
patch('/api/actors/3',  {"data": {"type": "actors", "id": "3", "attributes": {"input":{"trigger":"2"}}}})
patch('/api/actors/4',  {"data": {"type": "actors", "id": "4", "attributes": {"input":{"trigger":"2", "collect":{"stats":"3"}}}}})