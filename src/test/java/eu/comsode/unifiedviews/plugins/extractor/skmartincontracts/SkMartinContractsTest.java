package eu.comsode.unifiedviews.plugins.extractor.skmartincontracts;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import org.junit.Test;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.Rio;

import cz.cuni.mff.xrg.odcs.dpu.test.TestEnvironment;
import eu.unifiedviews.dataunit.rdf.WritableRDFDataUnit;
import eu.unifiedviews.helpers.dataunit.rdf.RDFHelper;
import eu.unifiedviews.helpers.dpu.test.config.ConfigurationBuilder;

public class SkMartinContractsTest {
    @Test
    public void testSmallFile() throws Exception {
        SkMartinContractsConfig_V1 config = new SkMartinContractsConfig_V1();

        // Prepare DPU.
        SkMartinContracts dpu = new SkMartinContracts();
        dpu.configure((new ConfigurationBuilder()).setDpuConfiguration(config).toString());

        // Prepare test environment.
        TestEnvironment environment = new TestEnvironment();
        CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
        encoder.onMalformedInput(CodingErrorAction.REPORT);
        encoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        PrintWriter outputWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream("contracts.txt", false), encoder)));

        // Prepare data unit.
        WritableRDFDataUnit rdfOutput = environment.createRdfOutput("rdfOutput", false);

        try {
            // Run.
            environment.run(dpu);

            RepositoryConnection con = rdfOutput.getConnection();
            con.export(Rio.createWriter(RDFFormat.TURTLE, outputWriter), RDFHelper.getGraphsURIArray(rdfOutput));
        } finally {
            // Release resources.
            environment.release();
        }
    }
}
