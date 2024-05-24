/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rat.anttasks;

import org.apache.commons.cli.Option;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.WordUtils;
import org.apache.rat.IOptionsProvider;
import org.apache.rat.OptionTools;
import org.apache.rat.OptionToolsTest;
import org.apache.rat.ReportConfiguration;
import org.apache.rat.ReportTest;
import org.apache.rat.anttasks.BaseAntTask.AntOption;
import org.apache.rat.license.ILicense;
import org.apache.rat.license.LicenseSetFactory;
import org.apache.rat.tools.AntGenerator;
import org.apache.rat.utils.CasedString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


public class ReportOptionTest  {

    static File baseDir = new File("target/optionTest");
    static ReportConfiguration reportConfiguration;


    @BeforeAll
    public static void makeDirs() {
        baseDir.mkdirs();
    }

    @ParameterizedTest
    @ArgumentsSource(OptionsProvider.class)
    public void testOptionsUpdateConfig(String name, OptionToolsTest.OptionTest test) throws Exception {
        test.test();
    }

    public static class OptionTest extends Report {

        public OptionTest() {}

        @Override
        public void execute() {
            reportConfiguration = getConfiguration();
        }
    }

    static class OptionsProvider implements ArgumentsProvider, IOptionsProvider {

        final AtomicBoolean helpCalled = new AtomicBoolean(false);

        final Map<Option, OptionToolsTest.OptionTest> testMap = new HashMap<>();

        File baseDir;

        public OptionsProvider() {
            baseDir = new File("target/optionTools");
            baseDir.mkdirs();
            testMap.put(OptionTools.ADD_LICENSE, this::addLicenseTest);
            testMap.put(OptionTools.ARCHIVE, this::archiveTest);
            testMap.put(OptionTools.STANDARD, this::standardTest);
            testMap.put(OptionTools.COPYRIGHT, this::copyrightTest);
            testMap.put(OptionTools.DRY_RUN, this::dryRunTest);
            testMap.put(OptionTools.EXCLUDE_CLI, this::excludeCliTest);
            testMap.put(OptionTools.EXCLUDE_FILE_CLI,this::excludeCliFileTest);
            testMap.put(OptionTools.FORCE, this::forceTest);
            testMap.put(OptionTools.LICENSES, this::licensesTest);
            testMap.put(OptionTools.LIST_LICENSES, this::listLicensesTest);
            testMap.put(OptionTools.LIST_FAMILIES, this::listFamiliesTest);
            testMap.put(OptionTools.NO_DEFAULTS, this::noDefaultsTest);
            testMap.put(OptionTools.OUT, this::outTest);
            testMap.put(OptionTools.SCAN_HIDDEN_DIRECTORIES, this::scanHiddenDirectoriesTest);
            testMap.put(OptionTools.STYLESHEET_CLI, this::styleSheetTest);
            testMap.put(OptionTools.XML, this::xmlTest);
        }

        private ReportConfiguration generateConfig(Option option, String... args) {
            List<Pair<String,String>> lst = new ArrayList<>();
            for (int i=0;i<args.length;i+=2) {
                lst.add(ImmutablePair.of(args[i], args[i+1]));
            }
            BuildTask task = new BuildTask(option, lst);
            task.setUp();
            task.buildRule.executeTarget(args[0]);
            return reportConfiguration;
        }

        private String antName(Option option) {
            CasedString name = new CasedString(CasedString.StringCase.Kebab, option.getLongOpt());
            return WordUtils.uncapitalize(name.toCase(CasedString.StringCase.Camel));
        }

        @Override
        public void addLicenseTest() {
            String name = antName(OptionTools.ADD_LICENSE);
            ReportConfiguration config = generateConfig(OptionTools.ADD_LICENSE, name,"true");
            assertTrue(config.isAddingLicenses());
            config = generateConfig(OptionTools.ADD_LICENSE,name, "false");
            assertFalse(config.isAddingLicenses());
        }

        @Override
        public void archiveTest() {
            String name = antName(OptionTools.ARCHIVE);
            for (ReportConfiguration.Processing proc : ReportConfiguration.Processing.values()) {
                ReportConfiguration config = generateConfig(OptionTools.ARCHIVE, name, proc.name());
                assertEquals(proc, config.getArchiveProcessing());
            }
        }

        @Override
        public void standardTest() {
            String name = antName(OptionTools.STANDARD);
            for (ReportConfiguration.Processing proc : ReportConfiguration.Processing.values()) {
                ReportConfiguration config = generateConfig(OptionTools.STANDARD, name, proc.name());
                assertEquals(proc, config.getStandardProcessing());
            }
        }

        @Override
        public void copyrightTest() {
            String name = antName(OptionTools.COPYRIGHT);
            ReportConfiguration config = generateConfig(OptionTools.COPYRIGHT, name, "MyCopyright" );
            assertNull(config.getCopyrightMessage(), "Copyright without ADD_LICENCE should not work");
            config = generateConfig(OptionTools.COPYRIGHT, name, "MyCopyright", antName(OptionTools.ADD_LICENSE), "true" );
            assertEquals("MyCopyright", config.getCopyrightMessage());
        }

