from httpMethods import *

# FSM demo
table = {
    "sleep": {
        "tired":"sleep",
        "hungry":"eat",
        "bored":"freak"
    },
    "eat":{
        "tired":"sleep",
        "hungry":"eat",
        "bored":"freak"
    },
    "freak":{
        "tired":"sleep",
        "hungry":"eat",
        "bored":"freak"
    }
}

post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"httpbroadcast"}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"fsm", "params":{"key":"mood", "table": table, "s0":"sleep"}}}})

put('/api/actors/1',  {"input":{"trigger":{"in":{"type":"external"}}}})
put('/api/actors/2',  {"input":{"trigger":{"in":{"type":"actor", "source":1}}}})