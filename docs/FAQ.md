---
title: FAQ
layout: default
---

## FAQ
<br>

#### How fast is the platform?

We think the platform is very fast, but this depends on your expectations and on the specific action executed. It takes approximately 10 milliseconds to return a JSON representation of a runtime currently on the platform, including authorization and authentication. It takes approximately 100 milliseconds to create a new runtime. 

These are just actions that relate to the "control API", i.e. actions that users manually perform. When data actually flows through the system, performance depends on the specific actor that gets triggered but an actor task typically takes in the order of milliseconds to complete. 

We have performed a speed test for [Example 3](GettingStarted-Examples.html#example3) and we have obtained a processing speed of around 75,000 messages per second on a single machine.
<br>
<br>

#### Why is the platform called *Coral*?

The platform is called Coral because the ocean flows through a coral reef, just like data flows through the Coral platform. Additionally, it creates the opportunity to make a nice website with logos of fishes.
Also, it reminds us of holiday. Good stuff.
<br>
<br>

#### What kind of problems can Coral deal with?

If the problem deals with events that can be expressed in terms of JSON objects, Coral can handle it. This is true for almost all events. At this moment, however, we do not have a sufficiently large set of transformation actors to truly handle all problems, but in principle, Coral can deal with it if it concerns JSON events.
<br>
<br>

#### Doesn't the platform become slow because of the HTTP API layer? Wouldn't it just be way easier to directly hard-code actors together? Or: Wow, that's a complicated way to write "Hello, world" to a file!

Although there is indeed a certain overhead involved with a RESTful interface like that of Coral, in our opinion, the benefits far outweigh the disadvantages. The API is only used when users interact with the system, and is not used when messages actually flow through the system. Besides, the API is very fast...

Remember that new runtimes can be created and started when the platform *is running*. If you manually code your actors together, you have to first link the coral libraries to your code, create some actors or a pipeline, compile the system, create a jar, start the jar and wait for the output. You would have to do this every time you create a new runtime. We have aimed to create a "platform as a service"-experience, where the end user does not have to bother with performing all these steps and can simply call the API.
<br>
<br>

#### Why do you use JSON as message format? This seems inefficient, it is much better to use *(enter other mechanism here)* for this.

The problem is that we do not know beforehand how your data will look. As mentioned in the [Architecture](architecture.html) section, JSON offers the advantage of flexibility, dynamic declaration and serializability. If we would have used [case classes](http://docs.scala-lang.org/tutorials/tour/case-classes.html) (a kind of Scala class) for actor communication, for example, we would have to know *at compile time* what your messages look like. Besides, if we would have used case classes, these would have to be serialized and deserialized as well when crossing machine boundaries. A JSON object is, in essence, a `Map[String, Any]`, which can handle any data format required. Any other mechanism would have to fulfill the same demands as JSON is capable of fulfilling.

An additional benefit of using JSON everywhere is that there is no difference between messages inside the system and messages that are returned to the user of an API. This way, a uniform interface of all communications within the system and to the outside world can be created.
<br>
<br>

#### Did you investigate technology \<x\>? Isn't this technology capable of what you want to achieve?

We have explored several alternative technologies before starting with this platform. Almost all currently available solutions had at least one of these problems:

- **Not linearly scalable**: Not designed for true linear scalability.
- **Master-slave architectures**: One node being more important than other nodes.
- **Not resilient**: Not designed for failure.
- **Not fast enough**: Think Hadoop and map/reduce. Nice, but way too slow.
- **Coding over configuration**: Compiling, linking and running to create a new runtime.
- **No RESTful API**: Or at least, not built from an "API-first" perspective.

So we decided to create our own system from scratch. Fun!