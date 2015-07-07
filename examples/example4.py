from httpMethods import *

# FSM demo, not useable with client.py because it expects different events
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

patch('/api/actors/2',  {"data": {"type": "actors", "id": "2", "attributes": {"input":{"trigger": "1"}}}})