---
layout: default
title: kafkaProducerActor
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

# KafkaProducerActor
The `KafkaProducerActor` is a [Coral Actor](/coral/docs/Overview-Actors.html) that can write to Kafka.

## Creating a KafkaProducerActor
The creation JSON of the KafkaProducerActor (see [Coral Actor](/coral/docs/Overview-Actors.html)) has `"type": "kafka-producer"`.
The `params` value is a JSON with the following fields:

field  | type | required | description
:----- | :---- | :--- | :------------
`topic` | String | yes| the name of the Kafka topic
`kafka` | JSON | yes | the configuration parameters for the Kafka producer

The properties of the kafka attribute should include at least the following:

field  | type | required | description
:----- | :---- | :--- | :------------
`metadata.broker.list` | string | yes| the brokers to use initially.

The producer type is configured as async. You can change this, but the KafkaProducerActor is designed for non-blocking
communication with Kafka.

Other properties may be supplied cf. the [Kafka producer properties](https://kafka.apache.org/documentation.html#producerconfigs).

#### Example
{% highlight json %}
{
  "data": {
    "type": "actors",
    "attributes": {
      "type": "kafka-producer",
      "params": {
        "topic": "test"
        "kafka" : {
          "metadata.broker.list": "broker1:9092,broker2:9092,broker3:9092"
        }
      }
    }
  }
}
{% endhighlight %}

## Trigger
The `KafkaProducerActor` only does useful work if the trigger is connected. The trigger JSON needs to contain a field `message`.
The value of this field is send to Kafka. The optional field `key` contains the key to use.

#### Example
{% highlight json %}
{
    "key": "somekey",
    "message": {
        "key1": "value1",
        "key2": "value2"
    }
}
{% endhighlight %}

## Emit
The `KafkaProducerActor` emits nothing.
Conceptually, the Kafka stream can be thought of as emit.

## State
The `KafkaProducerActor` keeps no state.

## Collect
The `KafkaProducerActor` does not collect state from other actors.