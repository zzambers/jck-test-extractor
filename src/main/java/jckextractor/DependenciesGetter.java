/*
 * The MIT License
 *
 * Copyright 2020 zzambers.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jckextractor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.ForwardingJavaFileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 *
 * @author zzambers
 */
public class DependenciesGetter {

    public static void getDependencies(Set<String> deps, Iterable<File> sources, Iterable<File> srcPath) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        File tmp = File.createTempFile("clses", null);
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            tmp.delete();
            tmp.mkdir();

            ArrayList<File> classOutput = new ArrayList();
            classOutput.add(tmp);

            fileManager.setLocation(StandardLocation.SOURCE_PATH, srcPath);
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, classOutput);

            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sources);

            Set dependencies = new HashSet();
            try (JavaFileManager manager = new MonitoringFileManager(fileManager, dependencies)) {
                compiler.getTask(null, manager, null, null, null, compilationUnits).call();
            }

            for (Object o : dependencies) {
                deps.add(((JavaFileObject) o).getName());
            }
        } finally {
            FileUtil.recursiveDelete(tmp.toPath());
        }
    }

    static class MonitoringFileManager extends ForwardingJavaFileManager<JavaFileManager> {

        final Set set;

        public MonitoringFileManager(JavaFileManager m, Set set) {
            super(m);
            this.set = set;
        }

        public MonitoringFileManager(JavaFileManager m) {
            this(m, null);
        }

        @Override
        public boolean isSameFile(FileObject a, FileObject b) {
            /*
                required because:
                https://hg.openjdk.java.net/jdk8u/jdk8u/langtools/file/4c4c8a86bcb2/src/share/classes/com/sun/tools/javac/file/JavacFileManager.java#l646
             */
            if (a instanceof MonitoringJavaFileObject) {
                a = ((MonitoringJavaFileObject) a).file;
            }
            if (b instanceof MonitoringJavaFileObject) {
                b = ((MonitoringJavaFileObject) b).file;
            }
            return super.isSameFile(a, b);
        }

        @Override
        public String inferBinaryName(JavaFileManager.Location location, JavaFileObject file) {
            /*
                required because:
                https://hg.openjdk.java.net/jdk8u/jdk8u/langtools/file/4c4c8a86bcb2/src/share/classes/com/sun/tools/javac/file/JavacFileManager.java#l640
             */
            if (file instanceof MonitoringJavaFileObject) {
                file = ((MonitoringJavaFileObject) file).file;
            }
            return super.inferBinaryName(location, file);
        }

        @Override
        public FileObject getFileForInput(JavaFileManager.Location location, String packageName, String relativeName) throws IOException {
            /*
            if (location.equals(StandardLocation.SOURCE_PATH)) {
            }
             */

            FileObject fileObject = super.getFileForInput(location, relativeName, relativeName);
            return fileObject;
        }

        @Override
        public JavaFileObject getJavaFileForInput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind) throws IOException {
            JavaFileObject file = super.getJavaFileForInput(location, className, kind);
            if (file == null) {
                return null;
            }
            if (location.equals(StandardLocation.SOURCE_PATH)) {
                return new MonitoringJavaFileObject(file);
            }
            return file;
        }

        @Override
        public Iterable list(JavaFileManager.Location location, String packageName, Set kinds, boolean recurse) throws IOException {
            Iterable<JavaFileObject> iter = super.list(location, packageName, kinds, recurse);
            if (location.equals(StandardLocation.SOURCE_PATH)) {
                ArrayList list = new ArrayList();
                for (JavaFileObject file : iter) {
                    list.add(new MonitoringJavaFileObject(file, set));
                }
                return list;
            }
            return iter;
        }

    }

    static class MonitoringJavaFileObject extends ForwardingJavaFileObject<JavaFileObject> {

        final JavaFileObject file;
        final Set set;

        public MonitoringJavaFileObject(JavaFileObject f, Set set) {
            super(f);
            this.file = f;
            this.set = set;
        }

        public MonitoringJavaFileObject(JavaFileObject f) {
            this(f, null);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            if (set != null) {
                set.add(file);
            }
            return super.getCharContent(ignoreEncodingErrors);
        }

        @Override
        public InputStream openInputStream() throws IOException {
            if (set != null) {
                set.add(file);
            }
            return super.openInputStream();
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            if (set != null) {
                set.add(file);
            }
            return super.openReader(ignoreEncodingErrors);
        }

    }

}
