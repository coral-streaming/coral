---
layout: default
title: HttpClientActor
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

# HttpClientActor
The `HttpClientActor` (HTTP client) is a [Coral Actor](/actors/overview/) that can perform the POST, GET, PUT, and DELETE HTTP methods on a specified URL.

## Creating a HttpClientActor
The creation JSON of the HttpClientActor actor (see [Coral Actor](/actors/overview/)) has `"type": "httpclient"`. The `params` value is a JSON:

field  | type | required | description
:----- | :---- | :--- | :------------
`url` | string | yes | url to which to connect
`method` | JSON object | yes | the HTTP method to use to connect
`headers` | JSON object | no | additional headers to send for the request

#### Example
```json
{
  "type": "httpclient"
  "params": {"url": "http://www.google.com", "method": "GET"}
}
```

## Trigger
The `HttpClientActor` is triggered by a JSON. This JSON can be an empty object or can contain the field '**payload**'.
The `HttpClientActor` will perform a HTTP method on the defined field '**url***' with the optionally the given payload message.
Currently the client supports the following HTTP methods:

POST
GET
PUT
DELETE

#### Example
```json
{
  "payload": "my payload"
}
```

## Emit
The `HttpClientActor` emits a json with the '**status**', '**header**' and '**body**' of the response of the http method that was triggered.
The '**header**' field is an JSON object with as fields the different header fields and as values the header values. The body's value is a JSON
string, unless the server responds with JSON content, then it is a JSON object.

## State
The `HttpClientActor` does not keep a state

## Collect
The `HttpClientActor` does not collect state from other actors.

## Timer
When the timer is defined in the `HttpClientActor`'s instantiation JSON, the actor will act according to this definition, so it's possible to
 e.g. poll an URL every second and emit the results to all the actors to which the `HttpClientActor` is defined to emit to.
