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
The `HttpClientActor` (HTTP client) is a [Coral Actor](/actors/overview/) that can post information to a specified URL.

## Creating a HttpClientActor
The creation JSON of the HttpClientActor actor (see [Coral Actor](/actors/overview/)) has `"**type**": "httpclient"`. This is the only field in the creating JSON for this actor.

#### Example
```json
{
  "type": "httpclient"
}
```

## Trigger
The `HttpClientActor` is triggered by a JSON with the fields '**url**', '**method**' and '**payload**'.
It will perform a HTTP method on the given url with the given payload message.
Currently the client supports the following HTTP methods:

POST
GET
PUT
DELETE

#### Example
```json
{
  "url": "www.google.com",
  "method": "GET",
  "payload": ""
}
```

## Emit
The `HttpClientActor` emits a json with the '**status**', '**header**' and '**body**' of the respons of the http method that was triggered.

## State
The `HttpClientActor` does not keep a state

## Collect
The `HttpClientActor` does not collect state from other actors.

## Timer
The `HttpClientActor` does not implement a timer action.
