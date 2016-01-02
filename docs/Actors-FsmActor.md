---
layout: default
title: FsmActor
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

# FsmActor
The `FsmActor` (finite state machine actor) is a [Coral Actor](/actors/overview/) that keeps state that can be changed due to trigger events.

## Creating a FsmActor
The FsmActor has `"type": "fsm"`. The `params` value is a JSON object with the following fields:

field  | type | required | description
:----- | :---- | :--- | :------------
`key` | string | yes| the name of the field in the trigger-JSON that can affect the state
`table` | map | yes| a map of maps of state transitions
`s0` | string | yes| the initial state

<br>

#### Example
{% highlight json %}
{
  "type": "fsm",
  "params": {
    "key": "transactionsize",
    "table": {
      "normal": {
        "small": "normal",
        "large": "normal",
        "x-large": "suspicious"
      }, "suspicious": {
        "small": "normal",
        "large": "suspicious",
        "x-large": "alarm"
      }, "alarm": {
        "small": "suspicious",
        "large": "alarm",
        "x-large": "alarm"
      }
    }, "s0": "normal"
  }
}
{% endhighlight %}
Note that the initial state _s0_ must be a valid key in the table.

## Trigger
The `FsmActor` only does useful work if the trigger is connected.
The actor checks for the key field and changes state if appropriate.

<br>

#### Example
Taking the example constructor above, the initial state is `normal`. Now if a trigger JSON contains `"transactionsize": "x-large"` then the state will change to `suspicious`.
Another trigger with the same `"transactionsize": "x-large"` will now change to state to `alarm`.

## Emit
The `FsmActor` emits nothing, the state must be queried using a *collect* through another actor.

## State
The `FsmActor` keeps the FSM state in a map as coral actor state:

field |type| description
:--- | :--- | :---
`s` | string| the (FSM) state label

<br>

The state is updated according to the table due to trigger events.

## Collect
The `FsmActor` does not collect state from other actors.

## Timer
The `FsmActor` does not provide timer actions.