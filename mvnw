#!/bin/bash
set -euo pipefail
export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
export MAVEN_HOME="$HOME/.sdkman/candidates/maven/current"
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
cd /mnt/d/IdeaProjects/reansonix-java
exec mvn "$@"
