---
layout: default
title: SampleActor
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

# SampleActor
The `SampleActor` (sample actor) is a [Coral Actor](/actors/overview/) that only emits a fraction of the supplied JSON trigger messages. The fraction can be specified by the user. The selection of messages to pass is random, but such that on average in the long term the fraction passed corresponds to the specified fraction.

## Creating a SampleActor
The SampleActor has `type: "sample"`. The `params` value is a JSON with two optional fields:

field  | type | required | description
:----- | :---- | :--- | :------------
`fraction` | float| yes | the fraction of messages to be passed (range 0 to 1).

<br>

#### Example
{% highlight json %}
{
  "data": {
    "type": "actors",
    "attributes": {
      "type": "sample",
        "params": { 
          "fraction": 0.125
      }
    }
  }
}
{% endhighlight %}

<br>

This will create a sample actor that passes 12.5% of all messages.

## Trigger
The `SampleActor` only does useful work if the trigger is connected.
The actor does nothing with the supplied JSON but just passes it through (or not).

## Emit
The `SampleActor` emits the received JSON with probability equal to the supplied fraction.

## State
The `SampleActor` does not keep state.

## Collect
The `SampleActor` does not collect state from other actors.

## Timer
The `SampleActor` does not provide timer actions.