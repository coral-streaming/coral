from httpMethods import *

post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"httpbroadcast"}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"linearregression", "params": {"intercept": 0.1, "weights":{"salary": 0.43, "age": 1.8 }}}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type": "log", "params": {"file": "/tmp/coral.log"}}}})

patch('/api/actors/2',  {"data": {"type": "actors", "id": "2", "attributes": {"input":{"trigger": "1"}}}})
patch('/api/actors/3',  {"data": {"type": "actors", "id": "3", "attributes": {"input":{"trigger":"2"}}}})

