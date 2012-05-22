/*
 * Created on Oct 15, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.bayesnets.inference;

import probcog.bayesnets.core.BeliefNetworkEx;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.DiscreteEvidence;

/**
 * general wrapper for BNJ inference algorithms
 * @author jain
 */
public class BNJInference extends Sampler {

	Class<? extends edu.ksu.cis.bnj.ver3.inference.Inference> algorithmClass;
	
	public BNJInference(BeliefNetworkEx bn, Class<? extends edu.ksu.cis.bnj.ver3.inference.Inference> algoClass) throws Exception {
		super(bn);
		this.algorithmClass = algoClass;
	}

	@Override
	public void _infer()
			throws Exception {
		// set evidence
		for(int i = 0; i < evidenceDomainIndices.length; i++)
			if(evidenceDomainIndices[i] != -1)
				nodes[i].setEvidence(new DiscreteEvidence(evidenceDomainIndices[i]));
		
		// run inference
		edu.ksu.cis.bnj.ver3.inference.Inference algo = algorithmClass.newInstance();
		algo.run(bn.bn);
		
		// retrieve results
		SampledDistribution dist = createDistribution();
		for(int i = 0; i < nodes.length; i++) {
			CPF cpf = algo.queryMarginal(nodes[i]);
			for(int j = 0; j < dist.values[i].length; j++)
				dist.values[i][j] = cpf.getDouble(j);
		}
		dist.Z = 1.0;
		dist.steps = 1;
		dist.trials = 1;
		((ImmediateDistributionBuilder)distributionBuilder).setDistribution(dist);

		// remove evidence
		bn.removeAllEvidences();
	}
	
	protected IDistributionBuilder createDistributionBuilder() {
		return new ImmediateDistributionBuilder();
	}	
}