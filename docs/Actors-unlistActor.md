---
layout: default
title: unlistActor
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

# UnlistActor
The `UnlistActor` is a [Coral Actor](/actors/overview/) that has as input a list (JArray) of JSON objects and emits these objects separately in order without delay.

## Creating a UnlistActor
The creation JSON of the UnlistActor (see [Coral Actor](/actors/overview/)) has `"type": "unlist"`.
The `params` value is a JSON with a single field:

field  | type | required | description
:----- | :---- | :--- | :------------
`field` | string | yes| The name of the array field in the JSON trigger object to unlist.

#### Example
```json
{
  "type": "unlist",
  "params": {
    "field": "data"
  }
}
```

This will create an `UnlistActor` monitoring the field _data_. Each JSON object in the field data will be emitted separately every time the _UnlistActor_ is triggered.

## Trigger
The `UnlistActor` is triggered by an object that contains an array, as follows:

{% highlight json %}
{ 
   "data": [
      { "name1": "object1" },
      { "name2": "object2" },
      { "name3": "object3" },
      { "name4": "object4" }
   ] 
}
{% endhighlight %}
## Emit
The `UnlistActor` emits all objects that are in the array separately, so in the example above it first emits

```json
{ "name": "object1" }
```

then 

```json
{ "name": "object2" }
```
 then

```json
{ "name": "object3" }
```

and then 

```json
{ "name": "object4" }
```

without delay between emits.

## State
The `UnlistActor` does not maintain any state.

## Collect
The `StatsActor` does not collect state from other actors.