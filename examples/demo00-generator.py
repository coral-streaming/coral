from utils import *

import time

# GENERATE DEMO DATA
# 1 - Generate fake ip data
create_generator({"ip": "['0.0.0.0', '0.0.0.1', '0.0.0.2', '0.0.0.3']"}, rate=3, times=10, delay=1000)
# 2 - Prepare for kafka producer by with "message" envelope
create_actor("json", {"template": {"message": {"ip": "${ip}"}}})
# 3 - Send data to Kafka
create_actor("kafka-producer", {"topic": "demo_in", "kafka": {"metadata.broker.list": "localhost:9092"}})

time.sleep(1)

connect_actor(1, 2)
connect_actor(2, 3)
connect_actor(3, 4)
