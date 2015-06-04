from httpMethods import *

post('/api/actors', {"type":"httpbroadcast"})
post('/api/actors', {"type":"linearregression", "params": {"intercept": 0.1, "weights":{"salary": 0.43, "age": 1.8 }}})

put('/api/actors/1',  {"input":{"trigger":{"in":{"type":"external"}}}})
put('/api/actors/2',  {"input":{"trigger":{"in":{"type":"actor", "source":1}}}})
