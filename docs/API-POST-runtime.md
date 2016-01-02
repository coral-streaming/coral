---
title: POST runtime
layout: default
topic: API Reference
category: Runtimes
verb: POST
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

### Create a new runtime

<div class="alert alert-info" role="alert"><strong>POST</strong> /api/runtimes</div>

Creates a new runtime on the platform. A runtime contains actors and links between those actors.

<div class="alert alert-warning" role="alert">
  <span class="glyphicon glyphicon-exclamation-sign" aria-hidden="true"></span>
  <span class="sr-only">Warning</span>
  <strong>NOTE</strong>:&nbsp;This call does not actually start the runtime, but only instantiates it.<br>To start it, look <a href="API-PATCH-runtime.html">here</a>.
</div>

### JSON input

{% highlight json %}
{
  "name": string,
  ("owner": string or UUID),
  ("projectid": UUID),
  "actors": [
     <actor_def>, ...
  ], "links": [
    <link_def>, ...
  ], 
  ("distribution": {
    <distribution>
  })
}

<actor_def>:
{
  "name": string,
  "type": string,
  "params": {
    <actor_params>
  }
}

<link_def>: 
{
  "from": string, "to": string
}

<distribution>:
{
  "mode": string,
  ("machine": {
    "ip": string,
    "port": int
  })
}
{% endhighlight %}

The "owner" field is optional, and can be either the unique name of the user or an UUID. If no owner field is provided, the current owner is assumed. When "accept-all" authentication is used, a user with the name "coral" is assumed. If the specified owner is not equal to the currently logged in user, the request is rejected. This may be changed in future releases, when a runtime on behalf of another user can be created.

The projectid is optional and is a planned future feature of the Coral platform. Currently, it is not used. The "actors" section contains actor definitions, which can be found in the [Actor Overview](Overview-ActorOverview.html). The "links" section contains all links between actors. The names mentioned in "from" and "to" must be existing names in the actor definition section.

The distribution specification is optional and specifies the distribution mechanism of the runtime on the Coral platform. The mode field can be any of ones specified [here](Overview-Architecture#distribution).
The "machine" field is only necessary when the "predefined" mode is used. If no distribution section is present, the platform distribution settings will be used.

### Example

<div class="alert alert-info" role="alert"><strong>POST</strong> /api/runtimes</div>

{% highlight json %}
{
  "name": "runtime1",
  "owner": "neo",
  "actors": [{
    "name": "generator1",
    "type": "generator",
      "params": {
        "format": {
          "field1": "Hello, world!"
        }, "timer": {
          "rate": 10
        }
      }
    }, {
      "name": "log1",
      "type": "log",
      "params": {
        "file": "/tmp/runtime1.log"
      }
  }], "links": [
    { "from": "generator1", "to": "log1" }
  ], 
  "distribution": {
    "mode": "predefined",
      "machine": {
        "ip": "127.0.0.1",
        "port": 2551
      }
    }
}
{% endhighlight %}

### JSON output

When successful:

{% highlight json %}
{
  "success": true,
  "created": datetime,
  "id": UUID,
  "definition": json
}
{% endhighlight %}

The platform returns the time of creation of the runtime, a unique identifier for the created runtime and the definition that you have posted.

On failure:

{% highlight json %}
{
  "success": false,
  "reason": string,
  "details": string
}
{% endhighlight %}

When the API call fails, the platform returns the reason of the failure and a stack trace of the failure. 

### Example

When successful:

<div class="alert alert-success">201 Created</div>

{% highlight json %}
{
  "success": true,
  "created": "2015-12-29T15:39:51",
  "id": "5572fdda-34a4-4d8f-8a6a-77ae36e1e3e5",
  "definition": <json definition>
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
`401` | Unauthorized | The user in the HTTP Basic Auth header is not the same as the owner in the runtime definition.
`503` | Bad request | The runtime definition is invalid. This can have several reasons.
`500` | Internal server error | There are several reasons an internal server error can be thrown. Check the stack trace of the resulting error code. If you cannot fix the error, please notify us so that we can investigate further.

<br>

#### Invalid runtime definition

A runtime definition can be invalid for several reasons:

- There is no valid actors section present. The "actors" array field is missing in your JSON definition. Make sure you have an "actors" section in your definition.
- There are no actors present in the actors section. The "actors" array field is present in your JSON definition, but it does not contain any actor definitions. Make sure that the "actors" section is not empty.
- There are actors without names. Make sure that each actor has a "name" field.
- The configuration does not have a runtime name specified in the "name" field. Make sure the "name" field in the top level of the JSON object is present.
- There are actors with duplicate names. Make sure that every actor has a unique name.
- There are invalid actor definitions. Check each actor definition against the specification of that actor.
- There is no valid links section present. Make sure a "links" section is present in the definition.
- There are no links present in the links section. The "links" section is present, but the array is empty. Make sure that the "links" section contains at least one link.
- There are links without a "from" and "to" attribute. Each link should have both a "from" and a "to" field.
- Not all actors mentioned in the links are actual actor names in the configuration. Make sure that all actors mentioned in the links section are defined in the actor section. The "from" or "to" field should match the "name" section of exactly one actor.
- There are actors which are not being referenced in any from or to field of the links section. Make sure that each actor is connected at least once.
- There are actors with types which do not refer to any existing actor (see [Actor Overview](Overview-ActorOverview.html)). Make sure each actor type refers to an actually existing actor. 

If you encounter any of these errors, check your runtime definition carefully and submit your runtime again if you have fixed the errors.
