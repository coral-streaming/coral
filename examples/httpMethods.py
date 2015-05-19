import json
import requests

api = 'http://localhost:8000'
headers = {'Content-Type': 'application/json', 'Accept': 'application/vnd.api+json'}

def post(path, payload={}) :
    r = requests.post(api+path, data=json.dumps(payload), headers=headers)
    if (r.text):
        print json.dumps(json.loads(r.text), indent=2)

def patch(path, payload={}) :
    r = requests.patch(api+path, data=json.dumps(payload), headers=headers)
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
