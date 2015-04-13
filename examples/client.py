#!/usr/bin/python

import json
import requests
import time
import random 

api = 'http://localhost:8000'
headers = {'content-type': 'application/json'}

generator = {
  "Amsterdam": {'mu':100, 'sigma':20},
  "Rotterdam": {'mu':20,  'sigma':20},
  "DenHaag":  {'mu':500,  'sigma':60},
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