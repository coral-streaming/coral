#!/usr/bin/python

import json
import requests

api = 'http://localhost:8000'
headers = {'content-type': 'application/json'}

def post(path, payload={}) :
    r = requests.post(api+path, data=json.dumps(payload), headers=headers)
    if (r.text):
        print json.dumps(json.loads(r.text), indent=2)

def put(path, payload={}) :
    r = requests.put(api+path, data=json.dumps(payload), headers=headers)
    if (r.text):
        print json.dumps(json.loads(r.text), indent=2)

def get(path) :
    r = requests.get(api+path, headers=headers)
    if (r.text):
        print json.dumps(json.loads(r.text), indent=2)

def delete(path) :
    r = requests.delete(api+path, headers=headers)
    if (r.text):
        print json.dumps(json.loads(r.text), indent=2)

post('/api/actors', {"type":"httpserver"})
post('/api/actors', {"type":"lookup", "params":{"key": "city", "function":"enrich", "lookup": { "amsterdam":
       {"geo":"aaa", "zip":"1010 AA"}, "rotterdam": {"geo":"bbb", "zip":"1010 AA"}} }})
post('/api/actors', {"type":"stats", "params":{"field": "amount"}, "group":{"by":"city"}})
post('/api/actors', {"type":"zscore",    "params":{"by":"city", "field": "amount", "score": 6.0}})

put('/api/actors/1',  {"input":{"trigger":{"in":{"type":"external"}}}})
put('/api/actors/2',  {"input":{"trigger":{"in":{"type":"actor", "source":1}}}})
put('/api/actors/3',  {"input":{"trigger":{"in":{"type":"actor", "source":2}}}})
put('/api/actors/4',  {"input":{"trigger":{"in":{"type":"actor", "source":2}},"collect":{"stats":{"type":"actor", "source":3}}}})