#!/usr/bin/python

import json
import requests
import time
import random

api = 'http://localhost:8000'
#api = 'http://natalinobusa-coral.herokuapp.com'
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

#create the graph
post('/api/actors', {"type":"rest"})
post('/api/actors', {"type":"histogram", "params":{"field": "amount"}, "group":{"by":"tag"}})
post('/api/actors', {"type":"zscore",    "params":{"by":"tag", "field": "amount","score" : 6.0}})
#post('/api/actors', {"type":"httpclient", "params":{"url":"http://localhost:8000/test"}})

put('/api/actors/1',  {"input":{"trigger":{"in":{"type":"external"}}}})
put('/api/actors/2',  {"input":{"trigger":{"in":{"type":"actor", "source":1}}}})
put('/api/actors/3',  {"input":{"trigger":{"in":{"type":"actor", "source":1}},"collect":{"histogram":{"type":"actor", "source":2}}}})
#put('/api/actors/4',  {"input":{"trigger":{"in":{"type":"actor", "source":3}}}})

