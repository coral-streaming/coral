---
layout: default
title: KafkaProducerActor
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
The `KafkaProducerActor` is a [Coral Actor](/coral/docs/Overview-Actors.html) that can write JSON objects to Kafka.

## Creating a KafkaProducerActor
The KafkaProducerActor has `"type": "kafka-producer"`. The `params` value is a JSON object with the following fields:

field  | type | required | description
:----- | :---- | :--- | :------------
`topic` | String | yes| the name of the Kafka topic
`kafka` | JSON | yes | the configuration parameters for the Kafka producer

<br>

The properties of the kafka attribute should include at least the following:

field  | type | required | description
:----- | :---- | :--- | :------------
`metadata.broker.list` | string | yes| the brokers to use initially.

<br>

Other supplied properties will be interpreted as [Kafka producer properties](https://kafka.apache.org/documentation.html#producerconfigs), if the field matches a Kafka consumer property.

#### Example
{% highlight json %}
{
  "type": "kafka-producer",
  "params": {
    "topic": "test",
      "kafka": {
        "metadata.broker.list": "broker1:9092,broker2:9092"
      }
    }
  }
}
{% endhighlight %}

## Trigger
The `KafkaProducerActor` is triggered by incoming JSON messages. 
The entire JSON object is sent to the Kafka topic specified in the constructor.

## Emit
The `KafkaProducerActor` emits nothing to other actors. Its only output is the message that it puts on Kafa.

## State
The `KafkaProducerActor` does not keep state.

## Collect
The `KafkaProducerActor` does not collect state from other actors.

## Timer
The `KafkaProducerActor` does not provide timer actions.