JAVA=$(JAVA_HOME)/bin/java
JAVAC=$(JAVA_HOME)/bin/javac
#CC=gcc

SCRIPTS=$(shell find tests -type f -name '*.ksh' )
LIBS=$(shell [ -d src/share/lib/jni ] && echo 'lib/libjckjni.so' ; [ -d src/share/lib/jvmti ] && echo 'lib/libjckjvmti.so' )

.PHONY: test clean $(SCRIPTS)

test: $(SCRIPTS)

$(SCRIPTS): %.ksh: | classes $(LIBS)
	export LD_LIBRARY_PATH=$$(pwd)/lib ; \
	sh "$@"

classes:
	mkdir classes
	$(JAVAC) -d classes -g $$( find src tests -type f -name '*.java' )

lib:
	mkdir lib

lib/libjckjni.so: | lib
	$(CC) -fPIC -shared -Isrc/share/lib/jni/include -Isrc/share/lib/jni/include/amd64 -o lib/libjckjni.so src/share/lib/jni/jckjni.c

lib/libjckjvmti.so: | lib
	$(CC) -fPIC -shared -I. -Isrc/share/lib/jvmti/include -Isrc/share/lib/jni/include/amd64 -Isrc/share/lib/jni/include -o lib/libjckjvmti.so src/share/lib/jvmti/jckjvmti.c

clean:
	rm -rf classes
	rm -rf lib
