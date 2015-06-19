---
layout: default
title: KafkaConsumerActor
topic: Actors
---
<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

# KafkaConsumerActor
The `KafkaConsumerActor` (Kafka consumer actor) is a [Coral Actor](/coral/docs/Overview-Actors.html) that can emits incoming Kafka messages as JSON message.

## Creating a KafkaConsumerActor
The creation JSON of the KafkaConsumerActor (see [Coral Actor](/coral/docs/Overview-Actors.html)) has `"type": "kafka-consumer"`.
The `params` value is a JSON with the following field:

field  | type | required | description
:----- | :---- | :--- | :------------
`topic` | string | yes| the Kafka bus topic to read
`kafka` | map | yes| a set of kafka properties for the consumer

The properties of the kafka attribute should include at least the following:

field  | type | required | description
:----- | :---- | :--- | :------------
`group.id` | string | yes| the unique identifier to read a stream (do not use the same name simultaneously for different instances)
`zookeeper.connect` | string | yes| the ip address(es) of the zookeeper node(s) to connect to

Other properties may be supplied cf. the [Kafka consumer properties](https://kafka.apache.org/documentation.html#consumerconfigs).

#### Example

{% highlight json %}
{
  "data": {
    "type": "actors",
    "attributes": {
      "type": "json",
      "params": {
        "topic": "clickstream",
        "kafka": {
          "zookeeper.connect": "localhost:2181",
          "group.id": "mygroup"
        }
      }
    }
  }
}
{% endhighlight %}

## Trigger
The `KafkaConsumerActor` has no trigger.
Conceptually, the Kafka stream can be thought of as trigger.

## Emit
The `KafkaConsumerActor` emits Kafka messages as JSON.
Message bodies are assumed to be in proper JSON format.

## State
The `KafkaConsumerActor` keeps no state.

## Collect
The `KafkaConsumerActor` does not collect state from other actors.