---
layout: default
title: GeneratorActor
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

# GeneratorActor
The `GeneratorActor` is a [Coral Actor](/actors/overview/) that enables the generation of data based on a JSON input template. The JSON input template defines the fields and structure of the JSON object to emit, and the values of the JSON template define the distribution of the data to generate.

## Creating a GeneratorActor
	
The GeneratorActor has `"type": "generator"`. The `params` value is a JSON object with the following fields:

field  | type |  required | description
:----- | :---- | :--- | :------------
`format` | JObject | yes | The format of the object to return. The complete object will be returned as the top-level JSON object. The fields provide the distribution of the data to generate.
`timer`:&nbsp;`rate` | Int | yes | The number of objects to generate per second.
`timer`:&nbsp;`times` | Int | no | The total number of objects to generate. After this number is reached, output is stopped.
`timer`:&nbsp;`delay` | Int | no | The number of seconds to wait until the first object is emitted. Not required, if no delay is given, a delay of 0 seconds is used.

<br>

The `GeneratorActor` currently has three different generator functions:

name | format  | description | example
:----- | :---- | :--- | :------------
Normal | `N(mu, sigma)` | Samples a value from the normal distribution as given by the mu (average) and sigma (standard deviation) parameters. | `N(100.2,53.8)`
Uniform | `U(max)` | Samples a value from a uniform distribution from 0 to `max` | `U(42)`
List&nbsp;choice | `['s1','s2','s3']` | Samples a value uniformly from any of the given string choices `s1`, `s2` or `s3`. | `['a','b','c']`

<br>

#### Creation example

{% highlight json %}
{
  "type": "generator",
  "params": {
    "format": {
      "field1": "N(100, 10)",
      "field2": "['a', 'b', 'c']",
      "field3": {
        "nested1": "U(100)",
        "nested2": "U(20.3)",
        "nested3": "N(20.5, 5.2)"
      }
    }, "timer": {
      "rate": 10,
      "times": 100,
      "delay": 1000
    }
  }
}
{% endhighlight %}
This will create a GeneratorActor that emits 10 objects per second, of which a couple of examples follow:

{% highlight json %}
{
  "field1": 82.85,
  "field2": "a",
  "field3": {
     "nested1": 57.42,
     "nested2": 4.75,
     "nested3": 18.97
  }
}
{% endhighlight %}

Next, the following object may be emitted:

{% highlight json %}
{
  "field1": 113.45,
  "field2": "b",
  "field3": {
     "nested1": 25.18,
     "nested2": 12.8,
     "nested3": 17.85
  }
}
{% endhighlight %}

Because the delay is set to 1 second, it waits 1 second before emitting the first object. When it has emitted 100 objects, it stops.

## Trigger
The `GeneratorActor` is not triggered by external events, but rather generates data autonomously.

## Emit
The `GeneratorActor` emits data based on the `rate` parameter. When `rate` is set to 10, it emits an object every 100 milliseconds.

## State
The state of the `GeneratorActor` returns the following fields:

field  | type | description
:----- | :---- | :------------
`rate` | Int | The number of objects per second that will be emitted.
`times` | Int | The maximum number of objects to emit.
`delay` | Int | The number of seconds that the actor has waited before emitting the first object.
`format` | JObject | The format that was specified in the constructor.
`count` | Int | The total number of objects emitted until now.

## Collect
The `GeneratorActor` does not collect state from other actors.

## Timer
The `GeneratorActor` periodically emits data according to its `rate`, `times` and `delay` settings.