from httpMethods import *

# Create the graph (profiling tags)
post('/api/actors', {"type":"httpserver"})
post('/api/actors', {"type":"stats", "timeout":{"duration":60, "mode":"continue"}, "params":{"field": "amount"}, "group":{"by":"tag"}})
post('/api/actors', {"type":"zscore",    "params":{"by":"tag", "field": "amount","score" : 6.0}})

put('/api/actors/1',  {"input":{"trigger":{"in":{"type":"external"}}}})
put('/api/actors/2',  {"input":{"trigger":{"in":{"type":"actor", "source":1}}}})
put('/api/actors/3',  {"input":{"trigger":{"in":{"type":"actor", "source":1}},"collect":{"stats":{"type":"actor", "source":2}}}})
