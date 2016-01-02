---
title: Hello World
layout: default
topic: Getting started
order: 3
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

## Hello World

  - [Introduction](#introduction)
  - [Download and unzip](#download)
  - [Create a user](#createuser)
  - [Start the platform](#startplatform)
  - [Set up a runtime](#setupruntime)
  - [Start the runtime](#startruntime)
  - [Investigate the output](#investigateoutput)
  
<br>

### <a name="introduction"></a>Introduction

This is a complete example how to start the platform, set up a runtime, start it, and let it write text to a file. 

<div class="alert alert-warning" role="alert">
  <span class="glyphicon glyphicon-exclamation-sign" aria-hidden="true"></span>
  <span class="sr-only">Warning</span>
  <strong>NOTE</strong>:&nbsp;This tutorial does not deal with Kafka, please look <a href ="GettingStarted-HelloWorldKafka.html">here</a> for a tutorial that does. 
</div>

We assume that you have downloaded and extracted the Coral platform on your machine, and that Cassandra is running. In this tutorial, we assume that you use the latest version of the Coral platform. We will also assume that you use `curl` to send commands to Coral. As stated in the section [Prerequisites](GettingStarted-Prerequisites.html), however, you can use any HTTP client you want.

The following headers should be used to access the API:

header | value
---: | :---
Content-Type | application-json
Accept | application-json
Authorization | Basic YWRtaW46cGFzc3dvcmQ=

<br>
Where the Authorization header contains your encrypted user name and password. If you use `curl`, you can issue the command in a terminal as follows:

{% highlight bash %}
curl -H "Content-Type: application/json" \
	-H "Accept: application/json" \
	--user <username>:<password> \
	--request GET \
	http://127.0.0.1:8000/api/runtimes
{% endhighlight %}

--------------------------

### <a name="download"></a>Download and unzip

[Download](GettingStarted-Download.html) the .tar.gz file and issue the following command:

{% highlight bash %}
tar -xzvf coral-runtime-0.1.1.tar.gz
{% endhighlight %}

--------------------------

### <a name="createuser"></a>Create a user

First, a user needs to be created. To do this, enter the following command, where the values that should be filled in are described in the table below.

<div class="alert alert-warning" role="alert">
  <span class="glyphicon glyphicon-exclamation-sign" aria-hidden="true"></span>
  <span class="sr-only">Note</span>
  <strong>NOTE</strong>:&nbsp;In this tutorial, we assume that you have a Cassandra instance running at 192.168.100.101:9042. Change this to the IP address and port on which you have a Cassandra instance running.</div>

{% highlight bash %}
java -cp "coral-runtime-0.0.131.jar" io.coral.api.Boot \
	user add -ccp "192.168.100.101" \
	-cp 9042 -k "coral" -n "neo" \
	-f "Thomas Anderson" -e "mranderson@metacortex.com" \
	-m "555-1234" -d "Meta Cortex programming division"
{% endhighlight %}

The program asks you to enter a password for the user and asks you to confirm the password. We will use the password "thematrix". If successful, a user with the following properties is now inserted into the database:

short | long | property | value
---: | ---: | ---: | :---
n | uniquename | Unique name | neo
f | fullname | Full name | Thomas Anderson
e | email | E-mail | mranderson@metacortex.com
m | mobile | Mobile phone | 555-1234
d | department | Department | Meta Cortex programming division

<br>
You could also directly insert the user into the database, but the disadvantage of this approach is that you need to generate a UUID yourself and that you need to hash the password of the user yourself. This is not recommended, it is probably easier to use this program.

--------------------------

### <a name="startplatform"></a>Start the platform

There are three different ways to set parameters for the Coral platform:

1. On the command line. These options override any other settings.
2. By specifying the location of a configuration file on the command line.
3. In the application.conf file in the jar of Coral. These settings are used as defaults if not overriden.

In this tutorial, we will only use the first option.

To start the platform, enter the following command:

{% highlight bash %}
java -cp "coral-runtime-0.0.131.jar" \
    io.coral.api.Boot start \
	-ai "0.0.0.0" -p 8000 -ah "127.0.0.1" \
	-ap 2551 -am "coral" -ccp "192.168.100.101" \
	-cp 9042 -k "coral" -nc -ll INFO
{% endhighlight %}

These options are explained in the table below:

short | long name | explanation
---: | :--- | :--- | :---
ai | apiinterface | Incoming HTTP requests on this interface are accepted.
p | apiport | HTTP traffic on this port is accepted.
ah | akkahostname | This is the interface that Akka binds to.
ap | akkaport | This is the port that Akka binds to.
am | authenticationmode | Sets the authentication mode.
ccp | contactpoints | The Cassandra node(s) that Coral will connect with.
cp | cassandraport | The port on which a connection with Cassandra will be made.
k | keyspace | The name of the Cassandra keyspace that will be used.
nc | nocluster | Tells Coral that this node will not be connected to a cluster.
ll | loglevel | Sets the log level.

<br>
Coral will print debug messages to the console, stating that it is now "Bound to /0.0.0.0:8000".
This means that the Coral platform can now be approached using the HTTP interface on "http://127.0.0.1:8000".

To test whether this is actually working, issue the following command: 

{% highlight bash %}
curl -H "Content-Type: application/json" \
	-H "Accept: application/json" \
	--request GET \
	--user neo:thematrix \
	http://127.0.0.1:8000/api/runtimes
{% endhighlight %}

The platform will respond with

{% highlight json %}
[]
{% endhighlight %}

This is a JSON array showing the runtimes currently running on the platform. As there are no runtimes on the platform yet, it returns an empty array.

--------------------------

### <a name="setupruntime"></a>Set up a runtime

We will set up a runtime with a [generator actor]() and a [log actor]() that writes the generated data to file. The definition of the runtime is as follows:

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
    ]
}
{% endhighlight %}

This runtime generates the text "Hello, world!" 10 times per second and writes this text to the file "/tmp/runtime1.log" until it is stopped. You can change the location of the text file, but make sure you check the output in the correct file.

To create the runtime, send the following command:

{% highlight bash %}
curl -H "Content-Type: application/json"     \
	-H "Accept: application/json"        \
	--user neo:thematrix                 \
	--request POST                       \
	--data '{ "name": "runtime1", "owner": "neo", "actors": [{ "name": "generator1", "type": "generator", "params": { "format": { "field1": "Hello, world!" }, "timer": { "rate": 10 }}}, { "name": "log1", "type": "log", "params": { "file": "/tmp/runtime1.log" }}], "links": [ { "from": "generator1", "to": "log1" }]}' \
http://127.0.0.1:8000/api/runtimes
{% endhighlight %}

The platform responds by returning the following information: 

{% highlight json %}
{
  "success": true,
  "created": "2015-12-25T12:06:45.731",
  "id": "20acd9ca-2532-433d-9be2-83608f7f2686",
  "definition": {
    "name": "runtime1",
    "owner": "fb7ee9d5-2ee1-4c63-9a95-2edb7f4f7c04",
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
      }
    ], "links": [{
      "from": "generator1", "to": "log1"
    }]
  }
}
{% endhighlight %}

The UUID's and the created time in your response may vary from the ones shown here.
The runtime has now been created.

--------------------------

### <a name="startruntime"></a>Start the runtime

The runtime is now created but is not started yet. To start the runtime, issue a PATCH commmand as follows: 

{% highlight bash %}
curl -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    --user neo:thematrix \
    --request PATCH \
    --data '{ "status": "start" }' \
    http://127.0.0.1:8000/api/runtimes/runtime1
{% endhighlight %}

The platform responds with: 

{% highlight json %}
{
    "action": "Start runtime",
    "name": "runtime1",
    "owner": "neo",
    "success": true,
    "time": "2015-12-29T16:21:58.752"
}
{% endhighlight %}

The start time in your response may vary from the one shown here. The runtime is now started and will generate a "Hello, World!" every second, and it will write these messages to "/tmp/runtime1.log".

--------------------------

### <a name="investigateoutput"></a>Investigate the output

To see the output of the platform, issue the following command:

{% highlight bash %}
tail -f /tmp/runtime1.log
{% endhighlight %}

If everything was set up right, this will show the following output, one new line per second:

{% highlight bash %}
{ "field1": "Hello, World!" }
{ "field1": "Hello, World!" }
{ "field1": "Hello, World!" }
{ "field1": "Hello, World!" }
{ "field1": "Hello, World!" }
{ "field1": "Hello, World!" }
...
{% endhighlight %}

There we have it! The output of our very first Coral pipeline.


