---
layout: default
title: LookupActor
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

# LookupActor
The `LookupActor` is a [Coral Actor](/actors/overview/) that can enrich data via the lookup table provided in its constructor.

## Creating a LookupActor
The LookupActor has `"type": "lookup"`. The `params` value contains the following fields:

field  | type | required | description
:----- | :---- | :--- | :------------
`key` | string | yes | name of the key field
`lookup` | JSON object | yes | the lookup table
`function` | string | yes | `enrich`, `filter` or `map`
`match` | string | no | `exact` or `startswith`
`default` | JSON object | no | when there is no match, the default is used when defined

<br>

The `function` field in the constructor defines the behavior of the `LookupActor`.

<br>

#### Example
{% highlight json %}
{
  "type": "lookup",
  "params": {
    "key": "city",
    "function": "enrich",
    "lookup": {
      "amsterdam": { 
        "country": "netherlands", 
        "population": 800000 
      }, "vancouver": { 
        "country": "canada", 
        "population": 600000
      }
    }
  }
}
{% endhighlight %}

This `LookUpActor` will now wait for any incoming trigger messages and will look at the "city" field. 
It will merge the "country" and "population" fields into the output message since "function" is set to "enrich".

If this is the input:
{% highlight json %}
{
  "city": "amsterdam",
  "otherdata": "irrelevant",
  "somevalue": 10
}
{% endhighlight %}

Then `enrich` will emit this output:
{% highlight json %}
{
   "city": "amsterdam",
   "otherdata": "irrelevant",
   "somevalue": 10,
   "country": "netherlands",
   "population": 800000
}
{% endhighlight %}

If the function is set to `filter`, the output is

{% highlight json %}
{
  "city": "amsterdam",
  "otherdata": "irrelevant",
  "somevalue": 10
}
{% endhighlight %}

but only if the "city" key is found in the lookup map. If it is not found, the input object is not emitted.

In the case of `map`, the output is the looked up object if it exists, else, nothing is emitted:
{% highlight json %}
{
   "country": "netherlands",
   "population": 800000
}
{% endhighlight %}

The standard behaviour is to use an exact match. When `match` is set to "startswith", a check is done if the key is a string of which a substring occurs in the lookup map. If there are multiple matches, one of them is used, which one is undefined.

The standard behaviour when there is no match is to do nothing: for check and filter nothing is emitted, for enrich nothing will be added to the output. When a default is given, this is used when there are no matches, so check and filter emit the default and enrich enriches with the default. The default is a template like used by the [JsonActor](/coral/docs/Actors-JsonActor.html).

## Trigger
The `LookupActor` accepts any JSON as trigger.

## Emit
The `function` of the `LookupActor` determines what is emitted.

- **enrich**  
The function `enrich` always emits the original JSON object. If lookup values are found, these are added to the original JSON object.
- **filter**  
The function `filter` emits the unenriched, original JSON when it is found in the lookup table. If it is not found, nothing is emitted.
- **check**  
The function `check` emits the lookup value when it is found, otherwise, nothing is emitted.

## State
The `LookupActor`does not keep state.

## Collect
The `LookupActor` does not collect state from other actors.

## Timer
The `LookupActor` does not provide timer actions.