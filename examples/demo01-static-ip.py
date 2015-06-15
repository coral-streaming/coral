from httpMethods import *


def create_actor(type, params):
    return post('/api/actors',
                {"data":
                     {"type": "actors",
                      "attributes":
                          {"type": type,
                           "params": params}}})


def create_generator(params, rate, times, delay=0):
    return post('/api/actors',
                {"data":
                     {"type": "actors",
                      "attributes":
                          {"type": "generator",
                           "format": params,
                           "timer":
                               {"rate": rate,
                                "times": times,
                                "delay": delay}}}})

def connect_actor(source, target):
    return patch('/api/actors/' + str(target),
                 {"data":
                      {"type": "actors",
                       "id": str(target),
                       "attributes":
                           {"input":
                                {"trigger":
                                     {"in":
                                          {"type": "actor",
                                           "source": source}}}}}})

# GENERATE DEMO DATA
# 1 - Generate fake ip data
create_generator({"ip": "['0.0.0.0', '0.0.0.1', '0.0.0.2', '0.0.0.3']"}, rate=100, times=10000, delay=1000)
# 2 - Prepare for kafka producer by with "message" envelope
create_actor("json", {"template": {"message": {"ip": "${ip}"}}})
# 3 - Send data to Kafka
create_actor("kafka-producer", {"topic": "demo_in", "kafka": {"metadata.broker.list": "localhost:9092"}})

# PIPELINE
# 4 - Read from Kafka
create_actor("kafka-consumer", {"topic": "demo_in", "kafka": {"zookeeper.connect": "localhost:2181", "group.id": "demo001"}})
# 5 - Check IP statically
create_actor("lookup", {"key": "ip", "lookup": {"0.0.0.0": {"result": "suspicious"}}, "function": "enrich"})
# 6 - Prepare for kafka producer by with "message" envelope
create_actor("json", {"template": {"message": {"ip": "${ip}", "result": "${result}"}}})
# 7 - Send result to Kafka
create_actor("kafka-producer", {"topic": "demo_out", "kafka": {"metadata.broker.list": "localhost:9092"}})

connect_actor(1, 2)
connect_actor(2, 3)
connect_actor(3, 4)
connect_actor(4, 5)
connect_actor(5, 6)
connect_actor(6, 7)
