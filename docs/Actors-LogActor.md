---
layout: default
title: LogActor
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

# LogActor
The `LogActor` is a [Coral Actor](/actors/overview/) that logs the received trigger JSON object. 
The actor can write incoming messages to a file or it can send them to the same log as where Coral platform log messages are written to (by default, this is standard out). This is useful for debugging purposes.

## Creating a LogActor
The LogActor has `"type": "log"`. The `params` value is optional and has the following fields:

field  | type | required | description
:----- | :---- | :--- | :------------
`file`   | string  | no | location of the file to log to. If not provided, the log settings of the platform are used.
`append` | boolean | no | when true, the actor appends to a possibily existing file, otherwise a possibly existing the file is overwritten.

<br>

#### Example
{% highlight json %}
{
  "type": "log",
  "params": {
    "file": "/tmp/coral.log",
    "append": true
  }
}
{% endhighlight %}

This will create a log actor that appends the received trigger JSON to the file `/tmp/coral.log`. If no `params` value is given, the log settings of the Coral platform will be used:

{% highlight json %}
{
  "type": "log"
}
{% endhighlight %}

By default, this means that JSON objects will then be sent to standard out.

## Trigger
The `LogActor` only does useful work if JSON objects come in through its trigger.

## Emit
The `LogActor` emits nothing. Conceptually, writing the trigger message to file or to a log can be thought of as an emit.

## State
The `LogActor` does not keep state.

## Collect
The `LogActor` does not collect state from other actors.

## Timer
The `LogActor` does not provide timer actions.