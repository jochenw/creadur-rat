/*
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 */
package org.apache.rat;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.rat.license.ILicense;
import org.apache.rat.license.LicenseSetFactory;
import org.apache.rat.testhelpers.TestingLog;
import org.apache.rat.testhelpers.TextUtils;
import org.apache.rat.utils.DefaultLog;
import org.apache.rat.utils.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class OptionToolsTest {

    File baseDir;

    public OptionToolsTest() {
        baseDir = new File("target/optionTools");
        baseDir.mkdirs();
    }

    @FunctionalInterface
    public interface OptionTest {
        void test();
    }



    /**
     * This method is a known workaround for junit 5 issue #2811
     * {@link  https://github.com/junit-team/junit5/issues/2811 }
     */
    @AfterEach
    @EnabledOnOs(OS.WINDOWS)
    void cleanUp() {
        System.gc();
    }

    private static String longOpt(Option opt) {
        return "--" + opt.getLongOpt();
    }

    @Test
    public void testDeprecatedUseLogged() throws IOException {
        TestingLog log = new TestingLog();
        try {
            DefaultLog.setInstance(log);
            String[] args = {longOpt(OptionTools.DIR), "foo", "-a"};
            ReportConfiguration config = OptionTools.parseCommands(args, (o) -> {
            }, true);

        } finally {
            DefaultLog.setInstance(null);
        }
        log.assertContains("WARN: Option [-d, --dir] used.  Deprecated for removal since 0.17: Use '--'");
        log.assertContains("WARN: Option [-a] used.  Deprecated for removal since 0.17: Use '-A' or '--addLicense'");
    }

    @Test
    public void parseExclusionsTest() {
        final Optional<IOFileFilter> filter = OptionTools
                .parseExclusions(DefaultLog.getInstance(), Arrays.asList("", " # foo/bar", "foo", "##", " ./foo/bar"));
        assertThat(filter).isPresent();
        assertThat(filter.get()).isExactlyInstanceOf(NotFileFilter.class);
        assertFalse(filter.get().accept(baseDir, "./foo/bar" ), "./foo/bar");
        assertTrue(filter.get().accept(baseDir, "B.bar"), "B.bar");
        assertFalse(filter.get().accept(baseDir, "foo" ), "foo");
        assertTrue(filter.get().accept(baseDir, "notfoo"), "notfoo");
    }

    @Test
    public void testDefaultConfiguration() throws ParseException, IOException {
        String[] empty = {};
        CommandLine cl = new DefaultParser().parse(OptionTools.buildOptions(), empty);
        ReportConfiguration config = OptionTools.createConfiguration(DefaultLog.getInstance(), "", cl);
        ReportConfigurationTest.validateDefault(config);
    }

    @ParameterizedTest
    @MethodSource("exclusionsProvider")
    public void testParseExclusions(String pattern, List<IOFileFilter> expectedPatterns, List<String> logEntries) {
        TestingLog log = new TestingLog();
        Optional<IOFileFilter> filter = OptionTools.parseExclusions(log, Collections.singletonList(pattern));
        if (expectedPatterns.isEmpty()) {
            assertThat(filter).isEmpty();
        } else {
            assertInstanceOf(NotFileFilter.class, filter.get());
            String result = filter.toString();
            for (IOFileFilter expectedFilter : expectedPatterns) {
                TextUtils.assertContains(expectedFilter.toString(), result);
            }
        }
        assertEquals(log.isEmpty(), logEntries.isEmpty());
        for (String logEntry : logEntries) {
            log.assertContains(logEntry);
        }
    }

    public static Stream<Arguments> exclusionsProvider() {
        List<Arguments> lst = new ArrayList<>();

        lst.add(Arguments.of( "", Collections.emptyList(), Collections.singletonList("INFO: Ignored 1 lines in your exclusion files as comments or empty lines.")));

        lst.add(Arguments.of( "# a comment", Collections.emptyList(), Collections.singletonList("INFO: Ignored 1 lines in your exclusion files as comments or empty lines.")));

        List<IOFileFilter> expected = new ArrayList<>();
        String pattern = "hello.world";
        expected.add(new RegexFileFilter(pattern));
        expected.add(new NameFileFilter(pattern));
        lst.add(Arguments.of( pattern, expected, Collections.emptyList()));

        expected = new ArrayList<>();
        pattern = "[Hh]ello.[Ww]orld";
        expected.add(new RegexFileFilter(pattern));
        expected.add(new NameFileFilter(pattern));
        lst.add(Arguments.of( pattern, expected, Collections.emptyList()));

        expected = new ArrayList<>();
        pattern = "hell*.world";
        expected.add(new RegexFileFilter(pattern));
        expected.add(new NameFileFilter(pattern));
        expected.add(WildcardFileFilter.builder().setWildcards(pattern).get());
        lst.add(Arguments.of( pattern, expected, Collections.emptyList()));

        // see RAT-265 for issue
        expected = new ArrayList<>();
        pattern = "*.world";
        expected.add(new NameFileFilter(pattern));
        expected.add(WildcardFileFilter.builder().setWildcards(pattern).get());
        lst.add(Arguments.of( pattern, expected, Collections.emptyList()));

        expected = new ArrayList<>();
        pattern = "hello.*";
        expected.add(new NameFileFilter(pattern));
        expected.add(WildcardFileFilter.builder().setWildcards(pattern).get());
        lst.add(Arguments.of( pattern, expected, Collections.emptyList()));

        expected = new ArrayList<>();
        pattern = "?ello.world";
        expected.add(new NameFileFilter(pattern));
        expected.add(WildcardFileFilter.builder().setWildcards(pattern).get());
        lst.add(Arguments.of( pattern, expected, Collections.emptyList()));

        expected = new ArrayList<>();
        pattern = "hell?.world";
        expected.add(new RegexFileFilter(pattern));
        expected.add(new NameFileFilter(pattern));
        expected.add(WildcardFileFilter.builder().setWildcards(pattern).get());
        lst.add(Arguments.of( pattern, expected, Collections.emptyList()));

        expected = new ArrayList<>();
        pattern = "hello.worl?";
        expected.add(new NameFileFilter(pattern));
        expected.add(WildcardFileFilter.builder().setWildcards(pattern).get());
        lst.add(Arguments.of( pattern, expected, Collections.emptyList()));

        return lst.stream();
    }

    @ParameterizedTest
    @ArgumentsSource(OptionsProvider.class)
    public void testOptionsUpdateConfig(String name, OptionTest test) throws Exception {
        test.test();
    }

    static class OptionsProvider implements ArgumentsProvider, IOptionsProvider {

        final AtomicBoolean helpCalled = new AtomicBoolean(false);

        final Map<Option,OptionTest> testMap = new HashMap<>();

        File baseDir;

        public OptionsProvider() {
            baseDir = new File("target/optionTools");
            baseDir.mkdirs();
            testMap.put(OptionTools.ADD_LICENSE, this::addLicenseTest);
            testMap.put(OptionTools.ARCHIVE, this::archiveTest);
            testMap.put(OptionTools.STANDARD, this::standardTest);
            testMap.put(OptionTools.COPYRIGHT, this::copyrightTest);
            testMap.put(OptionTools.DIR, () -> {DefaultLog.getInstance().info(longOpt(OptionTools.DIR)+" has no valid test");});
            testMap.put(OptionTools.DRY_RUN, this::dryRunTest);
            testMap.put(OptionTools.EXCLUDE_CLI, this::excludeCliTest);
            testMap.put(OptionTools.EXCLUDE_FILE_CLI,this::excludeCliFileTest);
            testMap.put(OptionTools.FORCE, this::forceTest);
            testMap.put(OptionTools.HELP, () -> {
                String[] args = {longOpt(OptionTools.HELP)};
                try {
                    ReportConfiguration config = OptionTools.parseCommands(args, o -> helpCalled.set(true), true);
                    assertNull(config, "Should not have config");
                    assertTrue(helpCalled.get(), "Help was not called");
                } catch (IOException e) {
                    fail(e.getMessage());
                }
            });
            testMap.put(OptionTools.LICENSES, this::licensesTest);
            testMap.put(OptionTools.LIST_LICENSES, this::listLicensesTest);
            testMap.put(OptionTools.LIST_FAMILIES, this::listFamiliesTest);
            testMap.put(OptionTools.LOG_LEVEL, this::logLevelTest);
            testMap.put(OptionTools.NO_DEFAULTS, this::noDefaultsTest);
            testMap.put(OptionTools.OUT, this::outTest);
            testMap.put(OptionTools.SCAN_HIDDEN_DIRECTORIES, this::scanHiddenDirectoriesTest);
            testMap.put(OptionTools.STYLESHEET_CLI, this::styleSheetTest);
            testMap.put(OptionTools.XML, this::xmlTest);
        }

        private ReportConfiguration generateConfig(String[] args) throws IOException {
            helpCalled.set(false);
            ReportConfiguration config = OptionTools.parseCommands(args, o -> helpCalled.set(true), true);
            assertFalse(helpCalled.get(), "Help was called");
            return config;
        }

        @Override
        public void addLicenseTest() {
                String[] args = {longOpt(OptionTools.ADD_LICENSE)};
                try {
                    ReportConfiguration config =generateConfig(args);
                    assertTrue(config.isAddingLicenses());
                    config = generateConfig(new String[0]);
                    assertFalse(config.isAddingLicenses());
                } catch (IOException e) {
                    fail(e.getMessage());
                }
        }
        @Override
        public void archiveTest() {
                String[] args = {longOpt(OptionTools.ARCHIVE), null};
                try {
                    for (ReportConfiguration.Processing proc : ReportConfiguration.Processing.values()) {
                        args[1] = proc.name();
                        ReportConfiguration config = generateConfig(args);
                        assertEquals(proc, config.getArchiveProcessing());
                    }
                } catch (IOException e) {
                    fail(e.getMessage());
                }
        }
        @Override
        public void standardTest() {
            String[] args = {longOpt(OptionTools.STANDARD), null};
            try {
                for (ReportConfiguration.Processing proc : ReportConfiguration.Processing.values()) {
                    args[1] = proc.name();
                    ReportConfiguration config = generateConfig(args);
                    assertEquals(proc, config.getStandardProcessing());
                }
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }
        @Override
        public void copyrightTest() {
            try {
                String[] args = {longOpt(OptionTools.COPYRIGHT), "MyCopyright"};
                ReportConfiguration config =generateConfig(args);
                assertNull(config.getCopyrightMessage(), "Copyright without ADD_LICENCE should not work");
                args = new String[]{longOpt(OptionTools.COPYRIGHT), "MyCopyright", longOpt(OptionTools.ADD_LICENSE)};
                config = generateConfig(args);
                assertEquals("MyCopyright", config.getCopyrightMessage());
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }
        @Override
        public void dryRunTest() {
            try {
                String[] args = {longOpt(OptionTools.DRY_RUN)};
                ReportConfiguration config = generateConfig(args);
                assertTrue(config.isDryRun());
                args = new String[0];
                config = generateConfig(args);
                assertFalse(config.isDryRun());
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }

        @Override
        public void excludeCliTest() {
            String[] args = {longOpt(OptionTools.EXCLUDE_CLI), "*.foo", "[A-Z]\\.bar", "justbaz"};
            execCliTest(args);
        }


        private void execCliTest(String[] args) {
                try {
                    ReportConfiguration config = generateConfig(args);
                    IOFileFilter filter = config.getFilesToIgnore();
                    assertThat(filter).isExactlyInstanceOf(NotFileFilter.class);
                    assertFalse(filter.accept(baseDir, "some.foo" ), "some.foo");
                    assertFalse(filter.accept(baseDir, "B.bar"), "B.bar");
                    assertFalse(filter.accept(baseDir, "justbaz" ), "justbaz");
                    assertTrue(filter.accept(baseDir, "notbaz"), "notbaz");
                } catch (IOException e) {
                    fail(e.getMessage());
                }
        }

        @Override
        public void excludeCliFileTest() {
            File outputFile = new File(baseDir, "exclude.txt");
            try (FileWriter fw = new FileWriter(outputFile)) {
                fw.write("*.foo");
                fw.write(System.lineSeparator());
                fw.write("[A-Z]\\.bar");
                fw.write(System.lineSeparator());
                fw.write("justbaz");
                fw.write(System.lineSeparator());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String[] args = {longOpt(OptionTools.EXCLUDE_FILE_CLI), outputFile.getPath()};
            execCliTest(args);
        }

        @Override
        public void forceTest() {
                String [] args =  new String[] {longOpt(OptionTools.FORCE)};
                try {
                    ReportConfiguration config = generateConfig(args);
                    assertFalse(config.isAddingLicensesForced());
                    args = new String[]{longOpt(OptionTools.FORCE), longOpt(OptionTools.ADD_LICENSE)};
                    config = generateConfig(args);
                    assertTrue(config.isAddingLicensesForced());
                } catch (IOException e) {
                    fail(e.getMessage());
                }
        }

        @Override
        public void licensesTest() {
            String[] args = {longOpt(OptionTools.LICENSES), "src/test/resources/OptionTools/One.xml", "src/test/resources/OptionTools/Two.xml"};
            try {
                ReportConfiguration config = generateConfig(args);
                SortedSet<ILicense> set = config.getLicenses(LicenseSetFactory.LicenseFilter.ALL);
                assertTrue(set.size() > 2);
                assertTrue(LicenseSetFactory.search("ONE", "ONE", set).isPresent());
                assertTrue(LicenseSetFactory.search("TWO", "TWO", set).isPresent());

                args = new String[]{longOpt(OptionTools.LICENSES), "src/test/resources/OptionTools/One.xml", "src/test/resources/OptionTools/Two.xml", longOpt(OptionTools.NO_DEFAULTS)};
                config = generateConfig(args);
                set = config.getLicenses(LicenseSetFactory.LicenseFilter.ALL);
                assertEquals(2, set.size());
                assertTrue(LicenseSetFactory.search("ONE", "ONE", set).isPresent());
                assertTrue(LicenseSetFactory.search("TWO", "TWO", set).isPresent());
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }

        @Override
        public void listLicensesTest() {
            String[] args = {longOpt(OptionTools.LIST_LICENSES), null};
            for (LicenseSetFactory.LicenseFilter filter : LicenseSetFactory.LicenseFilter.values()) {
                try {
                    args[1] = filter.name();
                    ReportConfiguration config = generateConfig(args);
                    assertEquals(filter, config.listLicenses());
                } catch (IOException e) {
                    fail(e.getMessage());
                }
            }
        }

        @Override
        public void listFamiliesTest() {
            String[] args = {longOpt(OptionTools.LIST_FAMILIES), null};
            for (LicenseSetFactory.LicenseFilter filter : LicenseSetFactory.LicenseFilter.values()) {
                try {
                    args[1] = filter.name();
                    ReportConfiguration config = generateConfig(args);
                    assertEquals(filter, config.listFamilies());
                } catch (IOException e) {
                    fail(e.getMessage());
                }
            }
        }

        public void logLevelTest() {
            String[] args = {longOpt(OptionTools.LOG_LEVEL), null};
            Log.Level logLevel = ((DefaultLog) DefaultLog.getInstance()).getLevel();
            try {
                for (Log.Level level : Log.Level.values()) {
                    try {
                        args[1] = level.name();
                        ReportConfiguration config = generateConfig(args);
                        assertEquals(level, ((DefaultLog) DefaultLog.getInstance()).getLevel());
                    } catch (IOException e) {
                        fail(e.getMessage());
                    }
                }
            } finally {
                ((DefaultLog) DefaultLog.getInstance()).setLevel(logLevel);
            }
        }

        @Override
        public void noDefaultsTest() {
            String[] args = {longOpt(OptionTools.NO_DEFAULTS)};
            try {
                ReportConfiguration config = generateConfig(args);
                assertTrue(config.getLicenses(LicenseSetFactory.LicenseFilter.ALL).isEmpty());
                config = generateConfig(new String[0]);
                assertFalse(config.getLicenses(LicenseSetFactory.LicenseFilter.ALL).isEmpty());
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }

        @Override
        public void outTest() {
            File outFile = new File( baseDir, "outexample");
            String[] args = new String[] {longOpt(OptionTools.OUT), outFile.getAbsolutePath()};
            try {
                ReportConfiguration config = generateConfig(args);
                try (OutputStream os = config.getOutput().get()) {
                    os.write("Hello world".getBytes());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                try (BufferedReader reader = new BufferedReader( new InputStreamReader(Files.newInputStream(outFile.toPath())))) {
                    assertEquals("Hello world",reader.readLine());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }

        @Override
        public void scanHiddenDirectoriesTest() {
            String[] args = {longOpt(OptionTools.SCAN_HIDDEN_DIRECTORIES)};
            try {
                ReportConfiguration config = generateConfig(args);
                assertThat(config.getDirectoriesToIgnore()).isExactlyInstanceOf(FalseFileFilter.class);
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }

        @Override
        public void styleSheetTest() {
            String[] args = {longOpt(OptionTools.STYLESHEET_CLI), null};
            try {
                URL url = ReportTest.class.getResource("MatcherContainerResource.txt");
                if (url == null) {
                    fail("Could not locate 'MatcherContainerResource.txt'");
                }
                for (String sheet : new String[]{"target/optionTools/stylesheet.xlt", "plain-rat", "missing-headers", "unapproved-licenses", url.getFile()}) {
                    args[1] = sheet;
                    ReportConfiguration config = generateConfig(args);
                    assertTrue(config.isStyleReport());
                }
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }

        @Override
        public void xmlTest() {
            String[] args = {longOpt(OptionTools.XML)};
            try {
                ReportConfiguration config = generateConfig(args);
                assertFalse(config.isStyleReport());
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            List<Arguments> lst = new ArrayList<>();

            for (Option option : OptionTools.buildOptions().getOptions()) {
                if (option.getLongOpt() != null) {
                    String name = longOpt(option);
                    OptionTest test = testMap.get(option);
                    if (test == null) {
                        fail("Option "+name+" is not defined in testMap");
                    }
                    lst.add(Arguments.of(name, test));
                }
            }
            return lst.stream();
        }
    }
}
