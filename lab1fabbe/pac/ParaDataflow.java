package pac;

import java.util.ListIterator;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.BitSet;

class ParaRandom {
    int w;
    int z;

    public ParaRandom(int seed)
    {
        w = seed + 1;
        z = seed * seed + seed + 2;
    }

    int nextInt()
    {
        z = 36969 * (z & 65535) + (z >> 16);
        w = 18000 * (w & 65535) + (w >> 16);

        return (z << 16) + w;
    }
}

class ParaVertex {
    int         index;
    boolean         listed;
    LinkedList<ParaVertex>  pred;
    LinkedList<ParaVertex>  succ;
    BitSet          in;
    BitSet          out;
    BitSet          use;
    BitSet          def;

    public static Object block1 = new Object();
    public static Object block2 = new Object();

    ParaVertex(int i)
    {
        index   = i;
        pred    = new LinkedList<ParaVertex>();
        succ    = new LinkedList<ParaVertex>();
        in  = new BitSet();
        out = new BitSet();
        use = new BitSet();
        def = new BitSet();
    }

    void computeIn(LinkedList<ParaVertex> worklist)
    {
        int         i;
        BitSet          old;
        ParaVertex          v;
        ListIterator<ParaVertex>    iter;

        iter = succ.listIterator();

        synchronized (worklist){
            while (iter.hasNext()) {
                v = iter.next();
                out.or(v.in);
            }
        }

        old = in;

        // in = use U (out - def)
        synchronized (worklist){
            in = new BitSet();
            in.or(out);
            in.andNot(def);
            in.or(use);
        }

        if (!in.equals(old)) {
            iter = pred.listIterator();

            while (iter.hasNext()) {
                v = iter.next();
                if (!v.listed) {
                    worklist.addLast(v);
                    v.listed = true;
                }
            }
        }
    }

    public void print()
    {
        int i;

        System.out.print("use[" + index + "] = { ");
        for (i = 0; i < use.size(); ++i)
            if (use.get(i))
                System.out.print("" + i + " ");
        System.out.println("}");
        System.out.print("def[" + index + "] = { ");
        for (i = 0; i < def.size(); ++i)
            if (def.get(i))
                System.out.print("" + i + " ");
        System.out.println("}\n");

        System.out.print("in[" + index + "] = { ");
        for (i = 0; i < in.size(); ++i)
            if (in.get(i))
                System.out.print("" + i + " ");
        System.out.println("}");

        System.out.print("out[" + index + "] = { ");
        for (i = 0; i < out.size(); ++i)
            if (out.get(i))
                System.out.print("" + i + " ");
        System.out.println("}\n");
    }

}

class ParaThread extends Thread{
    private LinkedList<ParaVertex> worklist;
    private ParaVertex u;

    public ParaThread (LinkedList<ParaVertex> worklist){
        this.worklist = worklist;
    }

    public void run(){
        while (!worklist.isEmpty()) {
            try {
                u = worklist.remove();
                u.listed = false;
            } catch(Exception e){
                break;
            }
            u.computeIn(worklist);
        }
    }
}

class ParaDataflow {

    public static void connect(ParaVertex pred, ParaVertex succ)
    {
        pred.succ.addLast(succ);
        succ.pred.addLast(pred);
    }

    public static void generateCFG(ParaVertex vertex[], int maxsucc, ParaRandom r)
    {
        int i;
        int j;
        int k;
        int s;  // number of successors of a vertex.

        System.out.println("generating CFG...");

        connect(vertex[0], vertex[1]);
        connect(vertex[0], vertex[2]);

        for (i = 2; i < vertex.length; ++i) {
            s = (r.nextInt() % maxsucc) + 1;
            for (j = 0; j < s; ++j) {
                k = Math.abs(r.nextInt()) % vertex.length;
                connect(vertex[i], vertex[k]);
            }
        }
    }

    public static void generateUseDef(
            ParaVertex  vertex[],
            int nsym,
            int nactive,
            ParaRandom  r)
    {
        int i;
        int j;
        int sym;

        System.out.println("generating usedefs...");

        for (i = 0; i < vertex.length; ++i) {
            for (j = 0; j < nactive; ++j) {
                sym = Math.abs(r.nextInt()) % nsym;

                if (j % 4 != 0) {
                    if (!vertex[i].def.get(sym))
                        vertex[i].use.set(sym);
                } else {
                    if (!vertex[i].use.get(sym))
                        vertex[i].def.set(sym);
                }
            }
        }
    }

    public static void liveness(ParaVertex vertex[], int nthread)
    {
        int         i;
        LinkedList<ParaVertex>  worklist;
        ArrayList<ParaThread> threads = new ArrayList<>();
        long            begin;
        long            end;

        System.out.println("computing liveness...");

        begin = System.nanoTime();
        worklist = new LinkedList<ParaVertex>();

        for (i = 0; i < vertex.length; ++i) {
            worklist.addLast(vertex[i]);
            vertex[i].listed = true;
        }

        for (int j = 0; j < nthread; j++) {
            ParaThread currentThread = new ParaThread(worklist);
            threads.add(currentThread);
            currentThread.start();
        }

        for (ParaThread t : threads) {
            try{
                t.join();
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        end = System.nanoTime();

        System.out.println("T = " + (end-begin)/1e9 + " s");
    }

    public static void myMain(String[] args)
    {
        int i;
        int nsym;
        int nvertex;
        int maxsucc;
        int nactive;
        int nthread;
        boolean print;
        ParaVertex  vertex[];
        ParaRandom  r;

        r = new ParaRandom(1);

        nsym = Integer.parseInt(args[0]);
        nvertex = Integer.parseInt(args[1]);
        maxsucc = Integer.parseInt(args[2]);
        nactive = Integer.parseInt(args[3]);
        nthread = Integer.parseInt(args[4]);
        print = Integer.parseInt(args[5]) != 0;

        System.out.println("nsym = " + nsym);
        System.out.println("nvertex = " + nvertex);
        System.out.println("maxsucc = " + maxsucc);
        System.out.println("nactive = " + nactive);

        vertex = new ParaVertex[nvertex];

        for (i = 0; i < vertex.length; ++i)
            vertex[i] = new ParaVertex(i);

        generateCFG(vertex, maxsucc, r);
        generateUseDef(vertex, nsym, nactive, r);
        liveness(vertex, nthread);

        if (print)
            for (i = 0; i < vertex.length; ++i)
                vertex[i].print();
    }
}