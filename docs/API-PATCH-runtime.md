---
title: PATCH runtime
layout: default
topic: API Reference
category: Runtimes
verb: PATCH
endpoint: /api/runtimes
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

### Change runtime state

<div class="alert alert-info" role="alert"><strong>PATCH</strong> /api/runtimes/&lt;id&gt;</div>

Changes the state of a runtime. Currently, it is only possible to start and to stop a runtime.

### JSON input

{% highlight json %}
{
  "status": string
}
{% endhighlight %}

The "status" field can only contain "start" or "stop".

### Example

<div class="alert alert-info" role="alert"><strong>PATCH</strong> /api/runtimes/runtime1</div>

{% highlight json %}
{
  "status": "start"
}
{% endhighlight %}

### JSON output

When successful:

{% highlight json %}
{
  "action": "Start runtime",
  "name": string,
  "success": true,
  "time": datetime
}
{% endhighlight %}

The platform returns the name of the runtime and the time that it was started.

On failure:

{% highlight json %}
{
  "action": "Start runtime",
  "success": false,
  "reason": string,
  "details": string
}
{% endhighlight %}

When the API call fails, the platform returns the reason of the failure and a stack trace of the failure. 

### Example

When successful:

<div class="alert alert-success">200 OK</div>

{% highlight json %}
{
  "action": "Start runtime",
  "name": "runtime1",
  "success": true,
  "time": "2015-12-29T15:39:51"
}
{% endhighlight %}

On failure:

<div class="alert alert-danger">503 Bad request</div>

{% highlight json %}
{
  "success": false,
  "reason": "Owner with name neo not found."
  "details": "<stacktrace>"
}
{% endhighlight %}

### Errors

status code | description | reason
---: | :--- | :---
`503` | Bad request | When a status other than "start" or "stop" is given, or when "start" is given when the runtime was already started.