        @Override
        public void dryRunTest() {
                String name = antName(OptionTools.DRY_RUN);
                ReportConfiguration config = generateConfig(OptionTools.DRY_RUN, name, "true" );
                assertTrue(config.isDryRun());
                config = generateConfig(OptionTools.DRY_RUN,name, "false" );
                assertFalse(config.isDryRun());
        }

        @Override
        public void excludeCliTest() {
            String name = antName(OptionTools.EXCLUDE_CLI);
            ReportConfiguration config = generateConfig(OptionTools.EXCLUDE_CLI, name, "*.foo", name, "[A-Z]\\.bar", name, "justbaz");
            execCliTest(config);
        }

        private void execCliTest(ReportConfiguration config) {
                IOFileFilter filter = config.getFilesToIgnore();
                assertThat(filter).isExactlyInstanceOf(NotFileFilter.class);
                assertFalse(filter.accept(baseDir, "some.foo" ), "some.foo");
                assertFalse(filter.accept(baseDir, "B.bar"), "B.bar");
                assertFalse(filter.accept(baseDir, "justbaz" ), "justbaz");
                assertTrue(filter.accept(baseDir, "notbaz"), "notbaz");
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
            String name = antName(OptionTools.EXCLUDE_FILE_CLI);
            ReportConfiguration config = generateConfig(OptionTools.EXCLUDE_FILE_CLI, name, outputFile.getPath());
            execCliTest(config);
        }

        @Override
        public void forceTest() {
            String name = antName(OptionTools.FORCE);
                ReportConfiguration config = generateConfig(OptionTools.FORCE, name, "true");
                assertFalse(config.isAddingLicensesForced());
                config = generateConfig(OptionTools.FORCE, name, "true", antName(OptionTools.ADD_LICENSE), "true");
                assertTrue(config.isAddingLicensesForced());
        }


        @Override
        public void licensesTest() {
            String name = antName(OptionTools.LICENSES);
            ReportConfiguration config = generateConfig(OptionTools.LICENSES, name, "src/test/resources/OptionTools/One.xml", name, "src/test/resources/OptionTools/Two.xml");

                SortedSet<ILicense> set = config.getLicenses(LicenseSetFactory.LicenseFilter.ALL);
                assertTrue(set.size() > 2);
                assertTrue(LicenseSetFactory.search("ONE", "ONE", set).isPresent());
                assertTrue(LicenseSetFactory.search("TWO", "TWO", set).isPresent());

            config = generateConfig(OptionTools.LICENSES, name, "src/test/resources/OptionTools/One.xml", name, "src/test/resources/OptionTools/Two.xml", antName(OptionTools.NO_DEFAULTS), "true");

                set = config.getLicenses(LicenseSetFactory.LicenseFilter.ALL);
                assertEquals(2, set.size());
                assertTrue(LicenseSetFactory.search("ONE", "ONE", set).isPresent());
                assertTrue(LicenseSetFactory.search("TWO", "TWO", set).isPresent());

        }

        @Override
        public void listLicensesTest() {
            String name = antName(OptionTools.LIST_LICENSES);
            for (LicenseSetFactory.LicenseFilter filter : LicenseSetFactory.LicenseFilter.values()) {
                    ReportConfiguration config = generateConfig(OptionTools.LIST_LICENSES, name, filter.name());
                    assertEquals(filter, config.listLicenses());
            }
        }

        @Override
        public void listFamiliesTest() {
            String name = antName(OptionTools.LIST_FAMILIES);
            for (LicenseSetFactory.LicenseFilter filter : LicenseSetFactory.LicenseFilter.values()) {
                ReportConfiguration config = generateConfig(OptionTools.LIST_FAMILIES, name, filter.name());
                assertEquals(filter, config.listFamilies());
            }
        }

        @Override
        public void noDefaultsTest() {
            String name = antName(OptionTools.NO_DEFAULTS);
                ReportConfiguration config = generateConfig(OptionTools.NO_DEFAULTS,name, "true");
                assertTrue(config.getLicenses(LicenseSetFactory.LicenseFilter.ALL).isEmpty());
                config = generateConfig(OptionTools.NO_DEFAULTS,name, "false");
                assertFalse(config.getLicenses(LicenseSetFactory.LicenseFilter.ALL).isEmpty());
        }

        @Override
        public void outTest() {
            File outFile = new File( baseDir, "outexample");
            String name = antName(OptionTools.OUT);
            ReportConfiguration config = generateConfig(OptionTools.OUT, name, outFile.getAbsolutePath() );
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
        }

        @Override
        public void scanHiddenDirectoriesTest() {
            String name = antName(OptionTools.SCAN_HIDDEN_DIRECTORIES);
            ReportConfiguration config = generateConfig(OptionTools.SCAN_HIDDEN_DIRECTORIES, name, "true");
            assertThat(config.getDirectoriesToIgnore()).isExactlyInstanceOf(FalseFileFilter.class);
        }

