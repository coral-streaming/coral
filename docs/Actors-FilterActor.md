---
layout: default
title: FilterActor
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

# FilterActor
The `FilterActor` (filter actor) is a [Coral Actor](/actors/overview/) that emits the received JSON when all the defined filter(s) match. Filters match on a defined field in the JSON and can be either include
(received JSON is emitted when filter applies) or exclude (received JSON is emitted when the filter doesn't apply).

## Creating a FilterActor
The FilterActor has `type: "filter"`. The `params` value is a JSON array with at least one filter. Each filter is a JSON object with the following fields:

field  | type |    | description
:----- | :---- | :--- | :------------
`type`     | string | required | only `startswith` is currently supported.
`function` | string | required | `exclude` or `include`.
`field`    | string | required | the field in the trigger JSON to which the filter is applied.
`param`    | string | required | the parameter for the given type, for `startswith` the start of the field.

#### Example
{% highlight json %}
{
  "data": {
    "type": "actors",
    "attributes": {
      "type": "filter",
      "params": [{
        "type": "startswith",
        "function": "include",
        "field": "url",
        "param": "/site/"
      }]
    }
  }
}
{% endhighlight %}
This will create a filter actor for the field _url_. Only when the trigger JSON has a field `url` and this field starts with "/site/", the trigger JSON is emitted.

## Trigger
The `FilterActor` only does useful work if the trigger is connected.

## Emit
The `FilterActor` emits the received JSON when all the filters match. Otherwise, nothing is emitted.

## State
The `FilterActor` doesn't keep state.

## Collect
The `FilterActor` does not collect state from other actors.

## Timer
The `FilterActor` does not provide timer actions.