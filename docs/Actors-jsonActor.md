---
layout: default
title: JsonActor
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

# JsonActor
The `JsonActor` (JSON transformation actor) is a [Coral Actor](/coral/docs/Overview-Actors.html) that can transform an input JSON according to a supplied template.

## Creating a JsonActor
The creation JSON of the JsonActor (see [Coral Actor](/coral/docs/Overview-Actors.html)) has `"type": "json"`.
The `params` value is a JSON with the following field:

field  | type | required | description
:----- | :---- | :--- | :------------
`template` | JSON | yes| the template for the output

#### Example
{% highlight json %}
{
  "data": {
    "type": "actors",
    "attributes": {
      "type": "json",
      "params": {
        "template": {
          "a": "some constant text",
          "b": "${referenceField}",
          "c": "${ref.sub.sub[2]}",
          "d": {
            "e": "${ref2}",
            "f": "${ref3}"
          }
        }
      }
    }
  }
}
{% endhighlight %}
If fields are expressions the create a string field starting with a the expression (according to the coral JSON expression parser) surrounded by `${` ... `}`.

## Trigger
The `JsonActor` only does useful work if the trigger is connected.
The trigger can be any JSON. The supplied JSON will be used to fill te template.

#### Example

## Emit
The `JsonActor` emits the transformed JSON.
That is the template JSON with expressions replaced with their evaluation from the trigger input.

## State
The `JsonActor` keeps no state.

## Collect
The `JsonActor` does not collect state from other actors.