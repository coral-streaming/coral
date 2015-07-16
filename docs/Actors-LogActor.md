---
layout: default
title: LogActor
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

# LogActor
The `LogActor` (log actor) is a [Coral Actor](/actors/overview/) that logs the received trigger JSON. The actor isn't meant for production use, but for usage during testing the pipeline.
In production, the pipeline should send the results to Kafka using the [KafkaProducerActor](/coral/docs/Actors-KafkaProducerActor.html).

## Creating a LogActor
The creation JSON of the log actor (see [Coral Actor](/actors/overview/)) has `type: "log"`.
The `params` value has the following fields:

field  | type |    | description
:----- | :---- | :--- | :------------
`file`   | string  | required | location of the file to log to.
`append` | boolean | optional | when true, the actor appends to a possibily existing file, otherwise a possibly existing the file is overwritten.

#### Example
{% highlight json %}
{
  "data": {
    "type": "actors",
    "attributes": {
      "type": "log",
      "params": {
        "file": "/tmp/coral.log",
        "append": true
      }
    }
  }
}
{% endhighlight %}
This will create a log actor that appends the received trigger JSON to the file `/tmp/coral.log`.

## Trigger
The `LogActor` only does useful work if the trigger is connected.

## Emit
The `LogActor` emits nothing. Conceptually, the writing to the output file can be thought of as emit.

## State
The `LogActor` doesn't keep state.

## Collect
The `LogActor` does not collect state from other actors.

## Timer
The `LogActor` does not provide timer actions.