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
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
public class TestExtractor {

    public static void recursiveCopy(Path srcDir, Path targetDir) throws IOException {
        final Path parent = srcDir.getParent();
        final Path srcRelativizeDir = (parent != null) ? parent : srcDir;
        FileVisitor<Path> fv = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path t, BasicFileAttributes bfa) throws IOException {
                Files.createDirectories(targetDir.resolve(srcRelativizeDir.relativize(t)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path t, BasicFileAttributes bfa) throws IOException {
                Files.copy(t, targetDir.resolve(srcRelativizeDir.relativize(t)));
                return FileVisitResult.CONTINUE;
            }
        };
        Files.walkFileTree(srcDir, fv);
    }

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
            tmp.delete();
        }
    }

    public static void extractTest(Options options) throws Exception {
        Set<String> depsStrings = new HashSet();
        List<File> javaSrcFiles = new ArrayList();
        boolean hasNatives = false;

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(options.testSrcDir)) {
            for (Path p : dirStream) {
                if (!Files.isDirectory(p)) {
                    String name = p.toString();
                    if (name.endsWith(".java")) {
                        javaSrcFiles.add(p.toFile());
                    } else if (name.endsWith(".c")) {
                        hasNatives = true;
                    }
                    depsStrings.add(name);
                }
            }
        }

        Path inputSrcDir = options.jckDir.resolve("src");
        List<File> srcDirs = new ArrayList();
        srcDirs.add(inputSrcDir.toFile());
        getDependencies(depsStrings, javaSrcFiles, srcDirs);

        FileSystem fs = options.jckDir.getFileSystem();
        for (String depString : depsStrings) {
            Path srcFile = fs.getPath(depString);
            Path destFile = options.outputDir.resolve(options.jckDir.relativize(srcFile));
            Path destDir = destFile.getParent();
            Files.createDirectories(destDir);
            Files.copy(srcFile, destFile, StandardCopyOption.COPY_ATTRIBUTES);
        }

        if (hasNatives) {
            Path outputSrcDir = options.outputDir.resolve("src");
            Path inputSrcShareDir = inputSrcDir.resolve("share");
            Files.createDirectories(outputSrcDir);
            recursiveCopy(inputSrcShareDir, outputSrcDir);
        }

        try (InputStream is = TestExtractor.class.getClassLoader().getResourceAsStream("jckextractor/res/TestMakefile.mk")) {
            Files.copy(is, options.outputDir.resolve("Makefile"));
        }

    }

    public static class Options {

        String jckDirArg;
        String outputDirArg;
        String testNameArg;

        Path jckDir;
        Path outputDir;
        Path testSrcDir;
    }

    public static void printHelp() {
        String help
                = "Args: \n"
                + "  --help                prints this help\n"
                + "  --jck-dir [DIR]       directory with unpacked jck (unpacked with -i shell_scripts)\n"
                + "  --output-dir [DIR]    directory where to place extracted test\n"
                + "  --test [TEST]         name of the test to extract\n";
        System.out.print(help);
    }

    public static Options processArgs(String[] args) {
        Options options = new Options();
        for (int i = 0; i < args.length; ++i) {
            switch (args[i]) {
                case "--help":
                    printHelp();
                    System.exit(0);
                    break;
                case "--jck-dir":
                    options.jckDirArg = args[++i];
                    break;
                case "--output-dir":
                    options.outputDirArg = args[++i];
                    break;
                case "--test":
                    options.testNameArg = args[++i];
                    break;
                default:
                    System.err.println("ERR: Unknown arg: " + args[i]);
                    printHelp();
                    System.exit(1);
            }
        }

        if (options.jckDirArg == null) {
            System.err.println("ERR: Missing: --jck-dir arg");
            System.exit(1);
        }
        if (options.outputDirArg == null) {
            System.err.println("ERR: Missing: --output-dir arg");
            System.exit(1);
        }
        if (options.testNameArg == null) {
            System.err.println("ERR: Missing: --test arg");
            System.exit(1);
        }

        FileSystem fs = FileSystems.getDefault();
        /* Checks for jck-dir */
        Path jckDir = fs.getPath(options.jckDirArg);
        if (!Files.isDirectory(jckDir)
                || !Files.isDirectory(jckDir.resolve("src"))
                || !Files.isDirectory(jckDir.resolve("tests"))) {
            System.err.println("ERR: Wrong jck-dir: " + options.jckDirArg);
            System.exit(1);
        }
        options.jckDir = jckDir.toAbsolutePath();

        /* Checks for output-dir */
        Path outputDir = fs.getPath(options.outputDirArg);
        if (!Files.isDirectory(outputDir)) {
            System.err.println("ERR: Wrong output-dir: " + options.outputDir);
            System.exit(1);
        }
        options.outputDir = outputDir.toAbsolutePath();

        /* Checks Test name */
        String testUrl = options.testNameArg;
        int hashIndex = testUrl.lastIndexOf('#');
        if (hashIndex > 0) {
            testUrl = testUrl.substring(0, hashIndex);
        }
        if (testUrl.endsWith(".html")) {
            int slashIndex = testUrl.lastIndexOf('/');
            if (slashIndex > 0) {
                testUrl = testUrl.substring(0, slashIndex);
            }
        }
        if (testUrl.startsWith("/")) {
            testUrl = testUrl.substring(1);
        }
        Path testSrcDir = jckDir.resolve(testUrl.replace("/", fs.getSeparator()));
        if (!Files.isDirectory(testSrcDir)) {
            System.err.println("ERR: Wrong test name: " + options.testNameArg);
            System.exit(1);
        }
        options.testSrcDir = testSrcDir;

        return options;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        Options options = processArgs(args);
        extractTest(options);
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
