#!/usr/bin/python

from httpMethods import *

import json
import requests
import time
import random

post('/api/actors', { "type": "httpbroadcast"})
post('/api/actors', { "type": "stats", "params": { "field": "amount"}, "by": "city" })
post('/api/actors',
{ "type": "zscore",
  "params": { "by": "city", "field": "amount", "score": 1.0 },
  "collect": {
    "avg": {"value":3.5},
    "std": {"actor":4444, "field":"standard_dev" },
    "count": {actor:2, "field":"standard_dev", "group": {"by": "city" }}
    "city_xxx": { function("city") }
    "zipcode": { "actor": 42, field:"zipcode", select:"city_xxx" }}
    },
   "trigger": 1
})

post('/api/actors',
{ "type": "zscore",
  "params": { ? }, // everything not related to the function, eg timers, group by etc ...
  "trigger": 1,
  "input": {
    "value": { "trigger": "amount" },
    "score": { "value": 2 },
    "avg":   { "actor": 2, "by": "city" },
    "std":   { "actor": 2, "by": "city" },
    "count": { "actor": 2, "by": "city" }
  }
})

post('/api/actors',
{ "type": "",
  "params": { ? }, // everything not related to the function, eg timers, group by etc ...
  "trigger": 1,
  "input": {
    "const":{
      "score":2
    },
    "trigger":{
      "value": "amount",
    },
    "collect":{
      "avg":   {"actor": 2, "by": "city" },
      "std":   {"actor": 2, "by": "city" },
      "count": {"actor": 2, "by": "city" }
  }
})


post('/api/links', { "trigger": { "from": 1, "to": 2 }})
post('/api/links', { "trigger": { "from": 1, "to": 3 }})


generator = {
  "Amsterdam": {'mu':100, 'sigma':20},
  "Rotterdam": {'mu':20,  'sigma':20},
  "DenHaag":   {'mu':500, 'sigma':60},
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

post('/api/actors/1/in', randEvent())