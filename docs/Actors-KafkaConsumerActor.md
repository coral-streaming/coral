---
layout: default
title: KafkaConsumerActor
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
The `KafkaConsumerActor` (Kafka consumer actor) is a [Coral Actor](/coral/docs/Overview-Actors.html) that listens to a Kafka topic and emits incoming Kafka messages as JSON messages.

## Creating a KafkaConsumerActor
The KafkaConsumerActor has `"type": "kafka-consumer"`. The `params` value is a JSON with the following fields:

field  | type | required | description
:----- | :---- | :--- | :------------
`topic` | string | yes| the Kafka topic to read
`kafka`:&nbsp;`group.id` | string | yes| the unique identifier to read a stream.
`kafka`:&nbsp;`zookeeper.connect` | string | yes| the ip address(es) of the [ZooKeeper](https://zookeeper.apache.org/) node(s) to connect to.

<br>

Other supplied properties will be interpreted as [Kafka consumer properties](https://kafka.apache.org/documentation.html#consumerconfigs), if the field matches a Kafka consumer property.

#### Example

{% highlight json %}
{
  "type": "kafka-consumer",
  "params": {
    "topic": "clickstream",
    "kafka": {
      "zookeeper.connect": "localhost:2181",
      "group.id": "mygroup"
    }
  }
}
{% endhighlight %}

<br>

The `group.id` is a unique identifier that Kafka uses to memorize the offset in the topic the actor listens to. For instance, if a Kafka consumer actor has listened to 100 messages from the start using `group.id` "mygroup", any other Kafka consumer actor with the same `group.id` will start from message 101. If another `group.id` is chosen, the offset is set to 0.

## Trigger
The `KafkaConsumerActor` has no trigger, but is triggered by incoming Kafka events on the topic the actor listens to.

## Emit
The `KafkaConsumerActor` emits Kafka messages as JSON objects. Message bodies of Kafka events are assumed to be in proper JSON format.

## State
The `KafkaConsumerActor` keeps no state.

## Collect
The `KafkaConsumerActor` does not collect state from other actors.

## Timer
The `KafkaConsumerActor` does not provide timer actions.