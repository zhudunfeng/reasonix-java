export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=/bin:/mnt/d/devtools/apache-maven-3.5.0/bin:
cd /mnt/d/IdeaProjects/reansonix-java
mvn clean compile -DskipTests 2>&1
