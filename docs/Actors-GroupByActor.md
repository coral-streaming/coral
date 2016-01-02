---
layout: default
title: GroupByActor
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

# GroupByActor
The `GroupByActor` is a technical [Coral Actor](/actors/overview/) that can partition an event stream based on unique values of an input field. The `GroupByActor` will relay messages to the correct actor, depending on the value of the input field, and will create a child actor for values that it did not see before.

## Creating a GroupByActor
The `GroupByActor` is not created directly. If an actor that supports grouping has the group by field specified in its constructor, a GroupByActor will be created. The `GroupByActor` will redirect the object to the correct actor for distinct values of the group by field.

#### Example
{% highlight json %}
{
  "type": "stats",
  "params": {
    "field": "amount"
  }, "group": {
    "by": "tag"
  }
}
{% endhighlight %}

In this example, a [StatsActor](Actors-StatsActor.html) is being created partitioned by the field 'tag'.  The Coral platform creates a `GroupByActor`, and waits for distinct values of the 'tag' field. An actual instance of the `StatsActor` is only created when a 'tag' field comes along containing a new value that was not encountered before.

## Trigger
The `GroupByActor` reads the value of the group by field from the trigger JSON. If such a field is found, the JSON will be relayed as trigger for the corresponding underlying actor. For a newly encountered value a new actor will be created. If the group by field is not found, nothing is done.

## Emit
The `GroupByActor` does not emit anything itself. However, underlying actors may emit.

## State
The `GroupByActor` does not keep state. However, if underlying actors do keep state, this state can be collected from the `GroupByActor`.

## Collect
The `GroupByActor` does not collect state from other actors. The underlying actors however may do so.

## Timer
The `GroupByActor` does not itself have a timer function implemented. The underlying actors, however, can have timer functions.