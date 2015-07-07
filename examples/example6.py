from httpMethods import *

# Reads from the Kafka topic source JSON in the format {"temperature": degrees, "date": "thedate"} and sends JSON back to the Kafka topic destination if the temperature is 30 degrees or higher.
# Not usable with client.py because it reads the data from Kafka instead of HTTP
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"kafka-consumer", "params": {"topic": "source", "kafka": {"zookeeper.connect": "localhost:2181", "group.id": "uniqueid"}}}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"threshold", "params": {"key": "temperature", "threshold": 30}}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"json", "params": {"template": {"message": {"temperature": "${temperature}", "date": "${date}"}}}}}})
post('/api/actors', {"data": {"type": "actors", "attributes": {"type":"kafka-producer", "params": {"topic": "destination", "kafka": {"metadata.broker.list": "localhost:9092"}}}}})

patch('/api/actors/3',  {"data": {"type": "actors", "id": "3", "attributes": {"input":{"trigger":"2"}}}})
patch('/api/actors/4',  {"data": {"type": "actors", "id": "4", "attributes": {"input":{"trigger":"3"}}}})