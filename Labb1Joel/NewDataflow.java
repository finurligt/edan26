import java.util.Iterator;
import java.util.ListIterator;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

class Random {
	int	w;
	int	z;
	
	public Random(int seed)
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

class Vertex {
	int			index;
	boolean			listed;
	LinkedList<Vertex>	pred;
	LinkedList<Vertex>	succ;
	BitSet			in;
	BitSet			out;
	BitSet			use;
	BitSet			def;

	Vertex(int i)
	{
		index	= i;
		pred	= new LinkedList<Vertex>();
		succ	= new LinkedList<Vertex>();
		in	= new BitSet();
		out	= new BitSet();
		use	= new BitSet();
		def	= new BitSet();
	}

	synchronized void setListed(boolean state){
		listed = state;
	}

	synchronized void reAdd(List<Vertex> worklist) {
		if(!listed){
			worklist.add(this);
			listed = true;
		}
	}

	void computeIn(List<Vertex> worklist)
	{
		succ.forEach(succVertex -> {
			synchronized(succVertex) {
				out.or(succVertex.in);
			}
		});
	
		BitSet old = in;
		
		// in = use U (out - def)

		synchronized (this){
			in = new BitSet();
			in.or(out);	
			in.andNot(def);	
			in.or(use);
		}

		if (!in.equals(old)) {
			pred.forEach(predVertex -> predVertex.reAdd(worklist));
		}
	}

	public void print()
	{
		int	i;

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
	private List<Vertex> worklist;
	private Vertex u;

	public ParaThread (List<Vertex> worklist){
		this.worklist = worklist;
	}

	public void run(){
		while(!worklist.isEmpty()) {
			u = null;
			try{
				u = worklist.remove(0);
			} catch (IndexOutOfBoundsException e) {}
			if(u != null) {
				u.setListed(false);
				u.computeIn(worklist);
			}
		}
	}
}

class Dataflow {

	public static void connect(Vertex pred, Vertex succ)
	{
		pred.succ.addLast(succ);
		succ.pred.addLast(pred);
	}

	public static void generateCFG(Vertex vertex[], int maxsucc, Random r)
	{
		int	i;
		int	j;
		int	k;
		int	s;	// number of successors of a vertex.

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
		Vertex	vertex[],
		int	nsym,
		int	nactive,
		Random	r)
	{
		int	i;
		int	j;
		int	sym;

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

	public static void liveness(Vertex vertex[], int nthread)
	{
		int			i;
		List<Vertex>	worklist;
		ArrayList<ParaThread> threads = new ArrayList<>();
		long			begin;
		long			end;

		System.out.println("computing liveness...");

		begin = System.nanoTime();
		worklist = Collections.synchronizedList(new LinkedList<Vertex>());

		for (i = 0; i < vertex.length; ++i) {
			worklist.add(vertex[i]);
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

			}
		}

		end = System.nanoTime();

		System.out.println("T = " + (end-begin)/1e9 + " s");
	}

	public static void main(String[] args)
	{
		int	i;
		int	nsym;
		int	nvertex;
		int	maxsucc;
		int	nactive;
		int	nthread;
		boolean	print;
		Vertex	vertex[];
		Random	r;

		r = new Random(1);

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

		vertex = new Vertex[nvertex];

		for (i = 0; i < vertex.length; ++i)
			vertex[i] = new Vertex(i);

		generateCFG(vertex, maxsucc, r);
		generateUseDef(vertex, nsym, nactive, r);
		liveness(vertex, nthread);

		if (print)
			for (i = 0; i < vertex.length; ++i)
				vertex[i].print();
	}
}
