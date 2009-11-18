/*
 * Created on Sep 29, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.bayesnets.inference;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.inference.IJGP.JoinGraph.Arc;
import edu.tum.cs.util.StringTool;
import edu.tum.cs.util.datastruct.MutableDouble;

public class IJGP extends Sampler {

	protected JoinGraph jg;
	Vector<JoinGraph.Node> jgNodes;
	protected BeliefNode[] nodes;
	protected final boolean debug = false;

	public IJGP(BeliefNetworkEx bn) throws FileNotFoundException {
		super(bn);
		this.nodes = bn.bn.getNodes();
		// detect minimum bound
		int bound = Integer.MIN_VALUE;
		for (BeliefNode n : nodes) {
			int l = n.getCPF().getDomainProduct().length;
			if (l > bound)
				bound = l;
		}
		// construct join-graph
		jg = new JoinGraph(bn, bound);
		jg.writeDOT(new File("jg.dot"));		
	}

	/*
	 * public IJGP(BeliefNetworkEx bn, int bound) { super(bn); this.nodes =
	 * bn.bn.getNodes(); jg = new JoinGraph(bn, bound); jg.print(System.out);
	 * jgNodes = jg.getTopologicalorder(); // construct join-graph }
	 */

	@Override
	public SampledDistribution infer()
			throws Exception {
		// Create topological order
		jgNodes = jg.getTopologicalorder();
		System.out.println("Topological Order: ");
		for (int i = 0; i < jgNodes.size(); i++){
			System.out.println(jgNodes.get(i).getShortName());
		}		
		// process observed variables
		for (JoinGraph.Node n : jgNodes) {
			Vector<BeliefNode> nodes = new Vector<BeliefNode>(n.getNodes());
			for (BeliefNode belNode : nodes) {
				int nodeIdx = bn.getNodeIndex(belNode);
				int domainIdx = evidenceDomainIndices[nodeIdx];
				if (domainIdx > -1)
					n.nodes.remove(belNode);
			}
		}
		System.out.println("\n");		
		for (int step = 1; step <= this.numSamples; step++) {
			// for every node in JG in topological order and back:
			int s = jgNodes.size();
			boolean direction = true;
			for (int j = 0; j < 2 * s; j++) {
				int i;
				if (j < s)
					i = j;
				else{
					i = 2 * s - j - 1;
					direction = false;
				}
				JoinGraph.Node u = jgNodes.get(i);
				int topIndex = jgNodes.indexOf(u);
				for (JoinGraph.Node v : u.getNeighbors()) {
					if ((direction && jgNodes.indexOf(v) < topIndex) || (!direction && jgNodes.indexOf(v) > topIndex)){
						continue;
					}
					Arc arc = u.getArcToNode(v);
					// construct cluster_v(u)
					Cluster cluster_u = new Cluster(u,v);
					// Include in cluster_H each function in cluster_u which
					// scope does not contain variables in elim(u,v)
					HashSet<BeliefNode> elim = new HashSet<BeliefNode>(u.nodes);
					//System.out.println(" Node " + u.getShortName() + " and node " +v.getShortName() + " have separator " + StringTool.join(", ", arc.separator));
					elim.removeAll(arc.separator);
					Cluster cluster_H = cluster_u.getReducedCluster(elim);
					// denote by cluster_A the remaining functions
					Cluster cluster_A = cluster_u.copy();
					cluster_A.subtractCluster(cluster_H);
					// DEBUG OUTPUT
					if (debug) {
						System.out.println("  cluster_v(u): \n" + cluster_u);
						System.out.println("  A: \n" + cluster_A);
						System.out.println("  H_(u,v): \n" + cluster_H);
					}
					// convert eliminator into varsToSumOver
					int[] varsToSumOver = new int[elim.size()];
					int k = 0;
					for (BeliefNode n : elim)
						varsToSumOver[k++] = bn.getNodeIndex(n);
					// create message function and send to v
					MessageFunction m = new MessageFunction(
							arc.separator, varsToSumOver,
							cluster_A);
					arc.addOutMessage(u, m);
					for (MessageFunction mf : cluster_H.functions) {
						arc.addOutMessage(u, mf);
					}
					for (BeliefNode n : cluster_H.cpts){
						arc.addCPTOutMessage(u,n); 
					}
				}
			}
		}

		// compute probabilities and store results in distribution
		System.out.println("computing results...");
		this.createDistribution();
		dist.Z = 1.0;
		for (int i = 0; i < nodes.length; i++) {
			System.out.println(nodes[i].getName());
			if (evidenceDomainIndices[i] >= 0) {
				dist.values[i][evidenceDomainIndices[i]] = 1.0;
				continue;
			}
			// For every node X let u be a vertex in the join graph such that X
			// is in u
			// System.out.println(nodes[i]);
			JoinGraph.Node u = null;
			for (JoinGraph.Node node : jgNodes) {
				if (node.nodes.contains(nodes[i])) {
					u = node;
					break;
				}
			}
			if (u == null)
				throw new Exception(
						"Could not find vertex in join graph containing variable "
								+ nodes[i].getName());
			// System.out.println("\nCalculating results for " + nodes[i]);
			// System.out.println(u);
			// compute sum for each domain value of i-th node
			int domSize = dist.values[i].length;
			double Z = 0.0;
			int[] nodeDomainIndices = evidenceDomainIndices.clone();
			for (int j = 0; j < domSize; j++) {
				nodeDomainIndices[i] = j;
				MutableDouble sum = new MutableDouble(0.0);
				BeliefNode[] nodesToSumOver = u.nodes
						.toArray(new BeliefNode[u.nodes.size()]);
				computeSum(0, nodesToSumOver, nodes[i], new Cluster(u),
						nodeDomainIndices, sum);
				Z += (dist.values[i][j] = sum.value);
			}
			// normalize
			for (int j = 0; j < domSize; j++)
				dist.values[i][j] /= Z;
		}
		// dist.print(System.out);
		return dist;
	}

	protected void computeSum(int i, BeliefNode[] varsToSumOver,
			BeliefNode excludedNode, Cluster u, int[] nodeDomainIndices,
			MutableDouble result) {
		System.out.println(i);
		if (i == varsToSumOver.length) {
			result.value += u.product(nodeDomainIndices);
			return;
		}
		if (varsToSumOver[i] == excludedNode)
			computeSum(i + 1, varsToSumOver, excludedNode, u,
					nodeDomainIndices, result);
		else {
			for (int j = 0; j < varsToSumOver[i].getDomain().getOrder(); j++) {
				nodeDomainIndices[this.getNodeIndex(varsToSumOver[i])] = j;
				computeSum(i + 1, varsToSumOver, excludedNode, u,
						nodeDomainIndices, result);
			}
		}
	}

	protected class Cluster {
		HashSet<BeliefNode> cpts = new HashSet<BeliefNode>();
		HashSet<MessageFunction> functions = new HashSet<MessageFunction>();
		JoinGraph.Node node;

		public Cluster(JoinGraph.Node u) {
			// Constructor for cluster(u)
			this.node = u;
			// add to the cluster all CPTs of the given node
			for (CPF cpf : u.functions)
				cpts.add(cpf.getDomainProduct()[0]);
			// add all incoming messages of n
			for (JoinGraph.Node nb : u.getNeighbors()) {
				JoinGraph.Arc arc = u.arcs.get(nb);
				HashSet<MessageFunction> m = arc.getInMessage(u);
				if (!m.isEmpty())
					functions.addAll(m);
				HashSet<BeliefNode> bn = arc.getCPTInMessage(u);
				if (!bn.isEmpty())
					cpts.addAll(bn);
			}
		}
		
		public Cluster(JoinGraph.Node u, JoinGraph.Node v){
			// Constructor for cluster_v(u)
			this.node = u;
			// add to the cluster all CPTs of the given node
			for (CPF cpf : u.functions)
				cpts.add(cpf.getDomainProduct()[0]);
			// add all incoming messages of n
			for (JoinGraph.Node nb : u.getNeighbors()) {
				if (!nb.equals(v)){
					JoinGraph.Arc arc = u.arcs.get(nb);
					HashSet<MessageFunction> m = arc.getInMessage(u);
					if (!m.isEmpty())
						functions.addAll(m);
					HashSet<BeliefNode> bn = arc.getCPTInMessage(u);
					if (!bn.isEmpty())
						cpts.addAll(bn);
				}
			}
		}

		public Cluster() {
		}

		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append(StringTool.join(", ", this.cpts));
			sb.append("; ");
			sb.append(StringTool.join(", ", this.functions));
			return sb.toString();
		}

		public void excludeMessagesFrom(JoinGraph.Node n) {
			JoinGraph.Arc arc = node.arcs.get(n);
			for (MessageFunction mf : arc.getInMessage(node)) {
				if (functions.contains(mf))
					functions.remove(mf);
			}
			for (BeliefNode bn : arc.getCPTInMessage(node)) {
				if (cpts.contains(bn))
					functions.remove(bn);
			}
		}

		public Cluster copy() {
			Cluster copyCluster = new Cluster();
			for (BeliefNode cpt : cpts) {
				copyCluster.cpts.add(cpt);
			}
			for (MessageFunction f : functions) {
				copyCluster.functions.add(f);
			}
			return copyCluster;
		}

		public Cluster getReducedCluster(HashSet<BeliefNode> nodes)
				throws CloneNotSupportedException {
			// deletes all functions and arcs in the cluster whose scope
			// contains the given nodes
			Cluster redCluster = this.copy();
			for (BeliefNode bn : nodes) {
				HashSet<BeliefNode> foo = (HashSet<BeliefNode>) cpts.clone();
				for (BeliefNode n : foo) {
					BeliefNode[] domProd = n.getCPF().getDomainProduct();
					/*
					 * if (bn.equals(n)){ redCluster.cpts.remove(n); }
					 */
					for (int i = 0; i < domProd.length; i++) {
						if (bn.equals(domProd[i])) {
							redCluster.cpts.remove(n);
							break;
						}
					}
				}
				for (MessageFunction m : ((HashSet<MessageFunction>) functions
						.clone())) {
					if (m.scope.contains(bn))
						redCluster.functions.remove(m);
				}
			}
			return redCluster;
		}

		public void subtractCluster(Cluster c2) {
			// deletes all functions and arcs of the cluster that are also in
			// cluster c2
			for (BeliefNode n : ((HashSet<BeliefNode>) c2.cpts.clone())) { // TODO
																			// nonsense
				cpts.remove(n);
			}
			for (MessageFunction m : ((HashSet<MessageFunction>) c2.functions
					.clone())) {
				functions.remove(m);
			}
		}

		public double product(int[] nodeDomainIndices) {
			double ret = 1.0;
			for (BeliefNode n : cpts) {
				// System.out.println("  " + n.getCPF().toString());
				ret *= getCPTProbability(n, nodeDomainIndices);
			}
			for (MessageFunction f : this.functions) {
				// System.out.println("  " + f);
				ret *= f.compute(nodeDomainIndices);
			}
			return ret;
		}
	}

	protected class MessageFunction {

		protected int[] varsToSumOver;
		HashSet<BeliefNode> cpts;
		Iterable<MessageFunction> childFunctions;
		HashSet<BeliefNode> scope;

		public MessageFunction(HashSet<BeliefNode> scope, int[] varsToSumOver,
				Cluster cluster) {
			this.scope = scope;
			this.varsToSumOver = varsToSumOver;
			this.cpts = cluster.cpts;
			this.childFunctions = cluster.functions;
		}

		public double compute(int[] nodeDomainIndices) {
			MutableDouble result = new MutableDouble(0.0);
			compute(varsToSumOver, 0, nodeDomainIndices.clone(), result);
			return result.value;
		}

		protected void compute(int[] varsToSumOver, int i,
				int[] nodeDomainIndices, MutableDouble sum) {
			if (i == varsToSumOver.length) {
				double result = 1.0;
				for (BeliefNode node : cpts)
					result *= getCPTProbability(node, nodeDomainIndices);
				for (MessageFunction h : childFunctions)
					result *= h.compute(nodeDomainIndices);
				sum.value += result;
				return;
			}
			int idxVar = varsToSumOver[i];
			for (int v = 0; v < nodes[idxVar].getDomain().getOrder(); v++) {
				nodeDomainIndices[idxVar] = v;
				compute(varsToSumOver, i + 1, nodeDomainIndices, sum);
			}
		}
		
		public String toString() {
			StringBuffer sb = new StringBuffer("MF[");
			sb.append("scope: " + StringTool.join(", ", scope));
			sb.append("; CPFs:");
			int i = 0;
			for (BeliefNode n : this.cpts) {
				if (i++ > 0)
					sb.append("; ");
				sb.append(n.getCPF().toString());
			}
			sb.append("; children: ");
			sb.append(StringTool.join("; ", this.childFunctions));
			sb.append("]");
			return sb.toString();
		}
	}

	protected static class BucketVar {

		public HashSet<BeliefNode> nodes;
		public CPF cpf = null;
		public Vector<MiniBucket> parents;
		public BeliefNode idxVar;

		public BucketVar(HashSet<BeliefNode> nodes) {
			this(nodes, null);
		}

		public BucketVar(HashSet<BeliefNode> nodes, MiniBucket parent) {
			this.nodes = nodes;
			if (nodes.size() == 0)
				throw new RuntimeException(
						"Must provide non-empty set of nodes.");
			this.parents = new Vector<MiniBucket>();
			if (parent != null)
				parents.add(parent);
		}

		public void setFunction(CPF cpf) {
			this.cpf = cpf;
		}

		public void addInArrow(MiniBucket parent) {
			parents.add(parent);
		}

		public BeliefNode getMaxNode(BeliefNetworkEx bn) {
			// returns the BeliefNode of a bucket variable highest in the
			// topological order
			BeliefNode maxNode = null;
			int[] topOrder = bn.getTopologicalOrder();
			for (int i = topOrder.length - 1; i > -1; i--){
				for (BeliefNode node : nodes){
					if (bn.getNodeIndex(node) == topOrder[i]){
						return node;
					}
				}
			}
			return maxNode;
		}

		public String toString() {
			return "[" + StringTool.join(" ", this.nodes) + "]";
		}

		public boolean equals(BucketVar other) {
			if (other.nodes.size() != this.nodes.size())
				return false;
			for (BeliefNode n : nodes)
				if (!other.nodes.contains(n))
					return false;
			return true;
		}
	}

	protected static class MiniBucket {

		public HashSet<BucketVar> items;
		public Bucket bucket;
		public HashSet<MiniBucket> parents;
		public BucketVar child;

		public MiniBucket(Bucket bucket) {
			this.items = new HashSet<BucketVar>();
			this.bucket = bucket;
			this.child = null;
			this.parents = new HashSet<MiniBucket>();
		}

		public void addVar(BucketVar bv) {
			items.add(bv);
			for (MiniBucket p : bv.parents)
				parents.add(p);
		}

		public String toString() {
			return "Minibucket[" + StringTool.join(" ", items) + "]";
		}
	}

	protected static class Bucket {

		public BeliefNode bucketNode;
		public HashSet<BucketVar> vars = new HashSet<BucketVar>();
		public Vector<MiniBucket> minibuckets = new Vector<MiniBucket>();

		public Bucket(BeliefNode bucketNode) {
			this.bucketNode = bucketNode;
		}

		public void addVar(BucketVar bv) {
			for (BucketVar v : vars)
				if (v.equals(bv)) {
					for (MiniBucket p : bv.parents)
						v.addInArrow(p);
					return;
				}
			vars.add(bv);
		}

		/**
		 * create minibuckets of size bound
		 * 
		 * @param bound
		 */
		public void partition(int bound) {
			minibuckets.add(new MiniBucket(this));
			HashSet<BeliefNode> count = new HashSet<BeliefNode>();
			for (BucketVar bv : vars) {
				int newNodes = 0;
				for (BeliefNode n : bv.nodes) {
					if (!count.contains(n)) {
						newNodes++;
					}
				}
				if (count.size() + newNodes > bound) { // create a new
														// minibucket
					minibuckets.add(new MiniBucket(this));
					count.clear();
					count.addAll(bv.nodes);
				} else {
					count.addAll(bv.nodes);
				}
				minibuckets.lastElement().addVar(bv);
			}
		}

		public HashSet<BucketVar> createScopeFunctions() {
			HashSet<BucketVar> newVars = new HashSet<BucketVar>();
			for (MiniBucket mb : minibuckets) {
				HashSet<BeliefNode> nodes = new HashSet<BeliefNode>();
				for (BucketVar bv : mb.items) {
					for (BeliefNode bn : bv.nodes) {
						if (bn != bucketNode)
							nodes.add(bn);
					}
				}
				if (nodes.size() != 0) { // TODO check correctness
					BucketVar newBucketVar = new BucketVar(nodes, mb);
					newVars.add(newBucketVar);
				}
			}
			return newVars;
		}

		public String toString() {
			return StringTool.join(" ", vars);
		}
	}

	protected static class SchematicMiniBucket {

		public HashMap<BeliefNode, Bucket> bucketMap;
		public BeliefNetworkEx bn;

		public SchematicMiniBucket(BeliefNetworkEx bn, int bound) {
			this.bn = bn;
			bucketMap = new HashMap<BeliefNode, Bucket>();
			// order the variables from X_1 to X_n
			BeliefNode[] nodes = bn.bn.getNodes();
			int[] topOrder = bn.getTopologicalOrder();
			// place each CPT in the bucket of the highest index
			for (int i = topOrder.length - 1; i > -1; i--) {
				Bucket bucket = new Bucket(nodes[topOrder[i]]);
				int[] cpt = bn.getDomainProductNodeIndices(nodes[topOrder[i]]);
				HashSet<BeliefNode> cptNodes = new HashSet<BeliefNode>();
				for (int j : cpt) {
					cptNodes.add(nodes[j]);
				}
				BucketVar bv = new BucketVar(cptNodes);
				bv.setFunction(nodes[topOrder[i]].getCPF());
				bucket.addVar(bv);
				bucketMap.put(nodes[topOrder[i]], bucket);
			}
			// partition buckets and create arcs
			for (int i = topOrder.length - 1; i > -1; i--) {
				Bucket oldVar = bucketMap.get(nodes[topOrder[i]]);
				oldVar.partition(bound);
				HashSet<BucketVar> scopes = oldVar.createScopeFunctions();
				for (BucketVar bv : scopes) {
					// add new variables to the bucket with the highest index
					BeliefNode node = bv.getMaxNode(bn);
					bucketMap.get(node).addVar(bv);
				}
			}
		}

		public void print(PrintStream out) {
			BeliefNode[] nodes = bn.bn.getNodes();
			int[] order = bn.getTopologicalOrder();
			for (int i = nodes.length - 1; i >= 0; i--) {
				BeliefNode n = nodes[order[i]];
				out.printf("%s: %s\n", n.toString(), bucketMap.get(n));
			}
		}

		public Vector<MiniBucket> getMiniBuckets() {
			Vector<MiniBucket> mb = new Vector<MiniBucket>();
			for (Bucket b : bucketMap.values()) {
				mb.addAll(b.minibuckets);
			}
			return mb;
		}

		public Vector<Bucket> getBuckets() {
			return new Vector<Bucket>(bucketMap.values());
		}
	}

	protected static class JoinGraph {

		HashSet<Node> nodes;
		HashMap<MiniBucket, Node> bucket2node = new HashMap<MiniBucket, Node>();

		public JoinGraph(BeliefNetworkEx bn, int bound) {
			nodes = new HashSet<Node>();
			// apply procedure schematic mini-bucket(bound)
			SchematicMiniBucket smb = new SchematicMiniBucket(bn, bound);
			//System.out.println("\nJoin graph decomposition:");
			//smb.print(System.out);
			Vector<MiniBucket> minibuckets = smb.getMiniBuckets();
			// associate each minibucket with a node
			// System.out.println("\nJoin graph nodes:");
			for (MiniBucket mb : minibuckets) {
				// System.out.println(mb);
				Node newNode = new Node(mb);
				// System.out.println(newNode);
				nodes.add(newNode);
				bucket2node.put(mb, newNode);
			}
			// copy parent structure
			for (MiniBucket mb : minibuckets){
				for (MiniBucket p : mb.parents){
					bucket2node.get(mb).parents.add(bucket2node.get(p));
				}
			}
			// keep the arcs and label them by regular separator
			for (MiniBucket mb : minibuckets) {
				for (MiniBucket par : mb.parents) {
					Node n1 = bucket2node.get(par);
					Node n2 = bucket2node.get(mb);
					new Arc(n1, n2);
				}
			}
			// connect the mini-bucket clusters
			for (MiniBucket mb1 : minibuckets) {
				for (MiniBucket mb2 : minibuckets) {
					if (mb1 != mb2 && mb1.bucket == mb2.bucket) {
						new Arc(bucket2node.get(mb1), bucket2node.get(mb2));
					}
				}
			}
		}

		public void print(PrintStream out) {
			int i = 0;
			for (Node n : nodes) {
				out.printf("Node%d: %s\n", i++, StringTool.join(", ", n.nodes));
				for (CPF cpf : n.functions) {
					out.printf("  CPFS: %s | %s\n", cpf.getDomainProduct()[0],
							StringTool.join(", ", cpf.getDomainProduct()));
				}
			}
		}

		public void writeDOT(File f) throws FileNotFoundException {
			PrintStream ps = new PrintStream(f);
			ps.println("graph {");
			for (Node n : nodes) {
				for (Node n2 : n.getNeighbors()) {
					ps.printf("\"%s\" -- \"%s\";\n", n.getShortName(), n2
							.getShortName());
				}
			}
			ps.println("}");
		}

		public Vector<Node> getTopologicalorder() {
			Vector<Node> topOrder = new Vector<Node>();
			HashSet<Node> nodesLeft = new HashSet<Node>();
			nodesLeft.addAll(nodes);
			for (Node n : nodes){
				if (n.parents.isEmpty()){
					topOrder.add(n);
					nodesLeft.remove(n);
				}
			}
			//System.out.println("Start topological order with " +StringTool.join(", ", topOrder));
			int i = 0;
			while(!nodesLeft.isEmpty() && i < 10){
				HashSet<Node> removeNodes = new HashSet<Node>();
				//System.out.println(" Current order: " +StringTool.join(", ", topOrder));
				for (Node n : nodesLeft){
					//System.out.println("   - Check for " + n.getShortName() + " with parents " + StringTool.join(", ", n.mb.parents));
					if (topOrder.containsAll(n.parents)){
						//System.out.println("    -- Can be inserted!");
						topOrder.add(n);
						removeNodes.add(n);
					}
				}		
				nodesLeft.removeAll(removeNodes);
				//i++;
			}
			return topOrder;
		}

		public static class Arc {
			HashSet<BeliefNode> separator = new HashSet<BeliefNode>();
			// messages between Nodes
			Vector<Node> nodes = new Vector<Node>();
			HashMap<Node, HashSet<MessageFunction>> outMessage = new HashMap<Node, HashSet<MessageFunction>>();
			HashMap<Node, HashSet<BeliefNode>> outCPTMessage = new HashMap<Node, HashSet<BeliefNode>>();

			public Arc(Node n0, Node n1) {
				if (n0 != n1) {
					// create separator
					/*
					 * if (n0.mb.bucket == n1.mb.bucket)
					 * separator.add(n0.mb.bucket.bucketNode); else {
					 */
					separator = (HashSet<BeliefNode>) n0.nodes.clone();
					separator.retainAll(n1.nodes);
					// }
					// arc informations
					nodes.add(n0);
					nodes.add(n1);
					n0.addArc(n1, this);
					n1.addArc(n0, this);
					outMessage.put(n0, new HashSet<MessageFunction>());
					outMessage.put(n1, new HashSet<MessageFunction>());
					outCPTMessage.put(n0, new HashSet<BeliefNode>());
					outCPTMessage.put(n1, new HashSet<BeliefNode>());
				} else
					throw new RuntimeException("1-node loop in graph");
			}

			public Node getNeighbor(Node n) {
				// needs to throw exception when n not in nodes
				return nodes.get((nodes.indexOf(n) + 1) % 2);
			}

			public void addOutMessage(Node n, MessageFunction m) {
				outMessage.get(n).add(m);
			}

			public HashSet<MessageFunction> getOutMessages(Node n) {
				return outMessage.get(n);
			}

			public HashSet<MessageFunction> getInMessage(Node n) {
				return this.getOutMessages(this.getNeighbor(n));
			}
			public void addCPTOutMessage(Node n, BeliefNode bn) {
				outCPTMessage.get(n).add(bn);
			}

			public HashSet<BeliefNode> getCPTOutMessages(Node n) {
				return outCPTMessage.get(n);
			}

			public HashSet<BeliefNode> getCPTInMessage(Node n) {
				return this.getCPTOutMessages(this.getNeighbor(n));
			}

		}

		public static class Node {
			MiniBucket mb;
			Vector<CPF> functions = new Vector<CPF>();
			HashSet<BeliefNode> nodes = new HashSet<BeliefNode>();
			HashSet<Node> parents;
			HashMap<Node, Arc> arcs = new HashMap<Node, Arc>();
			
			public Node(MiniBucket mb) {
				this.mb = mb;
				this.parents = new HashSet<Node>();
				for (BucketVar var : mb.items) {
					nodes.addAll(var.nodes);
					if (var.cpf != null)
						functions.add(var.cpf);
				}
			}

			public void addArc(Node n, Arc arc) {
				arcs.put(n, arc);
			}

			public HashSet<Node> getNeighbors() {
				return new HashSet<Node>(arcs.keySet());
			}

			public Arc getArcToNode(Node n) {
				return arcs.get(n);
			}

			public Collection<BeliefNode> getNodes() {
				return nodes;
			}

			public String toString() {
				return "Supernode[" + StringTool.join(",", nodes) + "; "
						+ StringTool.join("; ", this.functions) + "]";
			}

			public String getShortName() {
				return StringTool.join(",", nodes);
			}
		}
	}
}
