#!/usr/bin/env bash

# Load tests the system with a total of 10.000 requests
# and 3 concurrent connections
ab -n 10000 -c 3 -k http://localhost:80/coral/api/in