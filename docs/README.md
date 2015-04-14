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
## Setup

Coral's documentation uses Jekyll to build a website out of markdown pages. Prerequisites:

1. You need [Ruby](https://www.ruby-lang.org/) installed on your machine (run `ruby --version` to check)
2. Install [Bundler](http://bundler.io/) by running `sudo gem install bundler`
3. To install Jekyll and its dependencies, change to the `docs` directory and run `bundle install`

To serve the website on [localhost:4000](http://localhost:4000/):

    bundle exec jekyll serve --watch --baseurl

To compile the website in the \_site directory, execute:

    bundle exec jekyll build

To test the site, run:

    bundle exec jekyll serve --watch --baseurl