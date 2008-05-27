package edu.tum.cs.bayesnets.util;

import java.util.HashMap;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNetwork;
import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.util.graph.core.Graph;
import edu.ksu.cis.util.graph.core.Vertex;

public class TopologicalSort {
	protected BeliefNetwork bn;
	
	public TopologicalSort(BeliefNetwork bn) {
		this.bn = bn;
	}
	
	public TopologicalOrdering run() {
		return run(false);
	}
	
	public TopologicalOrdering run(boolean createTierMap) {
		Graph g = bn.getGraph();
		BeliefNode[] nodes = bn.getNodes();
		HashMap<BeliefNode, Integer> tierMap = null;
		if(createTierMap)
			tierMap = new HashMap<BeliefNode,Integer>(nodes.length);
		// obtain in-degree of each node/vertex
		Vertex[] vertices = g.getVertices();
		int[] indeg = new int[vertices.length];
		for(Vertex v : vertices) {
			for(Vertex child : g.getChildren(v)) {
				assert vertices[child.loc()] != child;
				indeg[child.loc()]++;
			}
		}
		// successively extract nodes with in-degree 0, decrementing the degree of nodes reached via them
		Vector<Vector<Integer>> ret = new Vector<Vector<Integer>>();
		int numExtracted = 0;
		Integer numLevel = 0;
		while(numExtracted < vertices.length) {
			System.out.println(numExtracted + " of " + vertices.length);
			Vector<Integer> level = new Vector<Integer>();
			int[] indeg2 = new int[indeg.length];			
			for(int i = 0; i < indeg.length; i++) {
				indeg2[i] += indeg[i];
				if(indeg[i] == 0) {
					numExtracted++;
					indeg2[i] = -1;
					level.add(i);
					if(createTierMap) 
						tierMap.put(nodes[i], numLevel);
					for(Vertex child : g.getChildren(vertices[i]))
						indeg2[child.loc()]--;
				}
			}
			indeg = indeg2;
			ret.add(level);
			numLevel++;
		}		
		return new TopologicalOrdering(ret, tierMap);
	}
}
