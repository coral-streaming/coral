---
layout: default
title: ThresholdActor
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

# ThresholdActor
The `ThresholdActor` (threshold actor) is a [Coral Actor](/actors/overview/) that emits the received JSON when for the specified field the associated value is at least the specified threshold.

## Creating a ThresholdActor
The ThresholdActor has `type: "threshold"`. The `params` value is a JSON with two fields:

field  | type | required | description
:----- | :---- | :--- | :------------
`key` | string | yes | the name of the threshold field in the trigger-JSON
`threshold` | number | yes | The value in the `key` field should be at least this before it is emitted.

#### Example
{% highlight json %}
{
  "type": "threshold",
  "params": {
    "key": "amount",
      "threshold": 100
  }
}
{% endhighlight %}
This will create a threshold actor for the field _amount_ which will only emit its trigger JSON if the "amount" field is equal to or larger than 100.

## Trigger
The `ThresholdActor` only does useful work if the trigger is connected.

## Emit
The `ThresholdActor` emits the received JSON when the value is at least the threshold. Otherwise, nothing is emitted.

## State
The `ThresholdActor` does not keep state.

## Collect
The `ThresholdActor` does not collect state from other actors.

## Timer
The `ThresholdActor` does not provide timer actions.