/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2021 Broad Institute, Aiden Lab, Rice University, Baylor College of Medicine
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
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package juicebox.tools.clt.old;

import juicebox.HiC;
import juicebox.HiCGlobals;
import juicebox.data.ChromosomeHandler;
import juicebox.data.ExpectedValueFunction;
import juicebox.data.HiCFileTools;
import juicebox.data.MatrixZoomData;
import juicebox.data.basics.Chromosome;
import juicebox.tools.clt.CommandLineParser;
import juicebox.tools.clt.JuiceboxCLT;
import juicebox.windowui.HiCZoom;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for calculating Eigenvector (separated out from Dump)
 * @author Neva Durand
 */
public class Eigenvector extends JuiceboxCLT {

    private HiC.Unit unit = null;
    private int binSize = 0;
    private Chromosome chromosome1;
    private PrintWriter pw;
    private List<Integer> whichEV;

    public Eigenvector() {
        super(getUsage() + "\n\t-p, --pearsons_all_resolutions: calculate eigenvector at all resolutions" + "\n\t--ev: which eigenvector to calculate. Default is --ev 1, the principal");
    }

    public static String getUsage(){
        return "eigenvector --ev <1/2> -p <NONE/VC/VC_SQRT/KR> <hicFile(s)> <chr> <BP/FRAG> <binsize> [outfile]";
    }

    @Override
    public void readArguments(String[] args, CommandLineParser parser) {
        if (args.length != 7 && args.length != 6) {
            printUsageAndExit();
        }

        whichEV = parser.getWhichEVOption();
        if (whichEV == null) {
            whichEV = new ArrayList<Integer>();
            whichEV.add(1);
        }

        setDatasetAndNorm(args[2], args[1], false);

        ChromosomeHandler chromosomeHandler = dataset.getChromosomeHandler();

        if (chromosomeHandler.doesNotContainChromosome(args[3])) {
            System.err.println("Unknown chromosome: " + args[3]);
            System.exit(18);
        }
        chromosome1 = chromosomeHandler.getChromosomeFromName(args[3]);

        try {
            unit = HiC.valueOfUnit(args[4]);
        } catch (IllegalArgumentException error) {
            System.err.println("Unit must be in BP or FRAG.");
            System.exit(20);
        }

        String binSizeSt = args[5];

        try {
            binSize = Integer.parseInt(binSizeSt);
        } catch (NumberFormatException e) {
            System.err.println("Integer expected for bin size.  Found: " + binSizeSt + ".");
            System.exit(21);
        }

        if ((unit == HiC.Unit.BP && binSize < HiCGlobals.MAX_EIGENVECTOR_ZOOM) ||
                (unit == HiC.Unit.FRAG && binSize < HiCGlobals.MAX_EIGENVECTOR_ZOOM / 1000)) {
            System.out.println("WARNING: Eigenvector calculation at high resolution can take a long time");
        }


        if (args.length == 7) {
            try {
                pw = new PrintWriter(new FileOutputStream(args[6]));
            } catch (IOException error) {
                System.err.println("Cannot write to " + args[6]);
                error.printStackTrace();
                System.exit(22);
            }
        }
        else pw = new PrintWriter(System.out);
    }

    @Override
    public void run() {
        HiCZoom zoom = new HiCZoom(unit, binSize);

        MatrixZoomData zd = HiCFileTools.getMatrixZoomData(dataset, chromosome1, chromosome1, zoom);
        if (zd == null) {
            System.err.println("No reads in " + chromosome1);
            System.err.println("Unknown resolution: " + zoom);
            System.err.println("This data set has the following bin sizes (in bp): ");
            for (int zoomIdx = 0; zoomIdx < dataset.getNumberZooms(HiC.Unit.BP); zoomIdx++) {
                System.err.print(dataset.getZoom(HiC.Unit.BP, zoomIdx).getBinSize() + " ");
            }
            System.err.println("\nand the following bin sizes (in frag): ");
            for (int zoomIdx = 0; zoomIdx < dataset.getNumberZooms(HiC.Unit.FRAG); zoomIdx++) {
                System.err.print(dataset.getZoom(HiC.Unit.FRAG, zoomIdx).getBinSize() + " ");
            }
            System.exit(13);
        }
        ExpectedValueFunction df = dataset.getExpectedValuesOrExit(zd.getZoom(), norm, chromosome1, true);

        List<double[]> eigenvectors = new ArrayList<double[]>();
        for (int ev : whichEV) {
            double[] vector = dataset.getEigenvector(chromosome1, zoom, ev - 1, norm);
            // mean center and print
            int count = 0;
            double total = 0;

            for (double element : vector) {
                if (!Double.isNaN(element)) {
                    total += element;
                    count++;
                }
            }

            double mean = total / count; // sum is now mean
            for (int idx = 0; idx < vector.length; idx++) {
                vector[idx] = vector[idx] - mean;
            }
            eigenvectors.add(vector);
        }

        // print out vector
        for (int idx = 0; idx < eigenvectors.get(0).length; idx++) {
            List<String> valueList = new ArrayList<String>();
            for (int valueIdx = 0; valueIdx < eigenvectors.size(); valueIdx++) {
                valueList.add(String.valueOf((eigenvectors.get(valueIdx))[idx]));
            }
            String row = String.join("\t", valueList);
            pw.println(row);
        }
        pw.close();
    }
}