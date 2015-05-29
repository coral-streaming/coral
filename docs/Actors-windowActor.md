---
layout: default
title: windowActor
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

# WindowActor
The `WindowActor` is a [Coral Actor](/actors/overview/) that enables windowing of events based on count or time. The actor receives individual events but, depending on the settings, waits until a certain time or a certain number of events have been received and then emits a list of the received items.

## Creating a WindowActor
The creation JSON of the WindowActor (see [Coral Actor](/actors/overview/)) has `type: "window"`.
The `params` value is a JSON with a single field:

field  | type |    | description
:----- | :---- | :--- | :------------
`method` | String | required | The method of windowing to use, either "count" or "time"
`number` | Int | required | The number of milliseconds (in the case of method == "time") or number of events (in the case of method == "count")
`sliding` | Int | not required | The number of events a window should slide. For instance, when sliding == 1, the window moves 1 event every time an event has been received. In the case of method == "time", sliding should be specified in milliseconds.

#### Creation example
{% highlight json %}
{
  "data": {
    "type": "actors",
    "attributes": {
      "type": "window",
      "params": {
        "method": "count",
        "number": 3,
        "sliding": 1
      }
    }
  }
}
{% endhighlight %}
This will create a WindowActor that waits until it receives 3 JSON objects, then emits these 3. Then, if a new event comes in, it moves the window, as in the following example:

{% highlight json %}
{
   "data": [
      { "name": "object1" },
      { "name": "object2" },
      { "name": "object3" }
   ]
}
{% endhighlight %}

Then, object4 comes in, and the WindowActor emits the following: 

{% highlight json %}
{
   "data": [
      { "name": "object2" },
      { "name": "object3" },
      { "name": "object4" }
   ]
}
{% endhighlight %}

## Trigger
The `WindowActor` is triggered by any incoming JSON event. It does not care about the structure of the JSON objects received, they can even be all different.

## Emit
When the right number of objects is collected, a list is emitted as in the example above. If method == "time", the `WindowActor` waits that amount of time and then emits what it has collected, if anything.

## State
The `WindowActor` returns its state as follows:

field |type| description
:--- | :--- | :---
`method` | String | "count" or "time" as set in the constructor
`number` | Int | The number of objects or the number of seconds of the window.
`sliding` | Array | The amount of objects or the amount of milliseconds the window slides every time.
