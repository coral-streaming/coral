---
layout: default
title: StatsActor
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

# StatsActor
The `StatsActor` (statistics actor) is a [Coral Actor](/actors/overview/) that gathers a single real valued field from its trigger JSON. It saves a number of statistics as state for this field.

## Creating a StatsActor
The StatsActor has `"type": "stats"`. The `params` value is a JSON with a single field:

field  | type | required | description
:----- | :---- | :--- | :------------
`field` | string | yes| the name of the field in the trigger-JSON to collect statistics for.

#### Example
{% highlight json %}
{
  "data": {
    "type": "actors",
    "attributes": {
      "type": "stats",
      "params": {
        "field": "amount"
      }
    }
  }
}
{% endhighlight %}
This will create a statistics component monitoring the field "amount". 

## Trigger
The `StatsActor` only does useful work if the trigger is connected.
The actor gathers values supplied by the field specified in the parameters.

## Emit
The `StatsActor` emits nothing. The state of the actor should be gathered by another actor through a *collect* procedure.

## State
The `StatsActor` keeps the following summary statistics as state:

field |type| description
:--- | :--- | :---
`count` | integer | number of values processed
`avg` | float | the average value
`sd` | float | the (population) standard deviation
`min` | float | the minimum value
`max` | float | the maximum value

<br>

The state is updated every time a value for the specified field is encountered in the trigger.
Note that when no events have occurred the count is zero, and other fields are undefined. This is represented as `null` in the JSON representation of the state.

## Collect
The `StatsActor` does not collect state from other actors.

## Timer
The `StatsActor` does not provide timer actions.