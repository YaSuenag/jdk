/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
import java.io.File;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.SA.SATestUtils;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.hprof.model.JavaObject;
import jdk.test.lib.hprof.model.JavaObjectRef;
import jdk.test.lib.hprof.parser.HprofReader;
import jdk.test.lib.process.OutputAnalyzer;


/**
 * @test
 * @bug 8381370
 * @requires vm.hasSA
 * @requires vm.gc != "Z"
 * @library /test/lib
 *
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @build LingeredAppWithValueObject
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestJhsdbJmapWithValueObject
 */
public class TestJhsdbJmapWithValueObject {

    private static final File TMP_HEAP_DUMP_FILE = new File(System.getProperty("java.io.tmpdir"), "TestJhsdbJmapWithValueObject.hprof");

    private static void runJmap(LingeredApp app) throws Exception {
        TMP_HEAP_DUMP_FILE.deleteOnExit();

        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
        launcher.addVMArgs(Utils.getFilteredTestJavaOpts("-showversion"));
        launcher.addToolArg("jmap");
        launcher.addToolArg("--binaryheap");
        launcher.addToolArg("--dumpfile");
        launcher.addToolArg(TMP_HEAP_DUMP_FILE.getAbsolutePath());
        launcher.addToolArg("--pid");
        launcher.addToolArg(Long.toString(app.getPid()));

        ProcessBuilder pb = SATestUtils.createProcessBuilder(launcher);
        Process jhsdb = pb.start();
        OutputAnalyzer out = new OutputAnalyzer(jhsdb);

        jhsdb.waitFor();

        System.out.println(out.getStdout());
        System.err.println(out.getStderr());

        out.stderrShouldBeEmptyIgnoreVMWarnings();
    }

    private static void verifyHeapDump() throws Exception {
        try (var snapshot = HprofReader.readFile(TMP_HEAP_DUMP_FILE.getAbsolutePath(), false, 0)) {
            snapshot.resolve(true);
            var jc = snapshot.findClass("LingeredAppWithValueObject");
            JavaObject valObj = (JavaObject)jc.getStaticField("valObj");

            // Check regular fields
            Asserts.assertEquals("0x1", valObj.getField("a").toString());
            Asserts.assertEquals("0x2", valObj.getField("b").toString());
            Asserts.assertEquals("0x3", valObj.getField("c").toString());

            // Check flattened fields
            JavaObject rec = (JavaObject)valObj.getField("rec");
            Asserts.assertEquals("0xa", rec.getField("recA").toString());
            Asserts.assertEquals("0x14", rec.getField("recB").toString());
        }
    }

    public static void main(String... args) throws Exception {
        SATestUtils.skipIfCannotAttach(); // throws SkippedException if attach not expected to work.
        LingeredApp app = null;

        try {
            app = new LingeredAppWithValueObject();
            LingeredApp.startApp(
                app,
                "--enable-preview",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                "-Xbootclasspath/a:."
            );
            System.out.println("Started LingeredApp with pid " + app.getPid());
            runJmap(app);
            verifyHeapDump();
        } finally {
            LingeredApp.stopApp(app);
        }
    }
}
