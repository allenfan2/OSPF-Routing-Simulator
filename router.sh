#!/bin/bash

if [ ! -f router.class ]; then
    javac router.java
fi

java router $1 "$2" $3 $4

