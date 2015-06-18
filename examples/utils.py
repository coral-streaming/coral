from httpMethods import *

# Create an actor
# adds obligatory json-api structure to actor specific parameters
# TODO: handle group by
def create_actor(type, params):
    return post('/api/actors',
                {"data":
                     {"type": "actors",
                      "attributes":
                          {"type": type,
                           "params": params}}})

# Create the generator actor
# TODO: modify generator actor to conform to other actors (format -> params)
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

# Connect an actor to the emit of another actor
# source: emitting actor
# target: actor triggered by source
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


