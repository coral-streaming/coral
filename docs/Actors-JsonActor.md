---
layout: default
title: JsonActor
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
The `JsonActor` (JSON transformation actor) is a [Coral Actor](/coral/docs/Overview-Actors.html) that can transform an input JSON object into an output JSON object according to a supplied template.

## Creating a JsonActor
The JsonActor has `"type": "json"`. The `params` value is a JSON object with the following field:

field  | type | required | description
:----- | :---- | :--- | :------------
`template` | JSON | yes| the template for the output

#### Example
{% highlight json %}
{
  "type": "json",
  "params": {
    "template": {
      "a": "some constant text",
      "b": "${referenceField}",
      "c": "${ref.sub.sub[2]}",
      "d": {
        "e": "${ref.othersub}",
        "f": "${ref3}"
      }
    }
  }
}
{% endhighlight %}

The above example defines a template according to which the output JSON will be formatted. To access a field value in the trigger JSON, surround a field name with `${` ... `}`.

In the above example, the value of top-level field "referenceField" from the input JSON object is used as value for field "b" in the output JSON object. Sub-fields can be accessed by dot notation, as the field "ref.othersub" shows. Array elements can be accessed by `[i]` where `i` refers to the 0-based index of the element in the array.

## Trigger

The `JsonActor` only does useful work if the trigger is connected.
The trigger can be any JSON object. The supplied JSON will be used to fill the template.

## Emit
The `JsonActor` emits the transformed JSON object.

## State
The `JsonActor` does not keep any state.

## Collect
The `JsonActor` does not collect state from other actors.

## Timer
The `JsonActor` does not provide timer actions.