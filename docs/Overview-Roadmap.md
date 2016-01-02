---
title: Roadmap
layout: default
topic: Overview
order: 4
---

## Roadmap

The current release of Coral is a so-called "Minimum Viable Product", meaning that it is stable and it has a basic set of functionality, but it is not as rich and mature yet as we would hope to achieve.

### More actors

There are currently only a very limited set of actors available, which can be combined to create some interesting tutorial examples but are not yet very useful in real-world scenarios. The next step is to add more actors to the system which make the Coral platform useful in practice.

### Connectors

Currently, and by design, only Kafka is supported. However, if it may turn out to be necessary to connect to other, less cool event buses out there, this is something that might need to be implemented. Currently, this requirement does not exist, however. 

### Machine Learning

More advanced models are planned in future releases. Among those are training and online scoring with linear regression, decision trees, cluster analysis, neural networks and bayesian networks. These models can then be updated after every event that comes in, or they can be updated after a certain period of time.

### Spark

Currently, there is no support for calculations on a Spark cluster. This will be needed in the future because the models that can be trained on the platform are expected to be too calculation-intensive to be calculated on a single machine. Therefore, Spark support is planned.

### User interface

Although the average programmer can likely handle an RESTful API like we have defined, it is not as user-friendly as we would like to. Therefore, a user interface is planned which makes drag-and-drop creation of runtimes possible.