from httpMethods import *

post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"httpbroadcast"}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {
    "type":"markovscore",
    "params":{
        "transitionProbs": [
            {"source": "s009", "destination": "s010", "prob": 0.1},
            {"source": "s009", "destination": "b002", "prob": 0.2},
            {"source": "s009", "destination": "b001", "prob": 0.4},
            {"source": "b001", "destination": "s003", "prob": 0.7},
            {"source": "s003", "destination": "b002", "prob": 0.1},
            {"source": "s003", "destination": "b004", "prob": 0.6},
            {"source": "b001", "destination": "s005", "prob": 0.1}
        ]
    }}}})

patch('/api/actors/2',  {"data": {"type": "actors", "id": "2", "attributes": {"input":{"trigger": "1"}}}})
