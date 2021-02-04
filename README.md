##### Small example how to extract test, lets say from JCK15
```
 JAVA_HOME=/usr/lib/jvm/java-15-openjdk-15.0.1.9-9.rolling.fc33.x86_64/ mvn clean install
 /usr/lib/jvm/java-15-openjdk-15.0.1.9-9.rolling.fc33.x86_64/bin/java -jar target/jck-test-extractor-1.0-SNAPSHOT.jar --jck-dir /GARBAGE/JCK-runtime-15 --output-dir /GARBAGE/ex --test api/java_lang/SecurityManager/CtorNonCheck.html
 ```
