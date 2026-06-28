#!/bin/bash
set -e
cd /mnt/d/IdeaProjects/reansonix-java
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:/usr/share/maven/bin:$PATH"
mvn -DskipTests compile
echo OK
