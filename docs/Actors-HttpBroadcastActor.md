---
layout: default
title: HttpBroadcastActor
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

# HttpBroadcastActor
The `HttpBroadcastActor` is a [Coral Actor](/actors/overview/) that passes through HTTP JSON requests. It is used to be able to directly send the incoming HTTP JSON requests to multiple actors by registering the `HttpBroadcastActor` as the source for these actors.

## Creating a HttpBroadcastActor
The HttpBroadcastActor has `"type": "httpbroadcast"`. This is the only field in the creating JSON for this actor. There is no `params` field.

#### Example
{% highlight json %}
{
  "type": "httpbroadcast"
}
{% endhighlight %}

## Trigger/Emit
The `HttpBroadcastActor` emits what is supplied to the trigger (passthrough).

## State
The `HttpBroadcastActor` does not keep state.

## Collect
The `HttpServerActor` does not collect state from other actors.

## Timer
The `HttpServerActor` does not provide timer actions.