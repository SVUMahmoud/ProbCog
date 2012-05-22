package probcog.srl.directed.inference;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.bayesnets.inference.ITimeLimitedInference;
import probcog.bayesnets.inference.SampledDistribution;
import probcog.srl.directed.bln.AbstractGroundBLN;

/**
 * Bayesian Network Sampler - reduces inference in relational models to standard Bayesian network inference in the ground (auxiliary) network
 * @author jain
 *
 */
public class BNSampler extends Sampler implements ITimeLimitedInference {
	protected int maxTrials;
	/**
	 * whether steps that exceed the max number of trials should just be skipped rather than raising an exception
	 */
	protected boolean skipFailedSteps;
	protected Class<? extends probcog.bayesnets.inference.Sampler> samplerClass;
	protected probcog.bayesnets.inference.Sampler sampler;
	/**
	 * the evidence we are working on
	 */
	protected int[] evidenceDomainIndices;
		
	public BNSampler(AbstractGroundBLN gbln, Class<? extends probcog.bayesnets.inference.Sampler> samplerClass) throws Exception {
		super(gbln);
		maxTrials = 5000;
		this.paramHandler.add("maxTrials", "setMaxTrials");
		this.paramHandler.add("skipFailedSteps", "setSkipFailedSteps");
		this.samplerClass = samplerClass;
	}
	
	public void setMaxTrials(int maxTrials) {
		this.maxTrials = maxTrials; 
	}
	
	public void setSkipFailedSteps(boolean canSkip) {
		this.skipFailedSteps = canSkip;
	}
	
	@Override
	protected void _initialize() throws Exception {
		// create full evidence
		String[][] evidence = this.gbln.getDatabase().getEntriesAsArray();
		evidenceDomainIndices = gbln.getFullEvidence(evidence);
	
		// initialize sampler				
		sampler = getSampler();
		paramHandler.addSubhandler(sampler.getParameterHandler());
		sampler.setEvidence(evidenceDomainIndices);
		sampler.setQueryVars(queryVars);
		sampler.setDebugMode(debug);
		sampler.setNumSamples(numSamples);
		sampler.setInfoInterval(infoInterval);
		sampler.setMaxTrials(maxTrials);
		sampler.setSkipFailedSteps(skipFailedSteps);
		sampler.initialize();
	}
	
	@Override
	public SampledDistribution _infer() throws Exception {
		// run inference
		if(verbose) System.out.printf("running %s...\n", sampler.getAlgorithmName());
		SampledDistribution dist = sampler.infer();
		return dist;
	}
	
	protected probcog.bayesnets.inference.Sampler getSampler() throws Exception {
		return samplerClass.getConstructor(BeliefNetworkEx.class).newInstance(gbln.getGroundNetwork());	
	}

	@Override
	public String getAlgorithmName() {
		return "BNInference:" + samplerClass.getSimpleName();
	}
	
	public SampledDistribution pollResults() throws Exception {
		if(sampler == null)
			return null;
		return sampler.pollResults();
	}
}