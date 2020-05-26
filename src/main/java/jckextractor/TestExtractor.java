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
import java.util.regex.Pattern;

/**
 *
 * @author zzambers
 */
public class TestExtractor {

    private static final Pattern pkgPattern = Pattern.compile("^\\s*package\\s+([A-Za-z0-9$_.-]+)\\s*;");
    private static final Pattern javaSrcPattern = Pattern.compile("\"([A-Za-z0-9$_.-]+\\.java)\"");

    public static String getPackage(Path path) throws IOException {
        return FileUtil.findPatternFirst(path, pkgPattern);
    }

    public static Set<String> getJavaSrcs(Path path) throws IOException {
        return FileUtil.findPattern(path, javaSrcPattern);
    }

    /* not all src files are stored in correct directory structure according to
       their package, this method creates that structure and links source files
       from there */
    public static void createdFixedSrcTree(final Path src, final Path dst, final boolean recursive) throws IOException {
        FileVisitor<Path> fv = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path t, BasicFileAttributes bfa) throws IOException {
                if (recursive == false && !t.equals(src)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path t, BasicFileAttributes bfa) throws IOException {
                String filename = t.getFileName().toString();
                if (filename.equals("module-info.java")) {
                    return FileVisitResult.SKIP_SIBLINGS;
                }
                if (filename.endsWith(".java")) {
                    String pkg = getPackage(t);
                    if (pkg != null) {
                        Path pkgDirRel = FileUtil.getPath(dst.getFileSystem(), pkg.split("[.]"));
                        Path pkgDir = dst.resolve(pkgDirRel);
                        Files.createDirectories(pkgDir);
                        Path linkFile = pkgDir.resolve(filename);
                        if (!Files.exists(linkFile)) {
                            Files.createSymbolicLink(linkFile, t);
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }

        };
        Files.walkFileTree(src, fv);
    }

    public static void extractTest(Options options) throws Exception {
        Set<String> depsStrings = new HashSet();
        List<File> javaSrcFiles = new ArrayList();
        List<Path> shellScripts = new ArrayList();
        boolean hasNatives = false;

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(options.testSrcDir)) {
            for (Path p : dirStream) {
                if (!Files.isDirectory(p)) {
                    String name = p.toString();
                    if (name.endsWith(".java")) {
                        javaSrcFiles.add(p.toFile());
                    } else if (name.endsWith(".c")) {
                        hasNatives = true;
                    } else if (name.endsWith(".ksh")) {
                        shellScripts.add(p);
                    }
                    depsStrings.add(name);
                }
            }
        }

        Path inputSrcDir = options.jckDir.resolve("src");
        /*
        Path testSrcDir2 = inputSrcDir.resolve(options.jckDir.relativize(options.testSrcDir));
        if (Files.isDirectory(testSrcDir2)) {
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(options.testSrcDir)) {
                for (Path p : dirStream) {
                    String name = p.toString();
                    if (!Files.isDirectory(p)) {
                        if (name.endsWith(".java")) {
                            javaSrcFiles.add(p.toFile());
                        }
                        depsStrings.add(name);
                    }
                }
            }
        }
         */

        List<File> srcDirs = new ArrayList();
        srcDirs.add(inputSrcDir.toFile());
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(inputSrcDir)) {
            for (Path p : dirStream) {
                if (Files.isDirectory(p)) {
                    String name = p.getFileName().toString();
                    if (name.startsWith("jck.") && !name.endsWith(".module")) {
                        srcDirs.add(p.toFile());
                    }
                }
            }
        }

        Path p = Files.createTempDirectory("jck-extr");
        try {
            /* test files in correct dir structure */
            Path testSrcDirFixed = p.resolve("tests");
            createdFixedSrcTree(options.testSrcDir, testSrcDirFixed, true);
            Path currentDir = options.testSrcDir.getParent();
            while (!currentDir.equals(options.jckDir)) {
                createdFixedSrcTree(currentDir, testSrcDirFixed, false);
                currentDir = currentDir.getParent();
            }
            srcDirs.add(testSrcDirFixed.toFile());

            /* files from src/test in correct dir structure */
            Path inputSrcTestDir = inputSrcDir.resolve("tests");
            if (Files.isDirectory(inputSrcTestDir)) {
                Path srcTestDirFixed = p.resolve("src-tests");
                Files.createDirectory(srcTestDirFixed);
                createdFixedSrcTree(inputSrcTestDir, srcTestDirFixed, true);
                srcDirs.add(srcTestDirFixed.toFile());
            }

            /* Find dependencies*/
            DependenciesGetter.getDependencies(depsStrings, javaSrcFiles, srcDirs);

            /* Convert symbolic links */
            Set set2 = new HashSet();
            FileSystem fs = options.jckDir.getFileSystem();
            for (String depString : depsStrings) {
                Path srcFile = fs.getPath(depString);
                if (Files.isSymbolicLink(srcFile)) {
                    srcFile = Files.readSymbolicLink(srcFile);
                }
                set2.add(srcFile.toString());
            }
            depsStrings = set2;
        } finally {
            FileUtil.recursiveDelete(p);
        }

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
            FileUtil.recursiveCopy(inputSrcShareDir, outputSrcDir);
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
        Path htmlFile;
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
        if (testUrl.startsWith("/")) {
            testUrl = testUrl.substring(1);
        }
        if (testUrl.startsWith("tests/")) {
            testUrl = testUrl.substring(6);
        }
        Path jckTestsDir = jckDir.resolve("tests");
        if (testUrl.endsWith(".html")) {
            Path htmlFile = jckTestsDir.resolve(testUrl.replace("/", fs.getSeparator()));
            if (!Files.exists(htmlFile)) {
                options.htmlFile = htmlFile;
            }
            int slashIndex = testUrl.lastIndexOf('/');
            if (slashIndex > 0) {
                testUrl = testUrl.substring(0, slashIndex);
            }
        }
        Path testSrcDir = jckTestsDir.resolve(testUrl.replace("/", fs.getSeparator()));
        if (!Files.isDirectory(testSrcDir)) {
            System.err.println("ERR: Wrong test name: " + options.testNameArg);
            System.exit(1);
        }
        options.testSrcDir = testSrcDir.toAbsolutePath();

        return options;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        Options options = processArgs(args);
        extractTest(options);
    }

}
