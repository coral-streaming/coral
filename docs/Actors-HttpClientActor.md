---
layout: default
title: HttpClientActor
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

# HttpClientActor
The `HttpClientActor` is a [Coral Actor](/actors/overview/) that can perform POST, GET, PUT, and DELETE HTTP requests on a specified URL.

## Creating a HttpClientActor
The HttpClientActor has `"type": "httpclient"`. The `params` value is a JSON object with the following fields:

field  | type | required | description
:----- | :---- | :--- | :------------
`mode` | string | yes | Either `static` or `dynamic`.
`field` | string | no | When `mode` is set to `dynamic`, the field to get the URL from.
`url` | string | yes | The URL to connect to.
`method` | string | yes | The HTTP method (verb) to use.
`headers` | JSON | no | Additional headers to send for the request.

#### Example

An example of a static HTTPClient is given below:

{% highlight json %}
{
  "type": "httpclient",
  "params": {
    "mode": "static",
    "url": "http://www.google.com", 
    "method": "POST"
  }
}
{% endhighlight %}

In this case, the actor will post the JSON it receives through its trigger to http://www.google.com.

An example of a dynamic HTPTClient is given below:
{% highlight json %}
{
  "type": "httpclient",
  "params": {
    "mode": "dynamic",
    "field": "url",
    "method": "POST"
  }
}
{% endhighlight %}

When the actor now receives a JSON object with a field "url" in it, it will post the received JSON to that URL.

## Trigger
The `HttpClientActor` is triggered by a JSON object. The `HttpClientActor` will perform the specified HTTP on the URL obtained as described above. The payload is the received trigger JSON object. Currently the client supports the following HTTP methods:

- POST
- GET
- PUT
- DELETE

## Emit
The `HttpClientActor` emits a json with the **status**, **header** and **body** of the response of the http method that was triggered. The **header** field is an JSON object with as fields the different header fields and as values the header values.

## State
The `HttpClientActor` does not keep state.

## Collect
The `HttpClientActor` does not collect state from other actors.

## Timer
The `HttpClientActor` does not define a timer function.
