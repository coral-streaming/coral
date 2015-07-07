---
layout: default
title: ZscoreActor
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

# ZscoreActor
The `ZscoreActor` (Z-score actor) is a [Coral Actor](/actors/overview/) that determines whether or not a value from an event is an outlier.
The statistics actor state is used for the average and standard deviation of the measurement value.

## Creating the actor
The creation JSON of the z-score actor (see [Coral Actor](/actors/overview/)) has `type: "zscore"`.
The `params` value is a JSON with a single field:

field  | type | required   | description
:----- | :---- | :--- | :------------
`by` | string | yes | the name of the grouping field
`field` | string | yes | the name of measurement field
`score` | float | yes | the value of the threshold for outliers

#### Example
{% highlight json %}
{
  "data": {
    "type": "actors",
    "attributes": {
      "type": "zscore",
      "params": {
        "by": "tag",
        "field": "amount",
        "score" : 6.0
      }
    }
  }
}
{% endhighlight %}
This will create a Z-score component monitoring the field _amount_. Outliers are defined to be more than 6 sd from the average.

## Trigger
The `ZscoreActor` accepts as trigger a JSON with a value for the specified field.

## Emit
The `ZscoreActor` emits the input but only when the value is considered an outlier (see below)

## State
The `ZscoreActor` keeps no state.

## Collect
The `ZscoreActor` collects state from the `StatsActor` for the field it monitors.
The count, average and standard deviation values are used to determine whether or not the trigger value is an outlier.

## Timer
The `ZscoreActor` does not provide timer actions.