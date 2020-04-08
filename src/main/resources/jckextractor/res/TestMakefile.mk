JAVA=$(JAVA_HOME)/bin/java
JAVAC=$(JAVA_HOME)/bin/javac
#CC=gcc

SCRIPTS=$(shell find tests -type f -name '*.ksh' )

.PHONY: test clean $(SCRIPTS)

test: $(SCRIPTS)

$(SCRIPTS): %.ksh: | classes lib
	export LD_LIBRARY_PATH=$$(pwd)/lib ; \
	sh "$@"

classes:
	mkdir classes
	$(JAVAC) -d classes -g -sourcepath src $$( find tests -type f -name '*.java' )

lib:
	mkdir lib
	! [ -f src/share/lib/jni/jckjni.c ] || $(CC) -fPIC -shared -Isrc/share/lib/jni/include -Isrc/share/lib/jni/include/amd64 -o lib/libjckjni.so 
	! [ -f src/share/lib/jvmti/jckjvmti.c ] || $(CC) -fPIC -shared -I. -Isrc/share/lib/jvmti/include -Isrc/share/lib/jni/include/amd64 -Isrc/share/lib/jni/include -o lib/libjckjvmti.so src/share/lib/jvmti/jckjvmti.c

clean:
	rm -rf classes
	rm -rf lib
