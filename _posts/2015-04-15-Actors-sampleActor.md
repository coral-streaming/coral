---
layout: default
title: sampleActor
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

# SampleActor
The `SampleActor` (sample actor) is a [Coral Actor](/actors/overview/) that only emits a fraction of the supplied JSON trigger messages. The fraction can be specified by the user. The selection of messages to pass is random, but such that on average in the long term the fraction passed corresponds to the specified fraction.

## Creating a SampleActor
The creation JSON of the sample actor (see [Coral Actor](/actors/overview/)) has `type: "sample"`.
The `params` value is a JSON with one field of two options:

field  | type |    | description
:----- | :---- | :--- | :------------
`fraction` | float| optional| the fraction of messages to be passed
`percentage` | float| optional| the fraction of messages to be passed expressed as percentage number

One and only one of these parameters has to be specified for correct definition. The percentage simply is 100 times the fraction.

#### Example
```json
{
  "type": "sample",
  "params": { "percentage": 12.5 }
}
```
This will create a sample actor that passes 12.5% of all messages.

## Trigger
The `SampleActor` only does useful work if the trigger is connected.
The actor does not process the supplied JSON.

## Emit
The `SampleActor` emits the received JSON with probability equal to the supplied fraction.
Otherwise nothing is emitted.

## State
The `SampleActor` doesn't keep state.

## Collect
The `SampleActor` doesn't collect.