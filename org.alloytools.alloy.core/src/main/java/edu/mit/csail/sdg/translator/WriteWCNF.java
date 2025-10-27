/*
 * Kodkod -- Copyright (c) 2005-present, Emina Torlak
 * Pardinus -- Copyright (c) 2013-present, Nuno Macedo, INESC TEC
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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package edu.mit.csail.sdg.translator;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kodkod.engine.satlab.SATAbortedException;
import kodkod.engine.satlab.SATFactory;
import kodkod.engine.satlab.SATSolver;
import kodkod.engine.satlab.WTargetSATSolver;

/**
 * An implementation of a wrapper for an external Yices SAT solver, executed in
 * a separate process. Adapted from {@link kodkod.engine.satlab.ExternalSolver}
 * because Yices does not follow the standard wcnf format. Also extends support
 * for targets.
 *
 * @author Tiago Guimarães // [HASLab] target-oriented model finding
 */
final class WriteWCNF implements WTargetSATSolver {

    private StringBuilder        buffer      = new StringBuilder();
    private final int            capacity    = 8192;
    private RandomAccessFile     cnf;
    private volatile int         vars, clauses;
    private final long            max = 999999999999999999L;
    private Map<Integer,Long> softclauses = new HashMap<Integer,Long>();  // [HASLab]
    private List<int[]>          hardclauses = new ArrayList<int[]>();  // [HASLab]
    private String               tmpFile;

    /**
     * Constructs an ExternalSolver that will execute the specified binary with the
     * given options on the {@code inTemp} file. The {@code inTemp} file will be
     * initialized to contain all clauses added to this solver via the
     * {@link #addClause(int[])} method. The solver is assumed to write its output
     * to standard out. The {@code deleteTemp} flag indicates whether the temporary
     * files should be deleted when they are no longer needed by this solver.
     */
    WriteWCNF() {
        this.vars = 0;
        this.clauses = 0;
        // remove empty strings from the options array
    }
    WriteWCNF(String filename) {
        this();
        this.tmpFile = filename;
    }

    /**
     * Silently closes the given resource if it is non-null.
     */
    private static void close(Closeable closeable) {
        try {
            if (closeable != null)
                closeable.close();
        } catch (IOException e) {
        } // ignore
    }

    /**
     * Returns the length, in characters, of the longest possible header for a cnf
     * file: p cnf Integer.MAX_VALUE Integer.MAX_VALUE
     *
     * @return the length, in characters, of the longest possible header for a cnf
     *         file: p cnf Integer.MAX_VALUE Integer.MAX_VALUE
     */
    private static final int headerLength() {
        return String.valueOf(Integer.MAX_VALUE).length() * 3 + 9;
    }

    /**
     * Flushes the contents of the string buffer to the cnf file.
     */
    private final void flush() {
        try {
            cnf.writeBytes(buffer.toString());
        } catch (IOException e) {
            close(cnf);
            throw new SATAbortedException(e);
        } finally {
            buffer.setLength(0);
        }
    }

    /**
     * {@inheritDoc} Clauses are added to a buffer instead of directly to the SAT
     * because it must be reconstructed at each iteration to update the targets.
     *
     * @see kodkod.engine.satlab.SATSolver#addClause(int[])
     */
    public boolean addClause(int[] lits) {
        hardclauses.add(lits.clone());
        clauses++;
        return true;
    }

    public void addVariables(int numVars) {
        if (numVars < 0)
            throw new IllegalArgumentException("vars < 0: " + numVars);
        vars += numVars;
    }

    public boolean addTarget(int lit) {
        return addWeight(lit, 1L);
    }

    public boolean addWeight(int lit, long weight) {
        softclauses.put(lit, weight);
        clauses++;
        return true;
    }

    /**
     * @see kodkod.engine.satlab.SATSolver#free()
     */
    public synchronized void free() {
        close(cnf);
    }

    /**
     * Releases the resources used by this external solver.
     */
    @Override
    protected final void finalize() throws Throwable {
        // super.finalize();
        free();
    }

    public int numberOfClauses() {
        return clauses;
    }

    public int numberOfVariables() {
        return vars;
    }

    public int numberOfTargets() {
        return softclauses.size();
    }

    public boolean solve() throws SATAbortedException {

        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(tmpFile, "rw");
            file.setLength(0);
        } catch (FileNotFoundException e) {
            throw new SATAbortedException(e);
        } catch (IOException e) {
            close(file);
            throw new SATAbortedException(e);
        }
        this.cnf = file;

        // get enough space into the buffer for the cnf header, which will be written last
        buffer = new StringBuilder();
        for (int i = headerLength(); i > 0; i--) {
            buffer.append(" ");
        }
        buffer.append("\n");

        // [HASLab] add the target variables as soft clauses
        for (Integer i : softclauses.keySet()) {
            if (buffer.length() > capacity)
                flush();
            buffer.append(softclauses.get(i));
            buffer.append(" ");
            buffer.append(i);
            buffer.append(" ");
            buffer.append("0\n");
        }
        // [HASLab] add the problem variables as hard clauses
        for (int[] is : hardclauses) {
            if (buffer.length() > capacity)
                flush();
            buffer.append(max);
            buffer.append(" ");
            for (int lit : is) {
                buffer.append(lit);
                buffer.append(" ");
            }
            buffer.append("0\n");
        }

        flush();
        Process p = null;
        try {
            cnf.seek(0);
            cnf.writeBytes("p wcnf " + vars + " " + (clauses) + " " + max);
            cnf.close();
        } catch (IOException e) {
            throw new SATAbortedException(e);
        } finally {
            close(cnf);
        }
        return false;
    }
    
    @Override
    public boolean valueOf(int variable) {
        throw new IllegalStateException("This solver just writes the CNF without solving them.");
    }

    public boolean clearTargets() {
        clauses = clauses - softclauses.size();
        softclauses = new HashMap<Integer,Long>();
        return Boolean.TRUE;
    }

    /**
     * Helper method that returns a factory for WriteCNF instances.
     */
    public static final SATFactory factory(final String filename) {
        return new SATFactory() {

            private static final long serialVersionUID = 1L;

            /** {@inheritDoc} */
            @Override
            public SATSolver createSolver() {
                return new WriteWCNF(filename);
            }

            @Override
            public String id() {
                return "writewcnf";
            }

            @Override
            public String type() {
                return "synthetic";
            }
        };
    }
}