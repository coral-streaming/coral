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


generator = {
    "Amsterdam": {'mu':100, 'sigma':20},
    "Rotterdam": {'mu':20,  'sigma':20},
    "Utrecht":   {'mu':40,  'sigma':20},
    "Eindhoven": {'mu':100, 'sigma':10},
    "Arnhem":    {'mu':200, 'sigma':50}
}

def randEvent() :
    account = random.randrange(1000)
    city = random.choice(generator.keys())
    amount = random.gauss(generator[city]['mu'],generator[city]['sigma'])
    event = {'account':'NL'+str(account), 'amount':amount, 'city':city }
    return event

#create the graph
post('/api/actors', {"type":"rest"})
post('/api/actors', {"type":"histogram", "params":{"field": "amount"}, "group":{"by":"city"}})
post('/api/actors', {"type":"zscore",    "params":{"by":"city", "field": "amount","score" : 2.0}})

put('/api/actors/1',  {"input":{"trigger":{"in":{"type":"external"}}}})
put('/api/actors/2',  {"input":{"trigger":{"in":{"type":"actor", "source":1}}}})
put('/api/actors/3',  {"input":{"trigger":{"in":{"type":"actor", "source":1}},"collect":{"histogram":{"type":"actor", "source":2}}}})

