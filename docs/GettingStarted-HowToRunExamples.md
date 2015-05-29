---
title: How to run the examples
layout: default
topic: Getting started
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

# Running the examples

The "examples" directory contains several examples for the platform. This directory can be found in the source trunk of the platform. The examples have been written in Python,
but any language or method that supports HTTP can be used to create a new pipeline on the platform. Other methods are [Postman](https://www.getpostman.com) or curl, which is a command-line program.
When you create your own pipeline, you need to set the headers that are obligatory according to [JSON API](http://jsonapi.org/): the Accept header and the Content-Type header.
So set the header `Accept: application/vnd.api+json` and `Content-Type: application/vnd.api+json`.