        @Override
        public void styleSheetTest() {
            String name = antName(OptionTools.STYLESHEET_CLI);
            URL url = ReportTest.class.getResource("MatcherContainerResource.txt");
            if (url == null) {
                fail("Could not locate 'MatcherContainerResource.txt'");
            }
            for (String sheet : new String[]{"target/optionTools/stylesheet.xlt", "plain-rat", "missing-headers", "unapproved-licenses", url.getFile()}) {
                ReportConfiguration config = generateConfig(OptionTools.STYLESHEET_CLI, name, sheet);
                assertTrue(config.isStyleReport());
            }
        }

        @Override
        public void xmlTest() {
            String name = antName(OptionTools.XML);
            ReportConfiguration config = generateConfig(OptionTools.XML, name, "true");
            assertFalse(config.isStyleReport());
        }

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            List<Arguments> lst = new ArrayList<>();

            List<Option> opt =  OptionTools.buildOptions().getOptions().stream().filter(AntGenerator.antFilter).collect(Collectors.toList());
            for (Option option : opt) {
                if (option.getLongOpt() != null) {
                    String name = antName(option);
                    OptionToolsTest.OptionTest test = testMap.get(option);
                    if (test == null) {
                        fail("Option "+name+" is not defined in testMap");
                    }
                    lst.add(Arguments.of(name, test));
                }
            }
            return lst.stream();
        }

        private class BuildTask extends AbstractRatAntTaskTest {

            final File antFile;
            final List<Pair<String,String>> lst;
            final AntOption option;

            BuildTask(Option option, List<Pair<String, String>> pairs) {
                this.option = new AntOption(option, pairs.get(0).getKey());
                lst = pairs;
                antFile = new File(baseDir, this.option.name + ".xml");
            }

            public void setUp() {
                StringBuilder childElements = new StringBuilder();
                StringBuilder attributes = new StringBuilder();
                for (Pair<String,String> pair : lst) {
                    CasedString name = new CasedString(CasedString.StringCase.Camel, pair.getKey());
                    AntOption workingOption = option;
                    if (!name.toString().equals(option.name)) {
                        String optionName = name.toCase(CasedString.StringCase.Kebab).toLowerCase();
                        Optional<Option> opt = testMap.keySet().stream().filter(o -> o.getLongOpt().equals(optionName)).findFirst();
                        if (opt.isPresent()) {
                            workingOption = new AntOption(opt.get(), optionName);
                        }
                    }

                    if (workingOption.isAttribute()) {
                        attributes.append(format(" %s='%s'", pair.getKey(),pair.getValue()));
                    } else {
                        childElements.append(format("\t\t\t\t<%1$s>%2$s</%1$s>%n",pair.getKey(),pair.getValue()));
                    }
                }
                try (FileWriter writer = new FileWriter(antFile)) {
                    writer.append(format(ANT_FILE, this.option.name, attributes, childElements, antFile.getAbsolutePath(), OptionTest.class.getName()));
                    writer.flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                super.setUp();
            }
            private String getAntFileName() {
                return getAntFile().getPath().replace('\\', '/');
            }

            protected File getAntFile() {
                return antFile;
            }

            private String logLine(String id) {
                return logLine(true, getAntFileName(), id);
            }

            private String logLine(String antFile, String id) {
                return logLine(true, antFile, id);
            }

            private String logLine(boolean approved, String antFile, String id) {
                return format("%sS \\Q%s\\E\\s+\\Q%s\\E ", approved ? " " : "!", antFile, id);
            }

            public void execute() {
                buildRule.executeTarget(this.option.name);
            }
        }
    }

    /* $1 = target name
       $2 = attributes
       $3 = file name to read
       $4 = classname.
     */
    final static String ANT_FILE = "<?xml version='1.0'?>\n" +
            "\n" +
            "<project\n" +
            "\txmlns:au=\"antlib:org.apache.ant.antunit\"\n" +
            "\txmlns:rat=\"antlib:org.apache.rat.anttasks\">\n" +
            "\n" +
            "\t<taskdef uri=\"antlib:org.apache.ant.antunit\"\n" +
            "\t\tresource=\"org/apache/rat/anttasks/antlib.xml\"\n" +
            "\t\tclasspath=\"${test.classpath}\" />\n" +
            "\n" +
            "\t<taskdef uri=\"antlib:org.apache.rat.anttasks\"\n" +
            "\t\tresource=\"org/apache/rat/anttasks/antlib.xml\"\n" +
            "\t\tclasspath=\"${test.classpath}\" />\n" +
            "\n" +
            "\t<taskdef \n" +
            "\t\tname=\"optionTest\"\n" +
            "\t\tclassname=\"%5$s\"\n" +
            "\t\tclasspath=\"${test.classpath}\" />\n" +
            "\n" +
            "\t<target name=\"%1$s\">\n" +
            "\t\t<optionTest%2$s>\n" +
            "%3$s" +
            "\t\t\t<file file=\"%4$s\" />\n" +
            "\t\t</optionTest>\n" +
            "\t</target>\n" +
            "\n" +
            "</project>";
}
