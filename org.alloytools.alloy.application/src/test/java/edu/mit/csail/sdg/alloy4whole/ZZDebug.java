package edu.mit.csail.sdg.alloy4whole;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.ConstList;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorAPI;
import edu.mit.csail.sdg.alloy4.SafeList;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.Sig;
import edu.mit.csail.sdg.ast.Sig.Field;
import edu.mit.csail.sdg.parser.CompModule;
import edu.mit.csail.sdg.parser.CompUtil;
import edu.mit.csail.sdg.translator.A4Options;
import edu.mit.csail.sdg.translator.A4Solution;
import edu.mit.csail.sdg.translator.A4Tuple;
import edu.mit.csail.sdg.translator.A4TupleSet;
import edu.mit.csail.sdg.translator.KKTransformer;
import edu.mit.csail.sdg.translator.TranslateAlloyToKodkod;
import edu.mit.csail.sdg.alloy4viz.VizGUI;

public class ZZDebug{

    public static void Run(A4Reporter rep, CompModule world) throws Err {

        // parse model from string
        // CompModule world = CompUtil.parseEverything_fromString(rep, model);
        ConstList<Command> commands = world.getAllCommands();
        if (commands.size() != 1)
            throw new ErrorAPI("Must specify exactly one command; number of commands found: " + commands.size());
        Command cmd = commands.get(0);
        A4Options opt = new A4Options();
        // opt.solver = new KKTransformer();
        // opt.solver = kodkod.solvers.SAT4JRef.INSTANCE;
        opt.solver = kodkod.solvers.PMaxSAT4JRef.INSTANCE;
        // opt.decompose_mode = 1;
        // opt.solver = kodkod.engine.satlab.SATFactory.get("sat4j.pmax");
        // opt.solver = kodkod.engine.satlab.SATFactory.get("WCNF output");
        // opt.solver = kodkod.engine.satlab.SATFactory.get("maxsat.external");
        opt.noOverflow = false;
        opt.symmetry = 20;
        opt.skolemDepth = 3;
        // solve
        A4Solution sol = TranslateAlloyToKodkod.execute_command(rep, world.getAllSigs(), cmd, opt);
        // sol = sol.next();
        // sol = sol.next();
        // sol = sol.next();

        sol.writeXML(null, "_Debug2.xml", world.getAllFunc(), null);
        // sol = null;

        // A4Solution sol = null;

        VizGUI vg =  new VizGUI(false, "_Debug2.xml", null, null, null, 1);
        vg.doShowViz();
        vg.loadThemeFile("/home/vscode/theme.thm");

        // var x = 3;

        // print solution
        System.out.println(sol);

        for (Sig sig : world.getAllReachableSigs()) {
            System.out.println("traversing sig: " + sig);
            SafeList<Field> fields = sig.getFields();
            for (Field field : fields) {
                System.out.println("  traversing field: " + field);
                A4TupleSet ts = (sol.eval(field));
                for (A4Tuple t : ts) {
                    System.out.print("    [");
                    for (int i = 0; i < t.arity(); i++)
                        System.out.print(t.atom(i) + " ");
                    System.out.println("]");
                }
            }
        }
    }

    public static void main(String[] args) throws Err {
        A4Reporter rep = new A4Reporter();
        // assert(3 < 2);
        // String model = "sig Y {} 7 \n sig X extends Int/2 {}\n run {}";
        String model = "some  sig X { var n : set Int/5}\n run {all x : X {maximal x.n; minimal x.n}} for 5 int, 3..3 steps ";
        if (args.length > 0)  
            model = args[0];
        CompModule world = CompUtil.parseEverything_fromString(rep, model);
        // String filename = "models/cmaxsat_30_40_3_6.als";
        // CompModule world = CompUtil.parseEverything_fromFile(A4Reporter.NOP, null, filename);

        Run(rep, world);
    }
    
 
}
