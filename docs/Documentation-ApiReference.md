---
title: API Reference
layout: default
topic: Documentation
order: 1
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

## API reference

- [Top-level entities](#toplevel)
- [Referring to runtimes](#referruntimes)
- [Referring to other entities](#referentities)
- [API](#api)
  - [Platform](#platform)
  - [Runtimes](#runtimes)
  - [Actors](#actors)
  - [Users](#users)
  - [Permissions](#permissions)
  - [Projects](#projects)

<br>
        
<div class="alert alert-danger" role="alert">
  <span class="glyphicon glyphicon-exclamation-sign" aria-hidden="true"></span>
  <span class="sr-only">Note</span>
  <strong>WARNING</strong>: Currently, a large part of this API is not implemented yet or may not work as stated. Some calls may even completely crash the system, so use this at your own risk. You have been warned... However, this <i>is</i> the future interface that we are aiming for.</a>
</div>

### <a name="toplevel"></a>Top-level entities

Below you can find an overview of the complete API of Coral. The following set of "top-level entities" are present in Coral:

- [Platform](#platform)  
Platform calls concern all platform and cluster-level interactions that deal with Coral as a whole and not the individual runtimes. You need to have special administrator privileges to change and look up platform settings.
- [Runtimes](#runtimes)  
Runtime calls deal with individual runtimes on the platform. You need to have permission to access a specific runtime. 
- [Actors](#actors)  
Actor calls deal with individual actors within a certain runtime. You need to have permission to access the runtime in which the actor resides.
- [Users](#users)  
User calls deal with creating and removing users and looking up user information. You need to have special administrator privileges to add and remove users from the platform.
- [Permissions](#permissions)  
Permission calls deal with creating, removing and changing user permissions. You need to have special administrator privileges to create, remove and change user permissions.
- [Projects](#projects)  
Project calls deal with creating, removing and changing project definitions. Currently, the project calls are not implemented yet but will be implemented in a future release.


--------------------------

###<a name="referruntimes"></a> Referring to runtimes

In the Coral platform, there are three ways to refer to runtimes:

- **By name**  
Access the runtime by name. If you do this, the platform implicitly assumes the current unique user name prefixed before the runtime name: `/api/runtimes/runtime1` becomes `/api/runtimes/neo-runtime1` if the current unique user name is "neo". In the case of [accept-all authentication](Overview-Architecture.html#security), an implicit user with unique name "coral" is assumed.

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Example: 

<div class="alert alert-info" role="alert" style="margin-left: 40px;"><strong>PATCH</strong> /api/runtimes/runtime1</div>

- **By owner and name**  
Access the runtime by owner "dash" name. This is the internal unique name that Coral keeps to refer to runtimes. 

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Example: 

<div class="alert alert-info" role="alert" style="margin-left: 40px;"><strong>PATCH</strong> /api/runtimes/neo-runtime1</div>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;This method is required if referring to a runtime of which the current user is not the 
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;owner. In the case of accept-all authentication, "coral" is used as the user name, but it is not &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;necessary to explicitly state this.

- **By UUID**  
This method refers to the runtime by its unique UUID assigned when creating the runtime. The user name should not be put before the UUID when referring to a runtime like this; the UUID is already unique across the entire platform.

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Example: 

<div class="alert alert-info" role="alert" style="margin-left: 40px;"><strong>PATCH</strong> /api/runtimes/130e4c2d-fc8b-44fb-b1f2-02fc0e9f2391</div>

--------------------------

### <a name="referentities"></a>Referring to other entities

Permissions are always referred to by their UUID. Users can be referred to by their UUID or their unique user name. Actors are always referred to by their unique name in a runtime.

--------------------------

### API

For each API call, the following information is provided:

- The method (HTTP verb)
- The endpoint
- A description of the action
- The JSON input format
- An example of the JSON input format
- The JSON result format
- An example of the JSON result format
- Possible error codes

Click on the link to go to the details of a specific call.



###  <a name="platform"></a>Platform

Show platform statistics, change platform settings and add and remove nodes from the cluster.

URL | action
---: | :--
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[POST&nbsp;/api/cluster]() | Join a node to or remove a node from the cluster
[GET&nbsp;/api/stats]() | Show statistics for the entire platform
[GET&nbsp;/api/settings]() | Returns all settings of the platform
[GET&nbsp;/api/cluster]() | Returns all machines in the Coral platform

<br>

###  <a name="runtimes"></a>Runtimes

Create, start and stop runtimes and get information and statistics of runtimes.

URL | action
---: | :---
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[POST&nbsp;/api/runtimes](API-POST-runtime.html) | Create a new runtime&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
[GET&nbsp;/api/runtimes/`<id>`]() | Gets the definition of runtime `<id>`
[GET&nbsp;/api/runtimes/`<id>`/actors]() | Gets all actors for runtime `<id>`
[GET&nbsp;/api/runtimes/`<id>`/links]() | Gets all links for runtime `<id>`
[GET&nbsp;/api/runtimes/`<id>`/stats]() | Gets statistics about runtime `<id>`
[PATCH&nbsp;/api/runtimes/`<id>`](API-PATCH-runtime.html) | Starts or stops runtime `<id>`
[DELETE&nbsp;/api/runtimes/`<id>`]() | Deletes runtime `<id>`
[DELETE&nbsp;/api/runtimes]() | Delete all runtimes

<br>

###  <a name="actors"></a>Actors

Interact with individual actors in a runtime and get actor statistics.

URL | action
---: | :--
[POST&nbsp;/api/runtimes/`<rid>`/actors/`<aid>`]() | Post JSON to actor `<aid>` in runtime `<rid>`
[POST&nbsp;/api/runtimes/`<rid>`/actors/`<aid>`/shunt]() | Post JSON to `<aid>` in `<rid>` and return
[GET&nbsp;/api/runtimes/`<id>`/actors]() | Gets information of all actors in runtime `<id>`
[GET&nbsp;/api/runtimes/`<rid>`/actors/`<aid>`/stats]() | Get stats about actor `<aid>` in runtime `<rid>`

<br>

###  <a name="users"></a>Users

Add and remove users from the platform.

URL | action
---: | :--
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[POST&nbsp;/api/users]() | Post a new user with permissions&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
[GET&nbsp;/api/users/`<id>`]() | Returns information for user `<id>`
[GET&nbsp;/api/users]() | Returns all users on the platform
[DELETE&nbsp;/api/users/`<id>`]() | Deletes user `<id>`

<br>

###  <a name="permissions"></a>Permissions

Add, remove and change permissions for users on the platform.

URL | action
---: | :---
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[POST&nbsp;/api/runtimes/`<id>`/permissions]() | Add a new permission to runtime `<id>`
[GET&nbsp;/api/runtimes/`<id>`/permissions]() | Gets all permissions for runtime `<id>`
[PATCH&nbsp;/api/runtimes/`<id>`/permissions]() | Set permission granted/denied in runtime `<id>`
[DELETE&nbsp;/api/runtimes/`<id>`/permissions]() | Delete a permission from runtime `<id>`


<br>

###  <a name="projects"></a>Projects

Create, delete or change projects.



URL | action
---: | :--
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[POST&nbsp;/api/projects]() | Create a new project&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
[GET&nbsp;/api/projects]() | Get all projects on the platform
[PATCH&nbsp;/api/projects/`<id>`]() | Update a project definition of project `<id>`
[DELETE&nbsp;/api/projects/`<id>`]() | Delete project `<id>`