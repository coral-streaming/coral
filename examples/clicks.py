import json
import requests

headers = {'Content-Type': 'application/vnd.api+json', 'Accept': 'application/vnd.api+json'}
api = 'http://localhost:8000'

def post(path, payload={}) :
    r = requests.post(api+path, data=json.dumps(payload), headers=headers)
    if (r.text):
        print json.dumps(json.loads(r.text), indent=2)

post('/api/actors/1/in', {"transitions": ["s009", "b001", "s005"]})