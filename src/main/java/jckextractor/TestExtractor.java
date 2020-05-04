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
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author zzambers
 */
public class TestExtractor {

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

        DependenciesGetter.getDependencies(depsStrings, javaSrcFiles, srcDirs);

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
