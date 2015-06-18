from utils import *

import time

# PIPELINE
# 4 - Read from Kafka
create_actor("kafka-consumer", {"topic": "demo_in", "kafka": {"zookeeper.connect": "localhost:2181", "group.id": "demo001"}})
# 5 - Check IP statically
create_actor("lookup", {"key": "ip", "lookup": {"0.0.0.0": {"result": "suspicious"}}, "function": "filter"})
# 6 - Prepare for kafka producer by with "message" envelope
create_actor("json", {"template": {"message": {"ip": "${ip}", "result": "${result}"}}})
# 7 - Send result to Kafka
create_actor("kafka-producer", {"topic": "demo_out", "kafka": {"metadata.broker.list": "localhost:9092"}})

time.sleep(1)

connect_actor(1, 2)
connect_actor(2, 3)
connect_actor(3, 4)
